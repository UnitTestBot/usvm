package org.usvm.mcp.exec

import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Factories for [UMachineOptions] used by the MCP tools. Mirrors the option
 * sets battle-tested in usvm-ts test infrastructure (`TsMethodTestRunner`,
 * reachability tests), but with finite, caller-controlled budgets.
 */
object AnalysisOptions {

    /** Path exploration aimed at covering new code: test generation, exceptions. */
    fun coverage(timeout: Duration): UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
        exceptionsPropagation = true,
        timeout = timeout,
        stepsFromLastCovered = STEPS_FROM_LAST_COVERED,
        solverType = SolverType.YICES,
        solverTimeout = SOLVER_TIMEOUT,
        typeOperationsTimeout = TYPE_OPERATIONS_TIMEOUT,
    )

    /** Directed exploration towards a reachability target. */
    fun targeted(timeout: Duration): UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        exceptionsPropagation = true,
        stopOnTargetsReached = true,
        timeout = timeout,
        stepsFromLastCovered = STEPS_FROM_LAST_COVERED,
        solverType = SolverType.YICES,
        solverTimeout = SOLVER_TIMEOUT,
        typeOperationsTimeout = TYPE_OPERATIONS_TIMEOUT,
    )

    private const val STEPS_FROM_LAST_COVERED = 3500L
    private val SOLVER_TIMEOUT = 5.seconds
    private val TYPE_OPERATIONS_TIMEOUT = 1.seconds
}
