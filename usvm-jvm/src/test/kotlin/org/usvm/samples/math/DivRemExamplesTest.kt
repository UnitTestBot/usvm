package org.usvm.samples.math

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.util.isException

internal class DivRemExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testDiv() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::div,
            { _, _, y, r -> y == 0 && r.isException<ArithmeticException>() },
            { _, x, y, r -> y != 0 && r.getOrNull() == x / y }
        )
    }

    @Test
    fun testRem() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::rem,
            { _, _, y, r -> y == 0 && r.isException<ArithmeticException>() },
            { _, x, y, r -> y != 0 && r.getOrNull() == x % y }
        )
    }

    @Test
    fun testRemPositiveConditional() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::remPositiveConditional,
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d != 0 && 11 % d == 2 && r.getOrNull() == true },
            { _, d, r -> d != 0 && 11 % d != 2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemNegativeConditional() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::remNegativeConditional,
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d != 0 && -11 % d == -2 && r.getOrNull() == true },
            { _, d, r -> d != 0 && -11 % d != -2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemWithConditions() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::remWithConditions,
            { _, d, r -> d < 0 && r.getOrNull() == false },
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d > 0 && -11 % d == -2 && r.getOrNull() == true },
            { _, d, r -> d > 0 && -11 % d != -2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemDoubles() {
        checkExecutionMatches(
            DivRemExamples::remDoubles,
        )
    }

    @Test
    fun testRemDoubleInt() {
        checkExecutionMatches(
            DivRemExamples::remDoubleInt,
        )
    }
}