package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

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
    fun `Test sumOfMultiNewArray`() {
        checkDiscoveredProperties(
            MultiDimensional::sumOfMultiNewArray,
            ge(1),
            { x, a, b, r -> r == x.sumOfMultiNewArray(a, b) }
        )
    }
}