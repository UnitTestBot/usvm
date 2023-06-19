package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ShortWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() {
        checkExecutionMatches(
            ShortWrapper::primitiveToWrapper,
            { _, x, r -> x >= 0 && r!! <= 0 },
            { _, x, r -> x < 0 && r!! < 0 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkExecutionMatches(
            ShortWrapper::wrapperToPrimitive,
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r <= 0 },
            { _, x, r -> x < 0 && r < 0 },
        )
    }

    @Disabled("Caching short values between -128 and 127 isn't supported JIRA:1481")
    @Test
    fun equalityTest() {
        checkExecutionMatches(
            ShortWrapper::equality,
            { _, a, b, result -> a == b && a >= -128 && a <= 127 && result == 1 },
            { _, a, b, result -> a == b && (a < -128 || a > 127) && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }
}