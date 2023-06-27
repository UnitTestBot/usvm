package org.usvm.machine

import org.usvm.ps.Bfs
import org.usvm.ps.PathSelectionStrategy

data class JcMachineOptions(
    /**
     * State selection heuristics. If multiple heuristics are specified, they are
     * interleaved.
     *
     * @see PathSelectionStrategy
     */
    val pathSelectionStrategy: List<PathSelectionStrategy> = listOf(Bfs),
    /**
     * Seed used for random operations.
     */
    val randomSeed: Long = 0,
    /**
     * Code coverage percent to stop execution on. Considered only if in range [1..100].
     */
    val expectedCoverage: Int = 100,
    /**
     * Optional limit of symbolic execution steps.
     */
    val stepLimit: ULong? = null
)
