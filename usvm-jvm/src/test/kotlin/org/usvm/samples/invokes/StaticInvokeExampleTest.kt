package org.usvm.samples.invokes

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between

import kotlin.math.max

internal class StaticInvokeExampleTest : JavaMethodTestRunner() {
    @Test
    fun testMaxForThree() {
        val method = StaticInvokeExample::maxForThree
        checkExecutionMatches(
            method, // two executions can cover all branches
            { x, y, _, _ -> x > y },
            { x, y, _, _ -> x <= y },
            { x, y, z, _ -> max(x, y.toInt()) > z },
            { x, y, z, _ -> max(x, y.toInt()) <= z },
        )
    }
}