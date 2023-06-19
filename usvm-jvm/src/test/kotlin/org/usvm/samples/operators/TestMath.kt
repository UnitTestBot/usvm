package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestMath : JavaMethodTestRunner() {
    @Test
    fun `Test complexWithLocals`() {
        checkWithExceptionPropertiesMatches(
            Math::kek,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r ->
                require(r.isSuccess)
                true
            }
        )
    }
}
