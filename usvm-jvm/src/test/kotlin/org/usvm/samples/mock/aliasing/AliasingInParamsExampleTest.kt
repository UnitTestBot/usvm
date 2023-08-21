package org.usvm.samples.mock.aliasing

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


internal class AliasingInParamsExampleTest : JavaMethodTestRunner() {
    @Test
    fun testExamplePackageBased() = disableTest("Expected exactly 1 executions, but 4 found") {
        checkDiscoveredProperties(
            AliasingInParamsExample::example,
            eq(1),
            { _, fst, snd, x, r -> fst != snd && x == r },
        )
    }

    @Test
    fun testExample() = disableTest("Expected exactly 2 executions, but 4 found") {
        checkDiscoveredProperties(
            AliasingInParamsExample::example,
            eq(2),
            { _, fst, snd, x, r -> fst == snd && x == r },
            { _, fst, snd, x, r -> fst != snd && x == r },
        )
    }
}