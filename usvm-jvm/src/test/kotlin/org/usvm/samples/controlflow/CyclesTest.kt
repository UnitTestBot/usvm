package org.usvm.samples.controlflow

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class CyclesTest : JavaMethodTestRunner() {
    @Test
    fun testForCycle() {
        checkExecutionMatches(
            Cycles::forCycle,
            eq(3),
            { _, x, r -> x <= 0 && r == -1 },
            { _, x, r -> x in 1..5 && r == -1 },
            { _, x, r -> x > 5 && r == 1 }
        )
    }

    @Test
    fun testForCycleFour() {
        checkExecutionMatches(
            Cycles::forCycleFour,
            eq(3),
            { _, x, r -> x <= 0 && r == -1 },
            { _, x, r -> x in 1..4 && r == -1 },
            { _, x, r -> x > 4 && r == 1 }
        )
    }

    @Test
    fun testForCycleJayHorn() {
        checkExecutionMatches(
            Cycles::forCycleFromJayHorn,
            eq(2),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x > 0 && r == 2 * x }
        )
    }

    @Test
    fun testFiniteCycle() {
        checkExecutionMatches(
            Cycles::finiteCycle,
            eq(2),
            { _, x, r -> x % 519 == 0 && r % 519 == 0 },
            { _, x, r -> x % 519 != 0 && r % 519 == 0 }
        )
    }

    @Test
    fun testWhileCycle() {
        checkExecutionMatches(
            Cycles::whileCycle,
            eq(2),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x > 0 && r == (0 until x).sum() }
        )
    }

    @Test
    fun testCallInnerWhile() {
        checkExecutionMatches(
            Cycles::callInnerWhile,
            between(1..2),
            { _, x, r -> x >= 42 && r == Cycles().callInnerWhile(x) }
        )
    }

    @Test
    fun testInnerLoop() {
        checkExecutionMatches(
            Cycles::innerLoop,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x in 1..3 && r == 0 },
            { _, x, r -> x == 4 && r == 1 },
            { _, x, r -> x >= 5 && r == 0 }
        )
    }

    @Test
    fun testDivideByZeroCheckWithCycles() {
        checkWithExceptionExecutionMatches(
            Cycles::divideByZeroCheckWithCycles,
            eq(3),
            { _, n, _, r -> n < 5 && r.isException<IllegalArgumentException>() },
            { _, n, x, r -> n >= 5 && x == 0 && r.isException<ArithmeticException>() },
            { _, n, x, r -> n >= 5 && x != 0 && r.getOrNull() == Cycles().divideByZeroCheckWithCycles(n, x) }
        )
    }

    @Test
    fun moveToExceptionTest() {
        checkWithExceptionExecutionMatches(
            Cycles::moveToException,
            eq(3),
            { _, x, r -> x < 400 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x > 400 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x == 400 && r.isException<IllegalArgumentException>() },
        )
    }
}