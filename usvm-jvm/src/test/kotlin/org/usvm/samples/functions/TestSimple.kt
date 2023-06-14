package org.usvm.samples.functions

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


class TestSimple : JavaMethodTestRunner() {

    @Test
    fun `Test calcTwoFunctions`() {
        checkExecutionMatches(
            Simple::calcTwoFunctions,
            ignoreNumberOfAnalysisResults,
            { _, x, y, r -> r == 0 && y > 0 && x * x + y < 0 },
            { _, x, y, r -> r == 1 && !(y > 0 && x * x + y < 0) },
        )
    }

    @Test
    fun `Test factorial`() {
        checkPropertiesMatches(
            Simple::factorial,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> (1..x).fold(1, Int::times) == r },
        )
    }
}