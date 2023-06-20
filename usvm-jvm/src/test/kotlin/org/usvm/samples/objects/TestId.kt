package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestId : JavaMethodTestRunner() {
    @Test
    fun `Test isOne`() {
        checkDiscoveredProperties(
            Id::isOne,
            ignoreNumberOfAnalysisResults,
            { x, r -> x.isOne && r },
            { x, r -> !x.isOne && !r },
        )
    }
}