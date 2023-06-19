package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class CustomPredicateExampleTest : JavaMethodTestRunner() {
    @Test
    fun testNoCapturedValuesPredicateCheck() {
        checkWithExceptionExecutionMatches(
            CustomPredicateExample::noCapturedValuesPredicateCheck,
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCapturedLocalVariablePredicateCheck() {
        checkWithExceptionExecutionMatches(
            CustomPredicateExample::capturedLocalVariablePredicateCheck,
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCapturedParameterPredicateCheck() {
        checkWithExceptionExecutionMatches(
            CustomPredicateExample::capturedParameterPredicateCheck,
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCapturedStaticFieldPredicateCheck() {
        checkWithExceptionExecutionMatches(
            CustomPredicateExample::capturedStaticFieldPredicateCheck,
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCapturedNonStaticFieldPredicateCheck() {
        checkWithExceptionExecutionMatches(
            CustomPredicateExample::capturedNonStaticFieldPredicateCheck,
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }
}
