package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class SimpleNoConditionTest : JavaMethodTestRunner() {

    @Test
    fun testNoConditionAdd() {
        checkDiscoveredProperties(
            SimpleNoCondition::basicAdd,
            eq(1)
        )
    }

    @Test
    fun testNoConditionPow() {
        checkDiscoveredProperties(
            SimpleNoCondition::basicXorInt,
            eq(1)
        )
    }

    @Test
    fun testNoConditionPowBoolean() {
        checkDiscoveredProperties(
            SimpleNoCondition::basicXorBoolean,
            eq(1)
        )
    }
}