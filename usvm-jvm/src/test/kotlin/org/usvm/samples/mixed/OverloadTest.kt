package org.usvm.samples.mixed

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class OverloadTest : JavaMethodTestRunner() {
    @Test
    fun testSignOneParam() {
        checkDiscoveredProperties(
            Overload::sign,
            eq(3),
            { _, x, r -> x < 0 && r == -1 },
            { _, x, r -> x == 0 && r == 0 },
            { _, x, r -> x > 0 && r == 1 }
        )
    }

    @Test
    fun testSignTwoParams() {
        checkDiscoveredProperties(
            Overload::sign,
            eq(3),
            { _, x, y, r -> x + y < 0 && r == -1 },
            { _, x, y, r -> x + y == 0 && r == 0 },
            { _, x, y, r -> x + y > 0 && r == 1 }
        )
    }
}