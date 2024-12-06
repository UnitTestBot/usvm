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
open class UDebugProfileObserver<Statement, Method, State: UState<*, Method, Statement, *, *, *>>(
    private val getMethodOfStatement: Statement.() -> Method,
    private val getStatementIndexInMethod: Statement.() -> Int,
    private val getMethodToCallIfCallStatement: Statement.() -> Method?,
    private val padInstructionEnd: Int = 0,
    private val momentOfUpdate: MomentOfUpdate = MomentOfUpdate.BeforeStep,
    private val printNonVisitedStatements: Boolean = false,
    private val getAllMethodStatements: Method.() -> List<Statement> = { emptyList() },
    private val printStatement: Statement.() -> String = { toString() },
    private val getNewStatements: State.() -> List<Statement> = { listOf(currentStatement) },
    private val forkHappened: (State, Statement) -> Boolean = defaultForkHappened(),
    originalInst: Statement.() -> (Statement) = { this },
) : UMachineObserver<State> {

    interface StatementOperations<Statement, Method> {
        fun getMethodOfStatement(statement: Statement): Method
        fun getStatementIndexInMethod(statement: Statement): Int
    }

    protected val instructionStats = hashMapOf<Statement, MutableMap<StateId, StatsCounter>>()
    protected val methodCalls = hashMapOf<Method, MutableMap<StateId, StatsCounter>>()

    private val stackTraceTracker = StackTraceTracker<Statement, State>(originalInst)
    private val stackTraces = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()
    private val forksCount = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()

    override fun onStatePeeked(state: State) {
        if (momentOfUpdate == MomentOfUpdate.BeforeStep) {
            processStateUpdate(state)
        }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        if (momentOfUpdate == MomentOfUpdate.AfterStep) {
            (listOf(parent) + forks).forEach {
                processStateUpdate(it)
            }
        }
    }

    private fun processStateUpdate(state: State) {
        if (state.pathNode.depth == 0) {
            return
        }

        val statements = state.getNewStatements()
        statements.forEach {
            processCurrentStatement(it, state)
        }
    }

    private fun processCurrentStatement(statement: Statement, state: State) {
        instructionStats.increment(statement, state)

        val method = statement.getMethodToCallIfCallStatement()
        if (method != null) {
            methodCalls.increment(method, state)
        }

        val fork = forkHappened(state, statement)

        var st = stackTraceTracker.getStackTrace(state, statement)
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

    private fun aggregateStackTraces(slice: StateId?): ProfileFrame<Statement, Method> {
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
            padInstructionEnd,
            getMethodOfStatement,
            getStatementIndexInMethod,
            printStatement,
            printNonVisitedStatements,
            getAllMethodStatements,
        )
    }

    private fun computeProfileFrame(
        slice: StateId?,
        inst: Statement,
        root: TrieNode<Statement, *>
    ): ProfileFrame<Statement, Method> {
        val allNodeStats = stackTraces[root] ?: emptyMap()
        val allForkStats = forksCount[root] ?: emptyMap()
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
            padInstructionEnd,
            getMethodOfStatement,
            getStatementIndexInMethod,
            printStatement,
            printNonVisitedStatements,
            getAllMethodStatements,
        )
    }

    private class ProfileFrame<Statement, Method>(
        val inst: Statement?,
        val total: Long,
        val self: Long,
        val totalForks: Long,
        val selfForks: Long,
        val children: Map<Statement, ProfileFrame<Statement, Method>>,
        val padInstructionEnd: Int,
        private val getMethodOfStatement: Statement.() -> Method,
        private val getStatementIndexInMethod: Statement.() -> Int,
        private val printStatement: Statement.() -> String,
        private val printNonVisitedStatements: Boolean,
        private val getAllMethodStatements: Method.() -> List<Statement>,
    ) {
        fun print(str: StringBuilder, indent: String) {
            val sortedChildren = children.entries
                .groupBy { it.key.getMethodOfStatement() }.entries
                .sortedByDescending { it.value.sumOf { it.value.total } }

            for ((method, inst) in sortedChildren) {
                val total = inst.sumOf { it.value.total }
                val self = inst.sumOf { it.value.self }
                val totalForks = inst.sumOf { it.value.totalForks }
                val selfForks = inst.sumOf { it.value.selfForks }
                str.appendLine("$indent|__ $method | Steps ${self}/${total} | Forks ${selfForks}/${totalForks}")

                val children = if (printNonVisitedStatements) {
                    val allStatements = method.getAllMethodStatements()
                    allStatements.map { childStmt ->
                        inst.find {
                            it.key == childStmt
                        }?.let {
                            it.key to it.value
                        } ?: let {
                            val emptyFrame = ProfileFrame(
                                childStmt,
                                0L,
                                0L,
                                0L,
                                0L,
                                emptyMap(),
                                padInstructionEnd,
                                getMethodOfStatement,
                                getStatementIndexInMethod,
                                printStatement,
                                printNonVisitedStatements = false,
                                getAllMethodStatements,
                            )
                            childStmt to emptyFrame
                        }
                    }
                } else {
                    inst.sortedBy { it.value.inst?.getStatementIndexInMethod() }.map { it.key to it.value }
                }

                for ((i, child) in children) {
                    val instRepr = i.printStatement().padEnd(padInstructionEnd)
                    str.appendLine("$indent$INDENT$instRepr | States ${child.self}/${child.total} | Forks ${child.selfForks}/${child.totalForks}")
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

    protected class StackTraceTracker<Statement, State: UState<*, *, Statement, *, *, *>>(
        val originalInst: Statement.() -> (Statement),
    ) {
        private val stackTraceTrie = TrieNode.root<Statement, Unit> { }

        fun getRoot(): TrieNode<Statement, Unit> = stackTraceTrie

        fun getStackTrace(state: State, statement: Statement): TrieNode<Statement, Unit> {
            var node = stackTraceTrie
            for (frame in state.callStack) {
                val inst = frame.returnSite?.originalInst() ?: continue
                node = node.add(inst) {}
            }
            return node.add(statement.originalInst()) {}
        }
    }

    private fun Map<StateId, StatsCounter>.sumOfSlice(slice: StateId?): Long =
        if (slice == null) values.sum() else this[slice]?.sum() ?: 0L

    private fun Iterable<StatsCounter>.sum(): Long = sumOf { it.sum() }

    companion object {
        fun <Statement, State : UState<*, *, Statement, *, *, *>> defaultForkHappened() = { state: State, _: Statement ->
            state.forkPoints != PathNode.root<Nothing>() && state.forkPoints.statement == state.pathNode.parent
        }
    }

    enum class MomentOfUpdate {
        BeforeStep,
        AfterStep,
    }
}