package org.usvm.samples.controlflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge

import java.math.RoundingMode.CEILING
import java.math.RoundingMode.DOWN
import java.math.RoundingMode.HALF_DOWN
import java.math.RoundingMode.HALF_EVEN
import java.math.RoundingMode.HALF_UP

internal class SwitchTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleSwitch() {
        checkDiscoveredProperties(
            Switch::simpleSwitch,
            ge(4),
            { _, x, r -> x == 10 && r == 10 },
            { _, x, r -> (x == 11 || x == 12) && r == 12 }, // fall-through has it's own branch
            { _, x, r -> x == 13 && r == 13 },
            { _, x, r -> x !in 10..13 && r == -1 }, // one for default is enough
        )
    }

    @Test
    fun testLookupSwitch() {
        checkDiscoveredProperties(
            Switch::lookupSwitch,
            ge(4),
            { _, x, r -> x == 0 && r == 0 },
            { _, x, r -> (x == 10 || x == 20) && r == 20 }, // fall-through has it's own branch
            { _, x, r -> x == 30 && r == 30 },
            { _, x, r -> x !in setOf(0, 10, 20, 30) && r == -1 } // one for default is enough
        )
    }

    @Test
    fun testEnumSwitch() {
        checkDiscoveredProperties(
            Switch::enumSwitch,
            eq(7),
            { _, m, _ -> m == null }, // NPE
            { _, m, r -> m == HALF_DOWN && r == 1 }, // We will minimize two of these branches
            { _, m, r -> m == HALF_EVEN && r == 1 }, // We will minimize two of these branches
            { _, m, r -> m == HALF_UP && r == 1 }, // We will minimize two of these branches
            { _, m, r -> m == DOWN && r == 2 },
            { _, m, r -> m == CEILING && r == 3 },
            { _, m, r -> m !in setOf(HALF_DOWN, HALF_EVEN, HALF_UP, DOWN, CEILING) && r == -1 },
        )
    }
}