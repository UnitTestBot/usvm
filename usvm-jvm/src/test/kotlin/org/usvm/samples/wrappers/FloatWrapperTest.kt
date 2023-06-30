package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SimplifyNegatedBinaryExpression")
internal class FloatWrapperTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Can not set static final float field java.lang.Float.POSITIVE_INFINITY to java.lang.Float")
    fun primitiveToWrapperTest() {
        checkDiscoveredProperties(
            FloatWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x >= 0 && r != null && r >= 0 },
            { _, x, r -> (x < 0 || x.isNaN()) && (r != null && r > 0 || r!!.isNaN()) },
        )
    }

    @Test
    @Disabled("Can not set static final float field java.lang.Float.POSITIVE_INFINITY to java.lang.Float")
    fun wrapperToPrimitiveTest() {
        checkDiscoveredProperties(
            FloatWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r != null && r >= 0 },
            { _, x, r -> (x < 0 || x.isNaN()) && (r != null && r > 0 || r!!.isNaN()) },
        )
    }
}