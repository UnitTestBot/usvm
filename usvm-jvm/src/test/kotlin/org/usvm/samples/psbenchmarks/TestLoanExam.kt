package org.usvm.samples.psbenchmarks

import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
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
