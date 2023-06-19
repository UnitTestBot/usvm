package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ModelMinimizationExamplesTest : JavaMethodTestRunner() {
    @Test
    fun singleValueComparisonTest() {
        checkExecutionMatches(
            ModelMinimizationExamples::singleValueComparison,
            { _, quad, _ -> quad == null }, // NPE
            { _, quad, _ -> quad.a == null }, // NPE
            { _, quad, r -> quad.a.value == 0 && r == true },
            { _, quad, r -> quad.a.value != 0 && r == false }
        )
    }

    @Test
    fun singleValueComparisonNotNullTest() {
        checkExecutionMatches(
            ModelMinimizationExamples::singleValueComparisonNotNull,
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
        checkExecutionMatches(
            ModelMinimizationExamples::conditionCheckANe,
            { _, a, _, r -> a.value == 42 && r == true },
            { _, a, _, r -> a.value <= 0 && r == true },
            { _, a, _, r -> a.value > 0 && a.value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun conditionCheckAEqTest() {
        // Parameters `a` and `b` should be not null.
        // Parameters `a` and `b` should refer to the same instance.
        // The field `a.value` is used and should be initialized.
        // The field `b.value` is not used but should be implicitly initialized, as `b` is `a` restored from cache.
        checkExecutionMatches(
            ModelMinimizationExamples::conditionCheckAEq,
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
        checkExecutionMatches(
            ModelMinimizationExamples::conditionCheckBNe,
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
        checkExecutionMatches(
            ModelMinimizationExamples::conditionCheckBEq,
            { _, _, b, r -> b.value == 42 && r == true },
            { _, _, b, r -> b.value <= 0 && r == true },
            { _, _, b, r -> b.value > 0 && b.value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun conditionCheckNoNullabilityConstraintTest() {
        // Note: in this test we have no constraints on the second argument, so it becomes `null`.
        checkExecutionMatches(
            ModelMinimizationExamples::conditionCheckNoNullabilityConstraintExample,
            { _, a, _, _ -> a == null }, // NPE
            { _, a, _, r -> a.value == 42 && r == true },
            { _, a, _, r -> a.value <= 0 && r == true },
            { _, a, _, r -> a.value > 0 && a.value != 42 && r == false }
        )
    }

    @Test
    fun firstArrayElementContainsSentinelTest() {
        checkExecutionMatches(
            ModelMinimizationExamples::firstArrayElementContainsSentinel,
            { _, values, r -> values[0].value == 42 && r == true },
            { _, values, r -> values[0].value != 42 && r == false }, // TODO: JIRA:1688
        )
    }

    @Test
    fun multipleConstraintsTest() {
        checkExecutionMatches(
            ModelMinimizationExamples::multipleConstraintsExample,
            { _, a, _, _, r -> a.value == 42 && r == 1 },
            { _, a, b, _, r -> a.value != 42 && b.value == 73 && r == 2 },
            { _, a, b, _, r -> a.value != 42 && b.value != 73 && r == 3 }, // TODO: JIRA:1688
        )
    }
}