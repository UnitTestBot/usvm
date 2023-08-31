package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class CustomPredicateExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testNoCapturedValuesPredicateCheck() {
        checkDiscoveredPropertiesWithExceptions(
            CustomPredicateExample::noCapturedValuesPredicateCheck,
            eq(3),
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testCapturedLocalVariablePredicateCheck() {
        checkDiscoveredPropertiesWithExceptions(
            CustomPredicateExample::capturedLocalVariablePredicateCheck,
            eq(3),
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testCapturedParameterPredicateCheck() {
        checkDiscoveredPropertiesWithExceptions(
            CustomPredicateExample::capturedParameterPredicateCheck,
            eq(3),
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testCapturedStaticFieldPredicateCheck() {
        checkDiscoveredPropertiesWithExceptions(
            CustomPredicateExample::capturedStaticFieldPredicateCheck,
            eq(3),
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    @Disabled("java.lang.Integer#valueOf(int). Native calls in IntegerCache#<clinit>")
    fun testCapturedNonStaticFieldPredicateCheck() {
        checkDiscoveredPropertiesWithExceptions(
            CustomPredicateExample::capturedNonStaticFieldPredicateCheck,
            eq(3),
            { _, predicate, x, r -> !predicate.test(x) && r.getOrNull() == false },
            { _, predicate, x, r -> predicate.test(x) && r.getOrNull() == true },
            { _, predicate, _, r -> predicate == null && r.isException<NullPointerException>() },
        )
    }
}
