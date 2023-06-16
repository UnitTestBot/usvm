package org.usvm.samples.functions

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestThrowing : JavaMethodTestRunner() {
    @Test
    fun `Test throwSometimes`() {
        checkWithExceptionExecutionMatches(
            Throwing::throwSometimes,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x == 1 && r.isFailure && r.exceptionOrNull() is IllegalArgumentException },
            { _, x, r -> x != 1 && r.isSuccess },
        )
    }

}