package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class SimpleLambdaExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testBiFunctionLambdaExample() {
        checkDiscoveredPropertiesWithExceptions(
            SimpleLambdaExamples::biFunctionLambdaExample,
            eq(2),
            { _, _, b, r -> b == 0 && r.isException<ArithmeticException>() },
            { _, a, b, r -> b != 0 && r.getOrThrow() == a / b },
        )
    }

    @Test
    fun testChoosePredicate() {
        checkDiscoveredProperties(
            SimpleLambdaExamples::choosePredicate,
            eq(2),
            { _, b, r -> b && r != null && !r.test(null) && r.test(0) },
            { _, b, r -> !b && r != null && r.test(null) && !r.test(0) }, // coverage could not be calculated since method result is lambda
        )
    }
}
