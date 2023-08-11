package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


// TODO failed Kotlin compilation
internal class CharacterWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() = disableTest("Expected exactly 2 executions, but 1 found") {
        checkDiscoveredProperties(
            CharacterWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x.code >= 100 && r != null && r.code >= 100 },
            { _, x, r -> x.code < 100 && r != null && r.code == x.code + 100 },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkDiscoveredProperties(
            CharacterWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x.code >= 100 && r != null && r.code >= 100 },
            { _, x, r -> x.code < 100 && r != null && r.code == x.code + 100 },
        )
    }

    @Test
    fun equalityTest() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredProperties(
            CharacterWrapper::equality,
            eq(3),
            { _, a, b, result -> a == b && a.code <= 127 && result == 1 },
            { _, a, b, result -> a == b && a.code > 127 && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }
}