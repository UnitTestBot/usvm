package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class CustomPredicateExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
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
