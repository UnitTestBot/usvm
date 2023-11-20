package org.usvm.statistics

import org.usvm.UState
import kotlin.math.roundToInt

/**
 * Prints a table with coverage, time and steps statistics for each method in [getMethods] result using [print] function.
 */
class StatisticsByMethodPrinter<Method, Statement, State : UState<*, Method, Statement, *, *, State>>(
    private val getMethods: () -> List<Method>,
    private val print: (String) -> Unit,
    private val getMethodSignature: (Method) -> String,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    private val timeStatistics: TimeStatistics<Method, State>,
    private val stepsStatistics: StepsStatistics<Method, State>
) : UMachineObserver<State> {

    override fun onMachineStopped() {
        val methods = getMethods()
        if (methods.isEmpty()) {
            return
        }
        val statsStrings = mutableListOf(StatisticsRow("Method", "Coverage, %", "Time spent, ms", "Steps"))
        methods.forEach {
            val name = getMethodSignature(it)
            val coverage = coverageStatistics.getMethodCoverage(it).roundToInt().toString()
            val time = timeStatistics.getTimeSpentOnMethod(it).inWholeMilliseconds.toString()
            val stepsCount = stepsStatistics.getMethodSteps(it).toString()
            statsStrings.add(StatisticsRow(name, coverage, time, stepsCount))
        }
        val totalCoverage = coverageStatistics.getTotalCoverage().roundToInt().toString()
        val totalTime = timeStatistics.runningTime.inWholeMilliseconds.toString()
        val totalSteps = stepsStatistics.totalSteps.toString()
        statsStrings.add(StatisticsRow("TOTAL", totalCoverage, totalTime, totalSteps))
        val timeColumnWidth = statsStrings.maxOf { it.time.length }
        val stepsColumnWidth = statsStrings.maxOf { it.stepsCount.length }
        val statisticsSb = StringBuilder("\n")
        statsStrings.forEach { (name, coverage, time, steps) ->
            statisticsSb.appendLine(" %-12s | %-${timeColumnWidth}s | %-${stepsColumnWidth}s | %s ".format(coverage, time, steps, name))
        }
        print(statisticsSb.toString())
    }

    private data class StatisticsRow(val methodName: String, val coverage: String, val time: String, val stepsCount: String)
}
