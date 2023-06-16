package org.usvm.samples.wrappers

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class BooleanWrapperTest : JavaMethodTestRunner() {
    @Test
    fun primitiveToWrapperTest() {
        checkExecutionMatches(
            BooleanWrapper::primitiveToWrapper,
            eq(2),
            { _, x, r -> x && r == true },
            { _, x, r -> !x && r == true },
        )
    }

    @Test
    fun wrapperToPrimitiveTest() {
        checkExecutionMatches(
            BooleanWrapper::wrapperToPrimitive,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x && r == true },
            { _, x, r -> !x && r == true },
        )
    }

    @Test
    fun equalityTest() {
        checkExecutionMatches(
            BooleanWrapper::equality,
            eq(2),
            { _, a, b, result -> a == b && result == 1 },
            { _, a, b, result -> a != b && result == 4 }, // method under test has unreachable branches because of caching
        )
    }
}