package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class PredicateNotExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testPredicateNotExample() {
        checkDiscoveredProperties(
            PredicateNotExample::predicateNotExample,
            eq(2),
            { _, a, r -> a == 5 && r == false },
            { _, a, r -> a != 5 && r == true },
        )
    }
}
