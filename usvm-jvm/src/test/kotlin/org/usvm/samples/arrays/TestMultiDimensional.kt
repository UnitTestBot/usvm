package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

class TestMultiDimensional : JavaMethodTestRunner() {
    @Test
    fun `Test sumOf`() {
        checkPropertiesMatches(
            MultiDimensional::sumOf,
            ge(1),
            { x, a, b, r -> r == x.sumOf(a, b) }
        )
    }

    @Test
    @Disabled("TODO: multidimensional arrays")
    fun `Test sumOfMultiNewArray`() {
        checkPropertiesMatches(
            MultiDimensional::sumOfMultiNewArray,
            ge(1),
            { x, a, b, r -> r == x.sumOfMultiNewArray(a, b) }
        )
    }
}