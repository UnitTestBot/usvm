package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

// TODO failed Kotlin compilation (generics) SAT-1332
class SimpleLambdaExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testBiFunctionLambdaExample() {
        checkWithExceptionExecutionMatches(
            SimpleLambdaExamples::biFunctionLambdaExample,
            { _, _, b, r -> b == 0 && r.isException<ArithmeticException>() },
            { _, a, b, r -> b != 0 && r.getOrThrow() == a / b },
        )
    }

    @Test
    fun testChoosePredicate() {
        checkExecutionMatches(
            SimpleLambdaExamples::choosePredicate,
            { _, b, r -> b && !r!!.test(null) && r.test(0) },
            { _, b, r -> !b && r!!.test(null) && !r.test(0) }, // coverage could not be calculated since method result is lambda
        )
    }
}
