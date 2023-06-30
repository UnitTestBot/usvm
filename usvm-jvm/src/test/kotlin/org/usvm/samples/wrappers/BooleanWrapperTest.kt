package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class BooleanWrapperTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
    fun primitiveToWrapperTest() {
        checkDiscoveredProperties(
            BooleanWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x && r == true },
            { _, x, r -> !x && r == true },
        )
    }

    @Test
    @Disabled("Can not set static final java.lang.Boolean field java.lang.Boolean.TRUE to null value")
    fun wrapperToPrimitiveTest() {
        checkDiscoveredProperties(
            BooleanWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x && r == true },
            { _, x, r -> !x && r == true },
        )
    }

    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
    fun equalityTest() {
        checkDiscoveredProperties(
            BooleanWrapper::equality,
            eq(2),
            { _, a, b, result -> a == b && result == 1 },
            { _, a, b, result -> a != b && result == 4 }, // method under test has unreachable branches because of caching
        )
    }
}