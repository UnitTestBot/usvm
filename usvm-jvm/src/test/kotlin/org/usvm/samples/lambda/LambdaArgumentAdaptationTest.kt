package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class LambdaArgumentAdaptationTest : ApproximationsTestRunner() {
    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    @Test
    fun `boxed predicate argument is unboxed for the primitive method reference`() {
        checkDiscoveredProperties(
            LambdaArgumentAdaptation::isWhitespace,
            eq(count = 3),
            { value, result -> value == ' '.code && result == 1 },
            { value, result -> value == 'A'.code && result == 0 },
            { value, result -> value != ' '.code && value != 'A'.code && result == -1 },
        )
    }

    @Test
    fun `unboxing a nullable lambda argument preserves NullPointerException`() {
        checkDiscoveredPropertiesWithExceptions(
            LambdaArgumentAdaptation::boxedPositive,
            eq(count = 3),
            { value, result -> value == null && result.isException<NullPointerException>() },
            { value, result -> value != null && value > 0 && result.getOrNull() == true },
            { value, result -> value != null && value <= 0 && result.getOrNull() == false },
        )
    }

    @Test
    fun `unboxing a lambda argument widens to the implementation primitive type`() {
        checkDiscoveredPropertiesWithExceptions(
            LambdaArgumentAdaptation::boxedWideningPositive,
            eq(count = 3),
            { value, result -> value == null && result.isException<NullPointerException>() },
            { value, result -> value != null && value > 0 && result.getOrNull() == true },
            { value, result -> value != null && value <= 0 && result.getOrNull() == false },
        )
    }

    @Test
    fun `primitive lambda arguments keep their values`() {
        checkDiscoveredProperties(
            LambdaArgumentAdaptation::primitivePositive,
            eq(count = 2),
            { value, result -> value > 0 && result == true },
            { value, result -> value <= 0 && result == false },
        )
    }
}
