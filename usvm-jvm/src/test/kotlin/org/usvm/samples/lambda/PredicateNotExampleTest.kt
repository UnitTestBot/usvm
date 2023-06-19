package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class PredicateNotExampleTest : JavaMethodTestRunner() {
    @Test
    fun testPredicateNotExample() {
        checkExecutionMatches(
            PredicateNotExample::predicateNotExample,
            { _, a, r -> a == 5 && r == false },
            { _, a, r -> a != 5 && r == true },
        )
    }
}
