package org.usvm.samples.functions

import org.junit.jupiter.api.Test
import org.usvm.TestRunner

class TestSimple : TestRunner() {

    @Test
    fun testCalcTwoFunctions() {
        run(
            Simple::calcTwoFunctions,
            { _, x, y, r -> r == x * x + y },
        )
    }
}