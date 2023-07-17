package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class PredicateNotExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("class org.jacodb.api.PredefinedPrimitive cannot be cast to class org.jacodb.api.JcRefType")
    fun testPredicateNotExample() {
        checkDiscoveredProperties(
            PredicateNotExample::predicateNotExample,
            eq(2),
            { _, a, r -> a == 5 && r == false },
            { _, a, r -> a != 5 && r == true },
        )
    }
}
