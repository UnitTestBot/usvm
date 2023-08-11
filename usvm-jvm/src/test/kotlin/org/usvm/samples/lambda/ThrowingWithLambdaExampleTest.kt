package org.usvm.samples.lambda

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


class ThrowingWithLambdaExampleTest : JavaMethodTestRunner() {
    @Test
    fun testAnyExample() = disableTest("Support assumptions") {
        checkDiscoveredProperties(
            ThrowingWithLambdaExample::anyExample,
            eq(4),
            { _, l, _, _ -> l == null },
            { _, l, _, r -> l.isEmpty() && r == false },
            { _, l, _, r -> l.isNotEmpty() && 42 in l && r == true },
            { _, l, _, r -> l.isNotEmpty() && 42 !in l && r == false }, // TODO failed coverage calculation
        )
    }
}
