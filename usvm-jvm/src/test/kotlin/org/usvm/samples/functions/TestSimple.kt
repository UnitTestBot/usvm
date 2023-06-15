package org.usvm.samples.functions

import org.junit.jupiter.api.Test
import org.usvm.samples.TestRunner


class TestSimple : TestRunner() {

    @Test
    fun `Test calcTwoFunctions`() {
        run(
            Simple::calcTwoFunctions,
            { _, x, y, r -> r == 0 && y > 0 && x * x + y < 0 },
            { _, x, y, r -> r == 1 && !(y > 0 && x * x + y < 0) },
        )
    }

    @Test
    fun `Test factorial`() {
        run(
            Simple::factorial,
            { _, x, r -> (1..x).fold(1, Int::times) == r },
        )
    }
}