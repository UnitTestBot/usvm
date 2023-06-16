package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SimplifyNegatedBinaryExpression")
internal class DoubleWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() {
        checkExecutionMatches(
            DoubleWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x >= 0 && r!!.toDouble() >= 0 },
            { _, x, r -> (x < 0 || x.isNaN()) && (r!!.toDouble() > 0 || r.isNaN()) },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkExecutionMatches(
            DoubleWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r >= 0 },
            { _, x, r -> (x < 0 || x.isNaN()) && (r > 0 || r.isNaN()) },
        )
    }
}