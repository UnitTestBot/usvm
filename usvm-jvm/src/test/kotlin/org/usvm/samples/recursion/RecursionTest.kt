package org.usvm.samples.recursion


import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import org.usvm.util.isException
import kotlin.math.pow

internal class RecursionTest : ApproximationsTestRunner() {

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

    @UsvmTest([Options([PathSelectionStrategy.FORK_DEPTH])])
    fun testSum(options: UMachineOptions) = withOptions(options) {
        checkDiscoveredProperties(
            Recursion::sum,
            eq(2),
            { _, x, y, r -> y == 0 && r == x },
            { _, x, y, r -> y != 0 && r == x + y }
        )
    }

    @UsvmTest([Options([PathSelectionStrategy.BFS], coverageZone = CoverageZone.TRANSITIVE)])
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
    fun vertexSumTest() {
        val options = options.copy(stepsFromLastCovered = 4500L)
        withOptions(options) {
            checkDiscoveredProperties(
                Recursion::vertexSum,
                between(2..3),
                { _, x, _ -> x <= 10 },
                { _, x, _ -> x > 10 }
            )
        }
    }

    @Test
    fun recursionWithExceptionTest() {
        // Two goto statements are expected to not be covered
        // The expected coverage is 15 out of 17 instructions
        val options = options.copy(stopOnCoverage = 88)
        withOptions(options) {
            checkDiscoveredPropertiesWithExceptions(
                Recursion::recursionWithException,
                ge(3),
                { _, x, r -> x < 42 && r.isException<IllegalArgumentException>() },
                { _, x, r -> x == 42 && r.isException<IllegalArgumentException>() },
                { _, x, r -> x > 42 && r.isException<IllegalArgumentException>() },
            )
        }
    }

    @UsvmTest([Options([PathSelectionStrategy.RANDOM_PATH])])
    fun recursionLoopTest(options: UMachineOptions) {
        withOptions(options) {
            checkDiscoveredProperties(
                Recursion::firstMethod,
                ignoreNumberOfAnalysisResults,
            )
        }
    }
}
