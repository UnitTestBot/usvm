package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SimplifyNegatedBinaryExpression")
internal class FloatWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() {
        checkExecutionMatches(
            FloatWrapper::primitiveToWrapper,
            { _, x, r -> x >= 0 && r!! >= 0 },
            { _, x, r -> (x < 0 || x.isNaN()) && (r!! > 0 || r.isNaN()) },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkExecutionMatches(
            FloatWrapper::wrapperToPrimitive,
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r >= 0 },
            { _, x, r -> (x < 0 || x.isNaN()) && (r > 0 || r.isNaN()) },
        )
    }
}