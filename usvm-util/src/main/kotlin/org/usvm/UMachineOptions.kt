package org.usvm

enum class SolverType {
    YICES,
    Z3
}

enum class PathSelectionStrategy {
    /**
     * Selects the states in depth-first order.
     */
    DFS,
    /**
     * Selects the states in breadth-first order.
     */
    BFS,
    /**
     * Selects the next state by descending from root to leaf in
     * symbolic execution tree. The child on each step is selected randomly.
     *
     * See KLEE's random path search heuristic.
     */
    RANDOM_PATH,
    /**
     * Gives priority to states with shorter path lengths.
     * The state with the shortest path is always selected.
     */
    DEPTH,
    /**
     * Gives priority to states with shorter path lengths.
     * States are selected randomly with distribution based on path length.
     */
    DEPTH_RANDOM,
    /**
     * Gives priority to states with less number of forks.
     * The state with the least number of forks is always selected.
     */
    FORK_DEPTH,
    /**
     * Gives priority to states with less number of forks.
     * States are selected randomly with distribution based on number of forks.
     */
    FORK_DEPTH_RANDOM,
    /**
     * Gives priority to states closer to uncovered instructions in application
     * graph.
     * The closest to uncovered instruction state is always selected.
     */
    CLOSEST_TO_UNCOVERED,
    /**
     * Gives priority to states closer to uncovered instructions in application
     * graph.
     * States are selected randomly with distribution based on distance to uncovered instructions.
     */
    CLOSEST_TO_UNCOVERED_RANDOM
}

enum class TargetReproductionPathSelectionStrategy {
    DETERMINISTIC,
    RANDOMIZED
}

enum class PathSelectorCombinationStrategy {
    /**
     * Multiple path selectors have the common state set and are interleaved.
     */
    INTERLEAVED,

    /**
     * Multiple path selectors have independent state sets and are interleaved.
     */
    PARALLEL
}

// TODO: add module/package coverage zone
enum class CoverageZone {
    /**
     * Only target method coverage is considered.
     */
    METHOD,
    /**
     * Coverage of methods in target method's class id considered.
     */
    CLASS,
    /**
     * Coverage of methods transitively reachable from a start method.
     */
    TRANSITIVE
}

data class UMachineOptions(
    /**
     * State selection heuristics.
     * If multiple heuristics are specified, they are combined according to [pathSelectorCombinationStrategy].
     *
     * @see PathSelectionStrategy
     */
    val pathSelectionStrategies: List<PathSelectionStrategy> = listOf(PathSelectionStrategy.BFS),
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
    val stepLimit: ULong? = null,
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

data class TargetReproductionOptions(
    val pathSelectionStrategy: TargetReproductionPathSelectionStrategy = TargetReproductionPathSelectionStrategy.RANDOMIZED,
    /**
     * Seed used for random operations.
     */
    val randomSeed: Long = 0,
    /**
     * Optional limit of symbolic execution steps to stop execution on.
     */
    val stepLimit: ULong? = null,
    /**
     * Optional timeout in milliseconds to stop execution on.
     */
    val timeoutMs: Long? = 20_000,
)
