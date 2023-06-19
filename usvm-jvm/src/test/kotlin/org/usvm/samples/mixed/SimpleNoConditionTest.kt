package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimpleNoConditionTest : JavaMethodTestRunner() {

    @Test
    fun testNoConditionAdd() {
        checkExecutionMatches(
            SimpleNoCondition::basicAdd,
        )
    }

    @Test
    fun testNoConditionPow() {
        checkExecutionMatches(
            SimpleNoCondition::basicXorInt,
        )
    }

    @Test
    fun testNoConditionPowBoolean() {
        checkExecutionMatches(
            SimpleNoCondition::basicXorBoolean,
        )
    }
}