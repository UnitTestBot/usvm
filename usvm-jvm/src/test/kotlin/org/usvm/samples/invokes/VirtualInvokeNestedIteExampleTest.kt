package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

internal class VirtualInvokeNestedIteExampleTest : JavaMethodTestRunner() {
    @Test
    fun testNestedIteWithVirtualInvokes() {
        checkDiscoveredProperties(
            VirtualInvokeNestedIteExample::virtualInvokeBySymbolicIndex,
            ignoreNumberOfAnalysisResults,
            { _, index, result -> index == 0 && result == 1 },
            { _, index, result -> index == 1 && result == 2 },
            { _, index, result -> index == 2 && result == 3 },
            { _, index, result -> index == 3 && result == 4 },
        )
    }
}
