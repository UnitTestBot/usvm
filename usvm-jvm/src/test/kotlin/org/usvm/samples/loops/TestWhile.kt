package org.usvm.samples.loops

import org.junit.jupiter.api.Test
import org.usvm.TestRunner

class TestWhile : TestRunner() {
    @Test
    fun testSingleLoop() {
        run(
            While::singleLoop,
            { _, n, r -> r == 0 && n <= 0 },
            { _, n, r -> r == 1 && (n >= 0) },
            { _, n, r -> r == 2 && (n in 1..4)}
        )
    }

    @Test
    fun testSmallestPowerOfTwo() {
        run(
            While::smallestPowerOfTwo,
            { _, n, r -> r == 0 && n.and(n - 1) == 0 },
            { _, n, r -> r == 1 && n <= 0 },
            { _, n, r -> r == 2 && n > 0 && n.and(n - 1) != 0 }
        )
    }
}
