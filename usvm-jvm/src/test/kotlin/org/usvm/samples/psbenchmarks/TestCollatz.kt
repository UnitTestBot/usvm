package org.usvm.samples.psbenchmarks

import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest

class TestCollatz : JavaMethodTestRunner() {

    @UsvmTest(
        [
            Options([PathSelectionStrategy.RANDOM_PATH], solverType = SolverType.YICES),
            Options([PathSelectionStrategy.DFS], solverType = SolverType.YICES),
            Options([PathSelectionStrategy.BFS, PathSelectionStrategy.DFS], PathSelectorCombinationStrategy.PARALLEL, solverType = SolverType.YICES)
        ]
    )
    fun `Test collatzBomb1()`(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredPropertiesWithExceptions(
                Collatz::collatzBomb1,
                ignoreNumberOfAnalysisResults,
                { _, _, r -> r.isSuccess && r.getOrThrow() == 1 },
                { _, _, r -> r.isSuccess && r.getOrThrow() == 2 },
            )
        }
    }
}
