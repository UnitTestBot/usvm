package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimpleNoConditionTest : JavaMethodTestRunner() {

    @Test
    fun testNoConditionAdd() {
        checkExecutionMatches(
            SimpleNoCondition::basicAdd,
            eq(1)
        )
    }

    @Test
    fun testNoConditionPow() {
        checkExecutionMatches(
            SimpleNoCondition::basicXorInt,
            eq(1)
        )
    }

    @Test
    fun testNoConditionPowBoolean() {
        checkExecutionMatches(
            SimpleNoCondition::basicXorBoolean,
            eq(1)
        )
    }
}