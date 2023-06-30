package org.usvm.samples.functions

import org.usvm.UMachineOptions
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest


class TestSimple : JavaMethodTestRunner() {

    @UsvmTest(
        [
            Options([PathSelectionStrategy.BFS, PathSelectionStrategy.DFS], PathSelectorCombinationStrategy.PARALLEL),
            Options([PathSelectionStrategy.CLOSEST_TO_UNCOVERED])
        ]
    )
    fun `Test calcTwoFunctions`(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredProperties(
                Simple::calcTwoFunctions,
                ignoreNumberOfAnalysisResults,
                { _, x, y, r -> r == 0 && y > 0 && x * x + y < 0 },
                { _, x, y, r -> r == 1 && !(y > 0 && x * x + y < 0) },
            )
        }
    }

    @UsvmTest(
        [
            Options([PathSelectionStrategy.BFS, PathSelectionStrategy.DFS], PathSelectorCombinationStrategy.PARALLEL),
            Options([PathSelectionStrategy.CLOSEST_TO_UNCOVERED])
        ]
    )
    fun `Test factorial`(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredProperties(
                Simple::factorial,
                ignoreNumberOfAnalysisResults,
                { _, x, r -> (1..x).fold(1, Int::times) == r },
            )
        }
    }
}
