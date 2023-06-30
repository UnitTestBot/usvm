package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

@Disabled("What are these tests about? We should rewrite matchers")
internal class ModelMinimizationExamplesTest : JavaMethodTestRunner() {
    @Test
    fun singleValueComparisonTest() {
        checkDiscoveredProperties(
            ModelMinimizationExamples::singleValueComparison,
            eq(4),
            { _, quad, _ -> quad == null }, // NPE
            { _, quad, _ -> quad.a == null }, // NPE
            { _, quad, r -> quad.a.value == 0 && r == true },
            { _, quad, r -> quad.a.value != 0 && r == false }
        )
    }

    @Test
    fun singleValueComparisonNotNullTest() {
        checkDiscoveredProperties(
            ModelMinimizationExamples::singleValueComparisonNotNull,
            eq(2),
            { _, quad, r -> quad.a.value == 0 && r == true },
            { _, quad, r -> quad.a.value != 0 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun conditionCheckANeTest() {
        // Parameters `a` and `b` should be not null.
        // Parameters `a` and `b` should be distinct instances.
        // The field `a.value` is used and should be initialized.
        // The field `b.value` is not used and should not be initialized to avoid redundancy.
        checkDiscoveredProperties(
            ModelMinimizationExamples::conditionCheckANe,
            eq(3),
            { _, a, _, r -> a.value == 42 && r == true },
            { _, a, _, r -> a.value <= 0 && r == true },
            { _, a, _, r -> a.value > 0 && a.value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    @Disabled("Sort mismatch")
    fun conditionCheckAEqTest() {
        // Parameters `a` and `b` should be not null.
        // Parameters `a` and `b` should refer to the same instance.
        // The field `a.value` is used and should be initialized.
        // The field `b.value` is not used but should be implicitly initialized, as `b` is `a` restored from cache.
        checkDiscoveredProperties(
            ModelMinimizationExamples::conditionCheckAEq,
            eq(3),
            { _, a, _, r -> a.value == 42 && r == true },
            { _, a, _, r -> a.value <= 0 && r == true },
            { _, a, _, r -> a.value > 0 && a.value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun conditionCheckBNeTest() {
        // Parameters `a` and `b` should be not null.
        // Parameters `a` and `b` should be distinct instances.
        // The field `a.value` is not used and should not be initialized to avoid redundancy.
        // The field `b.value` is used and should be initialized.
        checkDiscoveredProperties(
            ModelMinimizationExamples::conditionCheckBNe,
            eq(3),
            { _, _, b, r -> b.value == 42 && r == true },
            { _, _, b, r -> b.value <= 0 && r == true },
            { _, _, b, r -> b.value > 0 && b.value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun conditionCheckBEqTest() {
        // Parameters `a` and `b` should be not null.
        // Parameters `a` and `b` should refer to the same instance.
        // The field `a.value` is not used but should be initialized, as `b.value` is used, and `a === b`.
        // The field `b.value` is used and should be initialized.
        // `a` should be initialized even if its model is created first and stored in the cache.
        // Note: `a` and `b` might have different `addr` but they will have the same `concreteAddr`.
        checkDiscoveredProperties(
            ModelMinimizationExamples::conditionCheckBEq,
            eq(3),
            { _, _, b, r -> b.value == 42 && r == true },
            { _, _, b, r -> b.value <= 0 && r == true },
            { _, _, b, r -> b.value > 0 && b.value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun conditionCheckNoNullabilityConstraintTest() {
        // Note: in this test we have no constraints on the second argument, so it becomes `null`.
        checkDiscoveredProperties(
            ModelMinimizationExamples::conditionCheckNoNullabilityConstraintExample,
            eq(4),
            { _, a, _, _ -> a == null }, // NPE
            { _, a, _, r -> a.value == 42 && r == true },
            { _, a, _, r -> a.value <= 0 && r == true },
            { _, a, _, r -> a.value > 0 && a.value != 42 && r == false }
        )
    }

    @Test
    fun firstArrayElementContainsSentinelTest() {
        checkDiscoveredProperties(
            ModelMinimizationExamples::firstArrayElementContainsSentinel,
            eq(2),
            { _, values, r -> values[0].value == 42 && r == true },
            { _, values, r -> values[0].value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun multipleConstraintsTest() {
        checkDiscoveredProperties(
            ModelMinimizationExamples::multipleConstraintsExample,
            eq(3),
            { _, a, _, _, r -> a.value == 42 && r == 1 },
            { _, a, b, _, r -> a.value != 42 && b.value == 73 && r == 2 },
            { _, a, b, _, r -> a.value != 42 && b.value != 73 && r == 3 }, // TODO: JIRA:1688
        )
    }
}