package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.Options
import org.usvm.util.UsvmTest


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

    @UsvmTest([Options([PathSelectionStrategy.BFS], stopOnCoverage = -1)])
    fun testSum(options: UMachineOptions) {
        withOptions(options) {
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
}
