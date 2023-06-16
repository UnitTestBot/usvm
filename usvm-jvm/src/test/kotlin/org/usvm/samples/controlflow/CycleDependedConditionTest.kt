package org.usvm.samples.controlflow

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class CycleDependedConditionTest : JavaMethodTestRunner() {
    @Test
    fun testCycleDependedOneCondition() {
        checkExecutionMatches(
            CycleDependedCondition::oneCondition,
            eq(3),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x in 1..2 && r == 0 },
            { _, x, r -> x > 2 && r == 1 }
        )
    }

    @Test
    fun testCycleDependedTwoCondition() {
        checkExecutionMatches(
            CycleDependedCondition::twoCondition,
            eq(4),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x in 1..3 && r == 0 },
            { _, x, r -> x == 4 && r == 1 },
            { _, x, r -> x >= 5 && r == 0 }
        )
    }


    @Test
    fun testCycleDependedThreeCondition() {
        checkExecutionMatches(
            CycleDependedCondition::threeCondition,
            eq(4),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x in 1..5 && r == 0 },
            { _, x, r -> x == 6 || x > 8 && r == 1 },
            { _, x, r -> x == 7 && r == 0 }
        )
    }


    @Test
    fun testCycleDependedOneConditionHigherNumber() {
        checkExecutionMatches(
            CycleDependedCondition::oneConditionHigherNumber,
            eq(3),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x in 1..100 && r == 0 },
            { _, x, r -> x > 100 && r == 1 && r == 1 }
        )
    }
}