package org.usvm.samples.wrappers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


// TODO failed Kotlin compilation
internal class CharacterWrapperTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Expected exactly 2 executions, but 1 found")
    fun primitiveToWrapperTest() {
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

    @Disabled("Caching char values between -128 and 127 isn't supported JIRA:1481")
    @Test
    fun equalityTest() {
        checkDiscoveredProperties(
            CharacterWrapper::equality,
            eq(3),
            { _, a, b, result -> a == b && a.code <= 127 && result == 1 },
            { _, a, b, result -> a == b && a.code > 127 && result == 2 },
            { _, a, b, result -> a != b && result == 4 },
        )
    }
}