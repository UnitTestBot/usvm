package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class LongWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() {
        checkExecutionMatches(
            LongWrapper::primitiveToWrapper,
            { _, x, r -> x >= 0 && r!! <= 0 },
            { _, x, r -> x < 0 && r!! < 0 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkExecutionMatches(
            LongWrapper::wrapperToPrimitive,
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r <= 0 },
            { _, x, r -> x < 0 && r < 0 },
        )
    }

    @Disabled("Caching long values between -128 and 127 doesn't work JIRA:1481")
    @Test
    fun equalityTest() {
        checkExecutionMatches(
            LongWrapper::equality,
            { _, a, b, result -> a == b && a >= -128 && a <= 127 && result == 1 },
            { _, a, b, result -> a == b && (a < -128 || a > 127) && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }

    @Test
    fun parseLong() {
        checkExecutionMatches(
            LongWrapper::parseLong,
            { _, line, _ -> line == null },
            { _, line, _ -> line.isEmpty() },
            { _, line, _ -> line == "-" },
            { _, line, _ -> line == "+" },
            { _, line, _ -> line.startsWith("-") },
            { _, line, _ -> !line.startsWith("-") },
        )
    }
}