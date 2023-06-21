package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class PredicateNotExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@4640195a")
    fun testPredicateNotExample() {
        checkDiscoveredProperties(
            PredicateNotExample::predicateNotExample,
            eq(2),
            { _, a, r -> a == 5 && r == false },
            { _, a, r -> a != 5 && r == true },
        )
    }
}
