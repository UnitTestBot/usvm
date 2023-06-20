package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import kotlin.math.max

internal class StaticInvokeExampleTest : JavaMethodTestRunner() {
    @Test
    fun testMaxForThree() {
        val method = StaticInvokeExample::maxForThree
        checkDiscoveredProperties(
            method, // two executions can cover all branches
            eq(4),
            { x, y, _, _ -> x > y },
            { x, y, _, _ -> x <= y },
            { x, y, z, _ -> max(x, y.toInt()) > z },
            { x, y, z, _ -> max(x, y.toInt()) <= z },
        )
    }
}