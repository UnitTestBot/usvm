package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class MonitorUsageTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleMonitor() {
        checkDiscoveredProperties(
            MonitorUsage::simpleMonitor,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x > 0 && x <= Int.MAX_VALUE - 1 && r == 1 },
        )
    }
}