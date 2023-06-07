package org.usvm.samples.loops

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.usvm.forksCount
import org.usvm.samples.TestRunner

class TestWhile : TestRunner() {
    @Test
    fun `Test singleLoop`() {
        run(
            While::singleLoop,
            { _, n, r -> r == 0 && n >= 5 },
            { _, n, r -> r == 1 && n <= 0 },
            { _, n, r -> r == 2 && (n in 1..4)}
        )
    }

    @Test
    fun `Test smallestPowerOfTwo`() {
        run(
            While::smallestPowerOfTwo,
            { _, n, r -> r == 0 && n.and(n - 1) == 0 },
            { _, n, r -> r == 1 && n <= 0 },
            { _, n, r -> r == 2 && n > 0 && n.and(n - 1) != 0 }
        )
    }

    @Test
    fun `Test sumOf`() {
        run(
            While::sumOf,
            { _, n, r -> n * (n + 1) / 2 == r},
        )
    }

    @RepeatedTest(5)
    fun `Test while`() {
        run(
            While::func,
            { _, _, _, _, r -> r == 1 },
            { _, _, _, _, r -> r == 2 },
        )
        println(forksCount)
    }
}
