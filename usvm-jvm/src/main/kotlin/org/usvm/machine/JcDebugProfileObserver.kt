package org.usvm.machine

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.PathNode
import org.usvm.StateId
import org.usvm.UPathSelector
import org.usvm.algorithms.TrieNode
import org.usvm.machine.state.JcState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.originalInst

/**
 * Collect JcMachine profile (like async-profiler) for the debug purposes.
 * */
class JcDebugProfileObserver(
    private val pathSelector: UPathSelector<JcState>
) : UMachineObserver<JcState> {
    private val instructionStats = hashMapOf<JcInst, MutableMap<StateId, Int>>()
    private val methodCalls = hashMapOf<JcMethod, MutableMap<StateId, Int>>()

    private val stackTraceTracker = JcStackTraceTracker()
    private val stackTraces = hashMapOf<TrieNode<JcInst, *>, MutableMap<StateId, Int>>()
    private val forksCount = hashMapOf<TrieNode<JcInst, *>, MutableMap<StateId, Int>>()

    override fun onStatePeeked(state: JcState) {
        val statement = state.currentStatement
        instructionStats.increment(statement, state)

        if (statement is JcConcreteMethodCallInst) {
            methodCalls.increment(statement.method, state)
        }

        val fork = state.forkPoints != PathNode.root<Nothing>() && state.forkPoints.statement == state.pathNode.parent

        var st = stackTraceTracker.getStackTrace(state)
        while (true) {
            stackTraces.increment(st, state)
            if (fork) forksCount.increment(st, state)
            st = st.parent() ?: break
        }
    }

    private fun <K> MutableMap<K, MutableMap<StateId, Int>>.increment(key: K, state: JcState) {
        val stateStats = this.getOrPut(key) { hashMapOf() }
        val stats = stateStats[state.id] ?: 0
        stateStats[state.id] = stats + 1
    }

    private fun aggregateStats(): List<Map.Entry<JcClassOrInterface, List<Map.Entry<JcMethod, Int>>>> {
        val methodStats = instructionStats.entries
            .groupBy({ it.key.location.method }, { it.value })
            .mapValues { it.value.sumOf { it.values.sum() } }

        val classStats = methodStats.entries.groupBy { it.key.enclosingClass }
        val orderedMethodStats = classStats.mapValues { it.value.sortedByDescending { it.value } }
        return orderedMethodStats.entries.sortedByDescending { it.value.sumOf { it.value } }
    }

    private fun aggregateStackTraces(slice: StateId?): ProfileFrame {
        val children = stackTraceTracker.getRoot().children.mapValues { (i, node) ->
            computeProfileFrame(slice, i, node)
        }
        return ProfileFrame(inst = null, total = -1, self = -1, -1, -1, children = children)
    }

    private fun computeProfileFrame(slice: StateId?, inst: JcInst, root: TrieNode<JcInst, *>): ProfileFrame {
        val allNodeStats = stackTraces[root] ?: emptyMap()
        val allForkStats = forksCount[root] ?: emptyMap()
        val children = root.children.mapValues { (i, node) -> computeProfileFrame(slice, i, node) }

        val nodeStats = if (slice == null) {
            allNodeStats.values.sum()
        } else {
            allNodeStats[slice] ?: 0
        }

        val forkStats = if (slice == null) {
            allForkStats.values.sum()
        } else {
            allForkStats[slice] ?: 0
        }

        val selfStat = nodeStats - children.values.sumOf { it.total }
        val selfForks = forkStats - children.values.sumOf { it.totalForks }
        return ProfileFrame(inst, nodeStats, selfStat, forkStats, selfForks, children)
    }

    private class ProfileFrame(
        val inst: JcInst?,
        val total: Int,
        val self: Int,
        val totalForks: Int,
        val selfForks: Int,
        val children: Map<JcInst, ProfileFrame>
    ) {
        fun print(str: StringBuilder, indent: String) {
            val sortedChildren = children.entries
                .groupBy { it.key.location.method }.entries
                .sortedByDescending { it.value.sumOf { it.value.total } }

            for ((method, inst) in sortedChildren) {
                val total = inst.sumOf { it.value.total }
                val self = inst.sumOf { it.value.self }
                val totalForks = inst.sumOf { it.value.totalForks }
                val selfForks = inst.sumOf { it.value.selfForks }
                str.appendLine("$indent|__ $method | States ${self}/${total} | Forks ${selfForks}/${totalForks}")
                for ((i, child) in inst.sortedBy { it.value.inst?.location?.index }) {
                    str.appendLine("$indent$INDENT$i | States ${child.self}/${child.total} | Forks ${child.selfForks}/${child.totalForks}")
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

    private fun report(): String = buildString {
        reportUnprocessedStates()
        reportMethodStats()
        reportProfile()
    }

    private fun StringBuilder.reportUnprocessedStates() {
        val unprocessedStates = collectUnprocessedStates()
        val unprocessedMethods = unprocessedStates.groupBy { it.entrypoint }.mapValues { it.value.size }

        appendLine("Unprocessed states: ${unprocessedStates.size}")
        unprocessedMethods.entries.sortedByDescending { it.key.enclosingClass.name }.forEach {
            appendLine("${it.key} | ${it.value}")
        }
    }

    private fun StringBuilder.reportMethodStats() {
        val methodStats = aggregateStats()

        appendLine("Method stats:")
        for ((cls, methods) in methodStats) {
            appendLine("-".repeat(20))
            appendLine("$cls")
            for ((method, cnt) in methods) {
                appendLine("$method | $cnt | ${methodCalls[method]?.values?.sum()}")
            }
        }
    }

    private fun StringBuilder.reportProfile() {
        val profileStats = aggregateStackTraces(slice = null)

        appendLine("Profile:")
        profileStats.print(this, indent = "")
    }

    private fun collectUnprocessedStates(): List<JcState> {
        val unprocessedStates = mutableListOf<JcState>()
        while (!pathSelector.isEmpty()) {
            val state = pathSelector.peek()
            pathSelector.remove(state)
            unprocessedStates += state
        }
        return unprocessedStates
    }

    private class JcStackTraceTracker {
        private val stackTraceTrie = TrieNode.root<JcInst, Unit> { }

        fun getRoot(): TrieNode<JcInst, Unit> = stackTraceTrie

        fun getStackTrace(state: JcState): TrieNode<JcInst, Unit> {
            var node = stackTraceTrie
            for (frame in state.callStack) {
                val inst = frame.returnSite?.originalInst() ?: continue
                node = node.add(inst) {}
            }
            return node.add(state.currentStatement.originalInst()) {}
        }
    }
}
