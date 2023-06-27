package org.usvm.samples.psbenchmarks

import org.usvm.MachineOptions
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest

class TestLoanExam : JavaMethodTestRunner() {

    @UsvmTest(
        [
            Options([PathSelectionStrategy.BFS], stopOnCoverage = 93),
            Options([PathSelectionStrategy.RANDOM_PATH], stopOnCoverage = 93),
            Options([PathSelectionStrategy.BFS, PathSelectionStrategy.DFS], PathSelectorCombinationStrategy.PARALLEL, 93)
        ]
    )
    fun `Test getCreditPercent`(options: MachineOptions) {
        withOptions(options) {
            checkWithExceptionPropertiesMatches(
                LoanExam::getCreditPercent,
                ignoreNumberOfAnalysisResults,
                { _, _, r -> r.isSuccess && r.getOrThrow() == 12 }
            )
        }
    }
}
