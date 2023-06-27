package org.usvm.samples.psbenchmarks

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestLoanExam : JavaMethodTestRunner() {

    @Test
    fun `Test getCreditPercent`() {
        checkWithExceptionPropertiesMatches(
            LoanExam::getCreditPercent,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isSuccess && r.getOrThrow() == 12 }
        )
    }
}
