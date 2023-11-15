package org.usvm.samples.mock.aliasing

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class AliasingInParamsExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unsupported mock strategies")
    fun testExamplePackageBased() {
        checkDiscoveredProperties(
            AliasingInParamsExample::example,
            eq(1),
            { _, fst, snd, x, r -> fst != snd && x == r },
        )
    }

    @Test
    fun testExample() {
        checkDiscoveredProperties(
            AliasingInParamsExample::example,
            eq(2),
            { _, fst, snd, x, r -> fst == snd && x == r },
            { _, fst, snd, x, r -> fst != snd && x == r },
        )
    }
}