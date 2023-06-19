package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class ThrowingWithLambdaExampleTest : JavaMethodTestRunner() {
    @Test
    fun testAnyExample() {
        checkExecutionMatches(
            ThrowingWithLambdaExample::anyExample,
            { _, l, _, _ -> l == null },
            { _, l, _, r -> l.isEmpty() && r == false },
            { _, l, _, r -> l.isNotEmpty() && 42 in l && r == true },
            { _, l, _, r -> l.isNotEmpty() && 42 !in l && r == false }, // TODO failed coverage calculation
        )
    }
}
