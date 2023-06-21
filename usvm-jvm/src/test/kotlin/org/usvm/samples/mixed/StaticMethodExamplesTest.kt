package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class StaticMethodExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testComplement() {
        val method = StaticMethodExamples::complement
        checkDiscoveredProperties(
            method,
            eq(2),
            { x, r -> x == -2 && r == true },
            { x, r -> x != -2 && r == false }
        )
    }

    @Test
    fun testMax2() {
        val method = StaticMethodExamples::max2
        checkDiscoveredProperties(
            method,
            eq(2),
            { x, y, r -> x > y && r == x },
            { x, y, r -> x <= y && r == y.toInt() }
        )
    }

    @Test
    fun testSum() {
        val method = StaticMethodExamples::sum
        checkDiscoveredProperties(
            method,
            eq(3),
            { x, y, z, r -> x + y + z < -20 && r == (x + y + z).toLong() * 2 },
            { x, y, z, r -> x + y + z > 20 && r == (x + y + z).toLong() * 2 },
            { x, y, z, r -> x + y + z in -20..20 && r == (x + y + z).toLong() }
        )
    }
}