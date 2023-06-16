package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ByteWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() {
        checkExecutionMatches(
            ByteWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x >= 0 && r!! <= 0 },
            { _, x, r -> x < 0 && r!! < 0 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkExecutionMatches(
            ByteWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r <= 0 },
            { _, x, r -> x < 0 && r < 0 },
        )
    }

    @Test
    fun equalityTest() {
        checkExecutionMatches(
            ByteWrapper::equality,
            eq(2),
            { _, a, b, result -> a == b && result == 1 },
            { _, a, b, result -> a != b && result == 4 }, // method under test has unreachable branches because of caching
        )
    }
}