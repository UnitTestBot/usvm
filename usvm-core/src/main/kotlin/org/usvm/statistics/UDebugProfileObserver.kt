package org.usvm.statistics

import mu.KLogging
import org.usvm.PathNode
import org.usvm.StateId
import org.usvm.UState
import org.usvm.algorithms.TrieNode
import java.util.concurrent.atomic.LongAdder

private typealias StatsCounter = LongAdder

private val logger = object : KLogging() {}.logger

/**
 * Collect UMachine profile for debug purposes.
 * */
open class UDebugProfileObserver<Statement, Method, State : UState<*, Method, Statement, *, *, *>>(
    private val statementOperations: StatementOperations<Statement, Method, State>,
    private val profilerOptions: Options = Options(),
) : UMachineObserver<State> {

    interface StatementOperations<Statement, Method, State : UState<*, Method, Statement, *, *, *>> {

        // mandatory operations

        fun getMethodOfStatement(statement: Statement): Method

        fun getStatementIndexInMethod(statement: Statement): Int

        /**
         * Return null if statement is not a call statement
         * */
        fun getMethodToCallIfCallStatement(statement: Statement): Method?

        // operations with default implementations

        fun printStatement(statement: Statement): String = statement.toString()
        fun printMethodName(method: Method): String = method.toString()

        /**
         * This is needed for JVM hack (see originalInst() of JcInst).
         * */
        fun getOriginalInst(statement: Statement): Statement = statement

        /**
         * Used only if [Options.printNonVisitedStatements] option is set
         * */
        fun getAllMethodStatements(method: Method): List<Statement> = emptyList()

        /**
         * Extract new visited statements from [state].
         *
         * In a classic implementation of symbolic execution this is just [UState.currentStatement].
         * For concolic variant this should be overridden.
         * */
        fun getNewStatements(state: State): List<PathNode<Statement>> {
            return listOf(state.pathNode)
        }

        /**
         * Detect that a fork happened in [state] on [statement].
         * */
        fun forkHappened(state: State, statement: PathNode<Statement>): Boolean {
            return state.forkPoints != PathNode.root<Nothing>() && state.forkPoints.statement == statement.parent
        }
    }

    data class Options(
        /**
         * Statistics from state about visited statements can be added either before or after interpreter step.
         * */
        val momentOfUpdate: MomentOfUpdate = MomentOfUpdate.BeforeStep,
        /**
         * Non-visited statements can be excluded from profiler output or not.
         * If this option is true, [StatementOperations.getAllMethodStatements] must be implemented.
         * */
        val printNonVisitedStatements: Boolean = false,
        /**
         * Add padding to instructions in profiler report.
         * */
        val padInstructionEnd: Int = 0,
    )

    protected val instructionStats = hashMapOf<Statement, MutableMap<StateId, StatsCounter>>()
    protected val methodCalls = hashMapOf<Method, MutableMap<StateId, StatsCounter>>()

    private val stackTraceTracker = StackTraceTracker<Statement, Method, State>(statementOperations)
    private val stackTraces = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()
    private val forksCount = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()

    override fun onStatePeeked(state: State) {
        if (profilerOptions.momentOfUpdate == MomentOfUpdate.BeforeStep) {
            processStateUpdate(state)
        }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        if (profilerOptions.momentOfUpdate == MomentOfUpdate.AfterStep) {
            (listOf(parent) + forks).forEach {
                processStateUpdate(it)
            }
        }
    }

    private fun processStateUpdate(state: State) {
        if (state.pathNode.depth == 0) {
            return
        }

        val statements = statementOperations.getNewStatements(state)
        statements.forEach {
            processCurrentStatement(it, state)
        }
    }

    private fun processCurrentStatement(node: PathNode<Statement>, state: State) {
        instructionStats.increment(node.statement, state)

        val method = statementOperations.getMethodToCallIfCallStatement(node.statement)
        if (method != null) {
            methodCalls.increment(method, state)
        }

        val fork = statementOperations.forkHappened(state, node)

        var st = stackTraceTracker.getStackTrace(state, node.statement)
        while (true) {
            stackTraces.increment(st, state)
            if (fork) forksCount.increment(st, state)
            st = st.parent() ?: break
        }
    }

    private fun <K> MutableMap<K, MutableMap<StateId, StatsCounter>>.increment(key: K, state: State) {
        val stateStats = this.getOrPut(key) { hashMapOf() }
        val stats = stateStats.getOrPut(state.id) { StatsCounter() }
        stats.increment()
    }

    private fun aggregateStackTraces(slice: StateId?): ProfileFrame<Statement, Method, State> {
        val children = stackTraceTracker.getRoot().children.mapValues { (i, node) ->
            computeProfileFrame(slice, i, node)
        }
        return ProfileFrame(
            inst = null,
            total = -1,
            self = -1,
            totalForks = -1,
            selfForks = -1,
            children = children,
            profilerOptions,
            statementOperations,
        )
    }

    private fun computeProfileFrame(
        slice: StateId?,
        inst: Statement,
        root: TrieNode<Statement, *>,
    ): ProfileFrame<Statement, Method, State> {
        val allNodeStats = stackTraces[root].orEmpty()
        val allForkStats = forksCount[root].orEmpty()
        val children = root.children.mapValues { (i, node) -> computeProfileFrame(slice, i, node) }

        val nodeStats = allNodeStats.sumOfSlice(slice)
        val forkStats = allForkStats.sumOfSlice(slice)

        val selfStat = nodeStats - children.values.sumOf { it.total }
        val selfForks = forkStats - children.values.sumOf { it.totalForks }
        return ProfileFrame(
            inst,
            nodeStats,
            selfStat,
            forkStats,
            selfForks,
            children,
            profilerOptions,
            statementOperations,
        )
    }

    private class ProfileFrame<Statement, Method, State : UState<*, Method, Statement, *, *, *>>(
        val inst: Statement?,
        val total: Long,
        val self: Long,
        val totalForks: Long,
        val selfForks: Long,
        val children: Map<Statement, ProfileFrame<Statement, Method, State>>,
        private val profilerOptions: Options,
        private val statementOperations: StatementOperations<Statement, Method, State>,
    ) {
        fun print(str: StringBuilder, indent: String) {
            val sortedChildren = children.entries
                .groupBy { statementOperations.getMethodOfStatement(it.key) }.entries
                .sortedByDescending { entry -> entry.value.sumOf { it.value.total } }

            for ((method, inst) in sortedChildren) {
                val total = inst.sumOf { it.value.total }
                val self = inst.sumOf { it.value.self }
                val totalForks = inst.sumOf { it.value.totalForks }
                val selfForks = inst.sumOf { it.value.selfForks }
                val methodRepr = statementOperations.printMethodName(method)
                str.appendLine("$indent|__ $methodRepr | Steps $self/$total | Forks $selfForks/$totalForks")

                val children = if (profilerOptions.printNonVisitedStatements) {
                    val allStatements = statementOperations.getAllMethodStatements(method)
                    allStatements.map { childStmt ->
                        inst.find {
                            it.key == childStmt
                        }?.let {
                            it.key to it.value
                        } ?: let {
                            val emptyFrame = ProfileFrame(
                                childStmt,
                                total = 0L,
                                self = 0L,
                                totalForks = 0L,
                                selfForks = 0L,
                                emptyMap(),
                                profilerOptions,
                                statementOperations,
                            )
                            childStmt to emptyFrame
                        }
                    }
                } else {
                    inst.sortedBy { entry ->
                        entry.value.inst?.let { statementOperations.getStatementIndexInMethod(it) }
                    }.map { it.key to it.value }
                }

                for ((i, child) in children) {
                    val instRepr = statementOperations.printStatement(i).padEnd(profilerOptions.padInstructionEnd)
                    val line = "$indent$INDENT$instRepr" +
                        " | Steps ${child.self}/${child.total}" +
                        " | Forks ${child.selfForks}/${child.totalForks}"
                    str.appendLine(line)
                    child.print(str, "$indent$INDENT$INDENT")
                }
            }
        }

        override fun toString(): String = buildString { print(this, "") }

        companion object {
            private val INDENT = " ".repeat(4)
        }
    }

    override fun onMachineStopped() {
        logger.debug { report() }
    }

    protected open fun report(): String = buildString {
        reportProfile()
    }

    protected fun StringBuilder.reportProfile() {
        val profileStats = aggregateStackTraces(slice = null)

        appendLine("Profile:")
        profileStats.print(this, indent = "")
    }

    protected class StackTraceTracker<Statement, Method, State : UState<*, Method, Statement, *, *, *>>(
        private val statementOperations: StatementOperations<Statement, Method, State>,
    ) {
        private val stackTraceTrie = TrieNode.root<Statement, Unit> { }

        fun getRoot(): TrieNode<Statement, Unit> = stackTraceTrie

        fun getStackTrace(state: State, statement: Statement): TrieNode<Statement, Unit> {
            var node = stackTraceTrie
            for (frame in state.callStack) {
                val inst = frame.returnSite?.let { statementOperations.getOriginalInst(it) } ?: continue
                node = node.add(inst) {}
            }
            return node.add(statementOperations.getOriginalInst(statement)) {}
        }
    }

    private fun Map<StateId, StatsCounter>.sumOfSlice(slice: StateId?): Long =
        if (slice == null) values.sum() else this[slice]?.sum() ?: 0L

    private fun Iterable<StatsCounter>.sum(): Long = sumOf { it.sum() }

    enum class MomentOfUpdate {
        BeforeStep,
        AfterStep,
    }
}
