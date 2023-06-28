package org.usvm.samples.controlflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class CyclesTest : JavaMethodTestRunner() {
    @Test
    fun testForCycle() {
        checkDiscoveredProperties(
            Cycles::forCycle,
            eq(3),
            { _, x, r -> x <= 0 && r == -1 },
            { _, x, r -> x in 1..5 && r == -1 },
            { _, x, r -> x > 5 && r == 1 }
        )
    }

    @Test
    fun testForCycleFour() {
        checkDiscoveredProperties(
            Cycles::forCycleFour,
            eq(3),
            { _, x, r -> x <= 0 && r == -1 },
            { _, x, r -> x in 1..4 && r == -1 },
            { _, x, r -> x > 4 && r == 1 }
        )
    }

    @Test
    fun testForCycleJayHorn() {
        checkDiscoveredProperties(
            Cycles::forCycleFromJayHorn,
            eq(2),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x > 0 && r == 2 * x }
        )
    }

    @Test
//    @Disabled("Expected exactly 2 executions, but 1 found")
    fun testFiniteCycle() {
        checkDiscoveredProperties(
            Cycles::finiteCycle,
            eq(2),
            { _, x, r -> x % 519 == 0 && r != null && r % 519 == 0 },
            { _, x, r -> x % 519 != 0 && r != null && r % 519 == 0 }
        )
    }

    @Test
    fun testWhileCycle() {
        checkDiscoveredProperties(
            Cycles::whileCycle,
            eq(2),
            { _, x, r -> x <= 0 && r == 0 },
            { _, x, r -> x > 0 && r == (0 until x).sum() }
        )
    }

    @Test
//    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testCallInnerWhile() {
        checkDiscoveredProperties(
            Cycles::callInnerWhile,
            between(1..2),
            { _, x, r -> x >= 42 && r == Cycles().callInnerWhile(x) }
        )
    }

    @Test
    // fixme: according to the coverage strategy, [0] and [2] are equivalent
    @Disabled("Some properties were not discovered at positions (from 0): [2]")
    fun testInnerLoop() {
        checkDiscoveredProperties(
            Cycles::innerLoop,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x in 1..3 && r == 0 },
            { _, x, r -> x == 4 && r == 1 },
            { _, x, r -> x >= 5 && r == 0 }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testDivideByZeroCheckWithCycles() {
        checkDiscoveredPropertiesWithExceptions(
            Cycles::divideByZeroCheckWithCycles,
            eq(3),
            { _, n, _, r -> n < 5 && r.isException<IllegalArgumentException>() },
            { _, n, x, r -> n >= 5 && x == 0 && r.isException<ArithmeticException>() },
            { _, n, x, r -> n >= 5 && x != 0 && r.getOrNull() == Cycles().divideByZeroCheckWithCycles(n, x) }
        )
    }

    @Test
    @Disabled("Expected exactly 3 executions, but 32 found. Same exception discovered multiple times")
    fun moveToExceptionTest() {
        checkDiscoveredPropertiesWithExceptions(
            Cycles::moveToException,
            eq(3),
            { _, x, r -> x < 400 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x > 400 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x == 400 && r.isException<IllegalArgumentException>() },
        )
    }
}