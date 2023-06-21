package org.usvm.samples.controlflow

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class CycleDependedConditionTest : JavaMethodTestRunner() {
    @Test
    fun testCycleDependedOneCondition() {
        checkDiscoveredProperties(
            CycleDependedCondition::oneCondition,
            eq(3),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x in 1..2 && r == 0 },
            { _, x, r -> x > 2 && r == 1 }
        )
    }

    @Test
    fun testCycleDependedTwoCondition() {
        checkDiscoveredProperties(
            CycleDependedCondition::twoCondition,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x == 4 && r == 1 },
            { _, x, r -> (x in 1..3 || x >= 5) && r == 0 }
        )
    }


    @Test
    fun testCycleDependedThreeCondition() {
        checkDiscoveredProperties(
            CycleDependedCondition::threeCondition,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x == 6 || x > 8 && r == 1 },
            { _, x, r -> (x in 1..5 || x == 7) && r == 0 }
        )
    }


    @Test
    fun testCycleDependedOneConditionHigherNumber() {
        checkDiscoveredProperties(
            CycleDependedCondition::oneConditionHigherNumber,
            eq(3),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x in 1..100 && r == 0 },
            { _, x, r -> x > 100 && r == 1 && r == 1 }
        )
    }
}