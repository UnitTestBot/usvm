package org.usvm.machine

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState
import org.usvm.statistics.UDebugProfileObserver
import org.usvm.util.originalInst
import java.util.concurrent.atomic.LongAdder

private typealias StatsCounter = LongAdder

/**
 * Collect JcMachine profile (like async-profiler) for the debug purposes.
 * */
class JcDebugProfileObserver(
    private val pathSelector: UPathSelector<JcState>
) : UDebugProfileObserver<JcInst, JcMethod, JcState>(
    statementOperations = object : StatementOperations<JcInst, JcMethod, JcState> {
        override fun getMethodOfStatement(statement: JcInst) = statement.location.method
        override fun getStatementIndexInMethod(statement: JcInst) = statement.location.index
        override fun getMethodToCallIfCallStatement(statement: JcInst) =
            (statement as? JcConcreteMethodCallInst)?.method
        override fun getOriginalInst(statement: JcInst): JcInst = statement.originalInst()
    },
) {
    private fun aggregateStats(): List<Map.Entry<JcClassOrInterface, List<Map.Entry<JcMethod, Long>>>> {
        val methodStats = instructionStats.entries
            .groupBy({ it.key.location.method }, { it.value })
            .mapValues { it.value.sumOf { it.values.sum() } }

        val classStats = methodStats.entries.groupBy { it.key.enclosingClass }
        val orderedMethodStats = classStats.mapValues { it.value.sortedByDescending { it.value } }
        return orderedMethodStats.entries.sortedByDescending { it.value.sumOf { it.value } }
    }

    override fun report(): String = buildString {
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
                val methodCallsCnt = methodCalls[method]?.values?.sum()
                    ?: "NO CALLS" // e.g. entrypoint method

                appendLine("$method | $cnt | $methodCallsCnt")
            }
        }
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

    private fun Iterable<StatsCounter>.sum(): Long = sumOf { it.sum() }
}
