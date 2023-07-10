package org.usvm.samples.psbenchmarks

import org.junit.jupiter.api.Disabled
import org.usvm.*
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest

class TestLoanExam : JavaMethodTestRunner() {

    @UsvmTest(
        [
            Options([PathSelectionStrategy.BFS], stopOnCoverage = 93, coverageZone = CoverageZone.METHOD, solverType = SolverType.YICES),
            Options([PathSelectionStrategy.RANDOM_PATH], stopOnCoverage = 93, coverageZone = CoverageZone.METHOD, solverType = SolverType.YICES),
            Options(
                [PathSelectionStrategy.BFS, PathSelectionStrategy.DFS],
                PathSelectorCombinationStrategy.PARALLEL,
                stopOnCoverage = 93,
                coverageZone = CoverageZone.METHOD,
                solverType = SolverType.YICES
            )
        ]
    )
    @Disabled("Some properties were not discovered at positions (from 0): [0]. Not enough time?..")
    fun `Test getCreditPercent`(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredPropertiesWithExceptions(
                LoanExam::getCreditPercent,
                ignoreNumberOfAnalysisResults,
                { _, _, r -> r.isSuccess && r.getOrThrow() == 12 }
            )
        }
    }
}
