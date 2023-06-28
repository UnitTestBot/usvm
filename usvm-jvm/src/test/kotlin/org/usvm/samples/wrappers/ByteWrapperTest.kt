package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ByteWrapperTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
    fun primitiveToWrapperTest() {
        checkDiscoveredProperties(
            ByteWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x >= 0 && r != null && r <= 0 },
            { _, x, r -> x < 0 && r != null && r < 0 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkDiscoveredProperties(
            ByteWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x >= 0 && r != null && r <= 0 },
            { _, x, r -> x < 0 && r != null && r < 0 },
        )
    }

    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
    fun equalityTest() {
        checkDiscoveredProperties(
            ByteWrapper::equality,
            eq(2),
            { _, a, b, result -> a == b && result == 1 },
            { _, a, b, result -> a != b && result == 4 }, // method under test has unreachable branches because of caching
        )
    }
}