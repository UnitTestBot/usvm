package org.usvm.ml

import org.usvm.CoverageZone
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.SolverType

enum class OtherPathSelectionStrategy {
    BFS_WITH_LOGGING,
    INFERENCE_WITH_LOGGING,
}

data class OtherUMachineOptions(
    /**
     * State selection heuristics.
     * If multiple heuristics are specified, they are combined according to [pathSelectorCombinationStrategy].
     *
     * @see PathSelectionStrategy
     */
    val pathSelectionStrategies: List<OtherPathSelectionStrategy> = listOf(OtherPathSelectionStrategy.INFERENCE_WITH_LOGGING),
    /**
     * Strategy to combine multiple [pathSelectionStrategies].
     *
     * @see PathSelectorCombinationStrategy
     */
    val pathSelectorCombinationStrategy: PathSelectorCombinationStrategy = PathSelectorCombinationStrategy.INTERLEAVED,
    /**
     * Seed used for random operations.
     */
    val randomSeed: Long = 0,
    /**
     * Code coverage percent to stop execution on. Considered only if in range [1..100].
     */
    val stopOnCoverage: Int = 100,
    /**
     * Optional limit of symbolic execution steps to stop execution on.
     */
    val stepLimit: ULong? = 1500u,
    /**
     * Optional limit of collected states to stop execution on.
     */
    val collectedStatesLimit: Int? = null,
    /**
     * Optional timeout in milliseconds to stop execution on.
     */
    val timeoutMs: Long? = 20_000,
    /**
     * A number of steps from the last terminated state.
     */
    val stepsFromLastCovered: Long? = null,
    /**
     * Scope of methods which coverage is considered.
     *
     * @see CoverageZone
     */
    val coverageZone: CoverageZone = CoverageZone.METHOD,
    /**
     * Whether we should prefer exceptional state in the queue to the regular ones.
     */
    val exceptionsPropagation: Boolean = true,
    /**
     * SMT solver type used for path constraint solving.
     */
    val solverType: SolverType = SolverType.Z3
)
