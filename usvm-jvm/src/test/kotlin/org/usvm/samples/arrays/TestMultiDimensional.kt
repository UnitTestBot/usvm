package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge
import org.usvm.util.disableTest

class TestMultiDimensional : JavaMethodTestRunner() {
    @Test
    fun `Test sumOf`() {
        checkDiscoveredProperties(
            MultiDimensional::sumOf,
            ge(1),
            { x, a, b, r -> r == x.sumOf(a, b) }
        )
    }

    @Test
    fun `Test sumOfMultiNewArray`() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            MultiDimensional::sumOfMultiNewArray,
            ge(1),
            { x, a, b, r -> r == x.sumOfMultiNewArray(a, b) }
        )
    }
}