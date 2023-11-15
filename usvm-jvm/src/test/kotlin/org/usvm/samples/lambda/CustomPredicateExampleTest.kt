package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

// Use approximations for analysis of Integer#<clinit>
class CustomPredicateExampleTest : ApproximationsTestRunner() {
    @Test
    @Disabled("Expected exactly 3 executions, but 1 found")
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
    @Disabled("Expected exactly 3 executions, but 1 found")
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
    @Disabled("Expected exactly 3 executions, but 1 found")
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
    @Disabled("Expected exactly 3 executions, but 1 found")
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
    @Disabled("Expected exactly 3 executions, but 1 found")
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
