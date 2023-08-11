package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest


internal class IntegerWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
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
    fun numberOfZerosTest() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            IntegerWrapper::numberOfZeros,
            ignoreNumberOfAnalysisResults,
            { _, x, _ -> x == null },
            { _, x, r -> Integer.numberOfLeadingZeros(x) >= 5 || Integer.numberOfTrailingZeros(x) >= 5 && r == 0 },
            { _, x, r -> Integer.numberOfLeadingZeros(x) < 5 && Integer.numberOfTrailingZeros(x) < 5 && r == 1 },
        )
    }

    @Test
    fun bitCountTest() {
        checkDiscoveredProperties(
            IntegerWrapper::bitCount,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> Integer.bitCount(x) != 5 && r == 0 },
            { _, x, r -> Integer.bitCount(x) == 5 && r == 1 },
        )
    }


    @Test
    fun equalityTest() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            IntegerWrapper::equality,
            eq(3),
            { _, a, b, result -> a == b && a >= -128 && a <= 127 && result == 1 },
            { _, a, b, result -> a == b && (a < -128 || a > 127) && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }

}