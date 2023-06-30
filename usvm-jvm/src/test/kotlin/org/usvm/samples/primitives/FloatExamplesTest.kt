package org.usvm.samples.primitives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class FloatExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testFloatInfinity() {
        checkDiscoveredProperties(
            FloatExamples::floatInfinity,
            eq(3),
            { _, f, r -> f == Float.POSITIVE_INFINITY && r == 1 },
            { _, f, r -> f == Float.NEGATIVE_INFINITY && r == 2 },
            { _, f, r -> !f.isInfinite() && r == 3 },
        )
    }
}