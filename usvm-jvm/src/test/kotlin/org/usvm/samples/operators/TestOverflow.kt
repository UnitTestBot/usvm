package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestOverflow : JavaMethodTestRunner() {
    @Test
    fun `Test shortOverflow`() {
        checkDiscoveredPropertiesWithExceptions(
            Overflow::shortOverflow,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r ->
                require(r.isSuccess)
                true
            }
        )
    }
}
