package org.usvm.samples.primitives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

class FloatingPointIntegralBoundariesTest : JavaMethodTestRunner() {
    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    @Test
    fun `double at two to the power 63 saturates to long max`() {
        val boundary = Math.scalb(1.0, 63)

        checkDiscoveredProperties(
            FloatingPointIntegralBoundaries::doubleAtLongUpperBoundary,
            eq(count = 2),
            { value, result -> value == boundary && result == true },
            { value, result -> value != boundary && result == false },
        )
    }

    @Test
    fun `float at two to the power 31 saturates to int max`() {
        val boundary = Math.scalb(1.0f, 31)

        checkDiscoveredProperties(
            FloatingPointIntegralBoundaries::floatAtIntUpperBoundary,
            eq(count = 2),
            { value, result -> value == boundary && result == true },
            { value, result -> value != boundary && result == false },
        )
    }

    @Test
    fun `float at two to the power 63 saturates to long max`() {
        val boundary = Math.scalb(1.0f, 63)

        checkDiscoveredProperties(
            FloatingPointIntegralBoundaries::floatAtLongUpperBoundary,
            eq(count = 2),
            { value, result -> value == boundary && result == true },
            { value, result -> value != boundary && result == false },
        )
    }
}
