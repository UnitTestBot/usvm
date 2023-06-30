package org.usvm.samples.operators

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestLogic : JavaMethodTestRunner() {
    @Test
//    @Disabled("Some properties were not discovered at positions (from 0): [1]")
    fun `Test complexWithLocals`() {
        checkDiscoveredProperties(
            Logic::complexWithLocals,
            ignoreNumberOfAnalysisResults,
            { _, x, y, z, r -> r != null && r && (x.toLong() or y.toLong() or z) != 1337.toLong() },
            { _, x, y, z, r -> r != null && !r && (x.toLong() or y.toLong() or z) == 1337.toLong() },
        )
    }
}
