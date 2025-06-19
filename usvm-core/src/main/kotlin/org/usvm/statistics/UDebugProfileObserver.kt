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
        /**
         * Profile time spent on each instruction.
         * */
        val profileTime: Boolean = true,
        /**
         * Profile number of forks on each instruction.
         * */
        val profileForks: Boolean = true,
        val timeFormat: TimeFormat = TimeFormat.Microseconds,
    ) {
        init {
            require(!profileTime || momentOfUpdate == MomentOfUpdate.BeforeStep) {
                "Time profiling in now supported only if momentOfUpdate in BeforeStep"
            }
        }
    }

    protected val instructionStats = hashMapOf<Statement, MutableMap<StateId, StatsCounter>>()
    protected val methodCalls = hashMapOf<Method, MutableMap<StateId, StatsCounter>>()

    private val stackTraceTracker = StackTraceTracker(statementOperations)
    private val stackTraces = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()
    private val forksCount = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()
    private val instructionTime = hashMapOf<TrieNode<Statement, *>, MutableMap<StateId, StatsCounter>>()

    private var lastPeekMoment = 0L
    private var lastStackTrace: TrieNode<Statement, *>? = null

    override fun onStatePeeked(state: State) {
        if (profilerOptions.momentOfUpdate != MomentOfUpdate.BeforeStep) {
            return
        }

        processStateUpdate(state)

        if (profilerOptions.profileTime) {
            lastPeekMoment = System.nanoTime()
        }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        if (profilerOptions.momentOfUpdate == MomentOfUpdate.AfterStep) {
            (listOf(parent) + forks).forEach {
                processStateUpdate(it)
            }
        }

        if (profilerOptions.momentOfUpdate == MomentOfUpdate.BeforeStep && profilerOptions.profileTime) {
            val stackTrace = lastStackTrace
                ?: error("stackTraceAfterPeek should have been memorized")
            incrementInstructionTime(parent, stackTrace, System.nanoTime() - lastPeekMoment)
        }
    }

    private fun incrementInstructionTime(state: State, stackTrace: TrieNode<Statement, *>, time: Long) {
        var st = stackTrace
        while (true) {
            instructionTime.increment(st, state, value = time)
            st = st.parent() ?: break
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
        lastStackTrace = st
        while (true) {
            stackTraces.increment(st, state)
            if (fork) forksCount.increment(st, state)
            st = st.parent() ?: break
        }
    }

    private fun <K> MutableMap<K, MutableMap<StateId, StatsCounter>>.increment(key: K, state: State, value: Long = 1L) {
        val stateStats = this.getOrPut(key) { hashMapOf() }
        val stats = stateStats.getOrPut(state.id) { StatsCounter() }
        stats.add(value)
    }

    private fun aggregateStackTraces(slice: StateId?): ProfileFrame<Statement, Method, State> {
        val children = stackTraceTracker.getRoot().children.mapValues { (i, node) ->
            computeProfileFrame(slice, i, node)
        }
        return ProfileFrame(
            inst = null,
            totalSteps = -1,
            selfSteps = -1,
            totalForks = -1,
            selfForks = -1,
            totalTime = -1,
            selfTime = -1,
            children = children,
            profilerOptions,
            statementOperations,
        )
    }

    private fun formatTime(time: Long): Long {
        return when (profilerOptions.timeFormat) {
            TimeFormat.Nanoseconds -> time
            TimeFormat.Microseconds -> time / MICRO_IN_NANO
            TimeFormat.Milliseconds -> time / MILLIES_IN_NANO
        }
    }

    private fun computeProfileFrame(
        slice: StateId?,
        inst: Statement,
        root: TrieNode<Statement, *>,
    ): ProfileFrame<Statement, Method, State> {
        val allNodeStats = stackTraces[root].orEmpty()
        val allForkStats = forksCount[root].orEmpty()
        val allTimeStats = instructionTime[root].orEmpty()
        val children = root.children.mapValues { (i, node) -> computeProfileFrame(slice, i, node) }

        val nodeStats = allNodeStats.sumOfSlice(slice)
        val forkStats = allForkStats.sumOfSlice(slice)
        val timeStats = allTimeStats.sumOfSlice(slice)

        val selfStat = nodeStats - children.values.sumOf { it.totalSteps }
        val selfForks = forkStats - children.values.sumOf { it.totalForks }
        val selfTime = timeStats - children.values.sumOf { it.totalTime }
        return ProfileFrame(
            inst,
            totalSteps = nodeStats,
            selfSteps = selfStat,
            totalForks = forkStats,
            selfForks = selfForks,
            totalTime = formatTime(timeStats),
            selfTime = formatTime(selfTime),
            children,
            profilerOptions,
            statementOperations,
        )
    }

    private class ProfileFrame<Statement, Method, State : UState<*, Method, Statement, *, *, *>>(
        val inst: Statement?,
        val totalSteps: Long,
        val selfSteps: Long,
        val totalForks: Long,
        val selfForks: Long,
        val totalTime: Long,
        val selfTime: Long,
        val children: Map<Statement, ProfileFrame<Statement, Method, State>>,
        private val profilerOptions: Options,
        private val statementOperations: StatementOperations<Statement, Method, State>,
    ) {
        fun print(str: StringBuilder, indent: String) {
            val sortedChildren = children.entries
                .groupBy { statementOperations.getMethodOfStatement(it.key) }.entries
                .sortedByDescending { entry -> entry.value.sumOf { it.value.totalSteps } }

            for ((method, inst) in sortedChildren) {
                val total = inst.sumOf { it.value.totalSteps }
                val self = inst.sumOf { it.value.selfSteps }
                val totalForks = inst.sumOf { it.value.totalForks }
                val selfForks = inst.sumOf { it.value.selfForks }
                val methodRepr = statementOperations.printMethodName(method)

                str.append("$indent|__ $methodRepr | Steps $self/$total")
                if (profilerOptions.profileForks) {
                    str.append(" | Forks $selfForks/$totalForks")
                }
                if (profilerOptions.profileTime) {
                    str.append(" | Time $selfTime/$totalTime")
                }
                str.appendLine()

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
                                totalSteps = 0L,
                                selfSteps = 0L,
                                totalForks = 0L,
                                selfForks = 0L,
                                totalTime = 0L,
                                selfTime = 0L,
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

                    var line = "$indent$INDENT$instRepr | Steps ${child.selfSteps}/${child.totalSteps}"
                    if (profilerOptions.profileForks) {
                        line += " | Forks ${child.selfForks}/${child.totalForks}"
                    }
                    if (profilerOptions.profileTime) {
                        line += " | Time ${child.selfTime}/${child.totalTime}"
                    }
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

    enum class TimeFormat {
        Milliseconds,
        Microseconds,
        Nanoseconds,
    }

    companion object {
        private const val MICRO_IN_NANO = 1000L
        private const val MILLIES_IN_NANO = 1_000_000L
    }
}
