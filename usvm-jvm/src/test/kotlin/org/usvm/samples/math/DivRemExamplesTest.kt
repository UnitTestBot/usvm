package org.usvm.samplesmath

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.math.DivRemExamples
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

internal class DivRemExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testDiv() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::div,
            eq(2),
            { _, _, y, r -> y == 0 && r.isException<ArithmeticException>() },
            { _, x, y, r -> y != 0 && r.getOrNull() == x / y }
        )
    }

    @Test
    fun testRem() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::rem,
            eq(2),
            { _, _, y, r -> y == 0 && r.isException<ArithmeticException>() },
            { _, x, y, r -> y != 0 && r.getOrNull() == x % y }
        )
    }

    @Test
    fun testRemPositiveConditional() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::remPositiveConditional,
            eq(3),
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d != 0 && 11 % d == 2 && r.getOrNull() == true },
            { _, d, r -> d != 0 && 11 % d != 2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemNegativeConditional() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::remNegativeConditional,
            eq(3),
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d != 0 && -11 % d == -2 && r.getOrNull() == true },
            { _, d, r -> d != 0 && -11 % d != -2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemWithConditions() {
        checkWithExceptionExecutionMatches(
            DivRemExamples::remWithConditions,
            eq(4),
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
            eq(1)
        )
    }

    @Test
    fun testRemDoubleInt() {
        checkExecutionMatches(
            DivRemExamples::remDoubleInt,
            eq(1)
        )
    }
}