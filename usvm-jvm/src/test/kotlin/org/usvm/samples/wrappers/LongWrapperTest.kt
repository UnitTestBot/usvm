package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


internal class LongWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x >= 0 && r != null && r <= 0 },
            { _, x, r -> x < 0 && r != null && r < 0 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkDiscoveredProperties(
            LongWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r != null && r <= 0 },
            { _, x, r -> x < 0 && r != null && r < 0 },
        )
    }

    @Test
    fun equalityTest() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredProperties(
            LongWrapper::equality,
            eq(3),
            { _, a, b, result -> a == b && a >= -128 && a <= 127 && result == 1 },
            { _, a, b, result -> a == b && (a < -128 || a > 127) && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }

    @Test
    fun parseLong() = disableTest("Some properties were not discovered at positions (from 0): [3]") {
        checkDiscoveredProperties(
            LongWrapper::parseLong,
            eq(6),
            { _, line, _ -> line == null },
            { _, line, _ -> line.isEmpty() },
            { _, line, _ -> line == "-" },
            { _, line, _ -> line == "+" },
            { _, line, _ -> line.startsWith("-") },
            { _, line, _ -> !line.startsWith("-") },
        )
    }
}