package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class IntegerWrapperTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
    fun primitiveToWrapperTest() {
        checkDiscoveredProperties(
            IntegerWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x >= 0 && r != null && r <= 0 },
            { _, x, r -> x < 0 && r != null && r < 0 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkDiscoveredProperties(
            IntegerWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r != null && r <= 0 },
            { _, x, r -> x < 0 && r != null && r < 0 },
        )
    }

    @Test
    @Disabled("Not implemented: ushr")
    fun numberOfZerosTest() {
        checkDiscoveredProperties(
            IntegerWrapper::numberOfZeros,
            eq(4),
            { _, x, _ -> x == null },
            { _, x, r -> Integer.numberOfLeadingZeros(x) >= 5 && r == 0 },
            { _, x, r -> Integer.numberOfLeadingZeros(x) < 5 && Integer.numberOfTrailingZeros(x) >= 5 && r == 0 },
            { _, x, r -> Integer.numberOfLeadingZeros(x) < 5 && Integer.numberOfTrailingZeros(x) < 5 && r == 1 },
        )
    }

    @Test
    @Disabled("Not implemented: ushr")
    fun bitCountTest() {
        checkDiscoveredProperties(
            IntegerWrapper::bitCount,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> Integer.bitCount(x) != 5 && r == 0 },
            { _, x, r -> Integer.bitCount(x) == 5 && r == 1 },
        )
    }


    @Disabled("Caching integer values between -128 and 127 isn't supported JIRA:1481")
    @Test
    fun equalityTest() {
        checkDiscoveredProperties(
            IntegerWrapper::equality,
            eq(3),
            { _, a, b, result -> a == b && a >= -128 && a <= 127 && result == 1 },
            { _, a, b, result -> a == b && (a < -128 || a > 127) && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }

}