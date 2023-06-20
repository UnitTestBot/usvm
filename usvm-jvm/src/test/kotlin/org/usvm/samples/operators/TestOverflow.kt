package org.usvm.samples.operators

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestOverflow : JavaMethodTestRunner() {
    @Test
    @Disabled("TODO: disabled due to JacoDB incorrect types of local variables")
    fun `Test shortOverflow`() {
        checkWithExceptionPropertiesMatches(
            Overflow::shortOverflow,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r ->
                require(r.isSuccess)
                true
            }
        )
    }
}
