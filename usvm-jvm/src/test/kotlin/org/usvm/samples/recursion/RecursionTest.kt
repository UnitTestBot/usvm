package org.usvm.samples.recursion

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import org.usvm.util.isException


import kotlin.math.pow

internal class RecursionTest : JavaMethodTestRunner() {
    @UsvmTest([Options([PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM])])
    fun testFactorial(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredPropertiesWithExceptions(
                Recursion::factorial,
                eq(3),
                { _, x, r -> x < 0 && r.isException<IllegalArgumentException>() },
                { _, x, r -> x == 0 && r.getOrNull() == 1 },
                { _, x, r -> x > 0 && r.getOrNull() == (1..x).reduce { a, b -> a * b } }
            )
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.RANDOM_PATH])])
    fun testFib(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredPropertiesWithExceptions(
                Recursion::fib,
                eq(4),
                { _, x, r -> x < 0 && r.isException<IllegalArgumentException>() },
                { _, x, r -> x == 0 && r.getOrNull() == 0 },
                { _, x, r -> x == 1 && r.getOrNull() == 1 },
                { _, x, r -> x > 1 && r.getOrNull() == Recursion().fib(x) }
            )
        }
    }

    @Test
    @Disabled("Native method invocation: java.lang.Float.floatToRawIntBits")
    fun testSum() {
        checkDiscoveredProperties(
            Recursion::sum,
            eq(2),
            { _, x, y, r -> y == 0 && r == x },
            { _, x, y, r -> y != 0 && r == x + y }
        )

    }

    @UsvmTest([Options([PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM])])
    fun testPow(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredPropertiesWithExceptions(
                Recursion::pow,
                eq(4),
                { _, _, y, r -> y < 0 && r.isException<IllegalArgumentException>() },
                { _, _, y, r -> y == 0 && r.getOrNull() == 1 },
                { _, x, y, r -> y % 2 == 1 && r.getOrNull() == x.toDouble().pow(y.toDouble()).toInt() },
                { _, x, y, r -> y % 2 != 1 && r.getOrNull() == x.toDouble().pow(y.toDouble()).toInt() }
            )
        }
    }

    @Test
    @Disabled("Expected exactly 2 executions, but 54 found. Fix minimization")
    fun infiniteRecursionTest() {
        checkDiscoveredPropertiesWithExceptions(
            Recursion::infiniteRecursion,
            eq(2),
            { _, x, r -> x > 10000 && r.isException<StackOverflowError>() },
            { _, x, r -> x <= 10000 && r.isException<StackOverflowError>() },
        )
    }

    @Test
    @Disabled("Not implemented: string constant")
    fun vertexSumTest() {
        checkDiscoveredProperties(
            Recursion::vertexSum,
            between(2..3),
            { _, x, _ -> x <= 10 },
            { _, x, _ -> x > 10 }
        )
    }

    @Test
    fun recursionWithExceptionTest() {
        checkDiscoveredPropertiesWithExceptions(
            Recursion::recursionWithException,
            ge(3),
            { _, x, r -> x < 42 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x == 42 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x > 42 && r.isException<IllegalArgumentException>() },
        )
    }

    @UsvmTest([Options([PathSelectionStrategy.RANDOM_PATH])])
    fun recursionLoopTest(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredProperties(
                Recursion::firstMethod,
                eq(2),
                { _, x, _ -> x < 4 },
                { _, x, _ -> x >= 4 },
            )
        }
    }
}
