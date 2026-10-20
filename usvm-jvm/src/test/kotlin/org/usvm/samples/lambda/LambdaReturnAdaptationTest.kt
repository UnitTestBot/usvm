package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class LambdaReturnAdaptationTest : ApproximationsTestRunner() {
    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    @Test
    fun `primitive boolean result is boxed for erased Function return`() {
        checkDiscoveredProperties(
            LambdaReturnAdaptation::boxedBoolean,
            eq(count = 2),
            { value, result -> value > 0 && result == true },
            { value, result -> value <= 0 && result == false },
        )
    }

    @Test
    fun `primitive integer result is boxed for IntFunction return`() {
        checkDiscoveredProperties(
            LambdaReturnAdaptation::boxedInteger,
            eq(count = 1),
            { value, result -> result == value },
        )
    }

    @Test
    fun `boxed result is unboxed and null preserves NullPointerException`() {
        checkDiscoveredPropertiesWithExceptions(
            LambdaReturnAdaptation::unboxedInteger,
            eq(count = 2),
            { value, result -> value == null && result.isException<NullPointerException>() },
            { value, result -> value != null && result.getOrNull() == value },
        )
    }

    @Test
    fun `primitive result widens to functional interface return type`() {
        checkDiscoveredProperties(
            LambdaReturnAdaptation::widenedInteger,
            eq(count = 1),
            { value, result -> result == value.toLong() },
        )
    }

}
