package org.usvm.samples.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class PredicateNotExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0, 1]")
    fun testPredicateNotExample() {
        checkDiscoveredProperties(
            PredicateNotExample::predicateNotExample,
            eq(2),
            { _, a, r -> a == 5 && r == false },
            { _, a, r -> a != 5 && r == true },
        )
    }
}
