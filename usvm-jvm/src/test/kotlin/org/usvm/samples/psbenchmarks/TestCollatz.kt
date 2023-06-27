package org.usvm.samples.psbenchmarks

import org.usvm.MachineOptions
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest

class TestCollatz : JavaMethodTestRunner() {

    @UsvmTest(
        [
            Options([PathSelectionStrategy.RANDOM_PATH]),
            Options([PathSelectionStrategy.DFS]),
            Options([PathSelectionStrategy.BFS, PathSelectionStrategy.DFS], PathSelectorCombinationStrategy.PARALLEL)
        ]
    )
    fun `Test collatzBomb1()`(options: MachineOptions) {
        withOptions(options) {
            checkWithExceptionPropertiesMatches(
                Collatz::collatzBomb1,
                ignoreNumberOfAnalysisResults,
                { _, _, r -> r.isSuccess && r.getOrThrow() == 1 },
                { _, _, r -> r.isSuccess && r.getOrThrow() == 2 },
            )
        }
    }
}
