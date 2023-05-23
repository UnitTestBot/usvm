package org.usvm.samples.loops

import org.junit.jupiter.api.Test
import org.usvm.TestRunner

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
}
