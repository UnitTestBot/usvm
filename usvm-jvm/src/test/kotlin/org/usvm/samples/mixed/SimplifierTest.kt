package org.usvm.samples.mixed

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimplifierTest: JavaMethodTestRunner() {
    @Test
    @Disabled("Support assumptions")
    fun testSimplifyAdditionWithZero() {
        checkDiscoveredProperties(
            Simplifier::simplifyAdditionWithZero,
            eq(1),
            { _, fst, r -> r != null && r.x == fst.shortValue.toInt() },
        )
    }
}