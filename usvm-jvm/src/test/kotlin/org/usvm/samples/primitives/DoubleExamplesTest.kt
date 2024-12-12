package org.usvm.samples.primitives

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


@Suppress("SimplifyNegatedBinaryExpression")
internal class DoubleExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testCompareSum() {
        checkDiscoveredProperties(
            DoubleExamples::compareSum,
            eq(2),
            { _, a, b, r -> a + b > 5.6 && r == 1.0 },
            { _, a, b, r -> (a + b).isNaN() || a + b <= 5.6 && r == 0.0 }
        )
    }

    @Test
    fun testCompare() {
        checkDiscoveredProperties(
            DoubleExamples::compare,
            eq(2),
            { _, a, b, r -> a > b && r == 1.0 },
            { _, a, b, r -> !(a > b) && r == 0.0 }
        )
    }

    @Test
    fun testCompareWithDiv() {
        checkDiscoveredProperties(
            DoubleExamples::compareWithDiv,
            eq(2), // only two branches because division by zero is not an error with doubles
            { _, a, b, r -> a / (a + 0.5) > b && r == 1.0 },
            { _, a, b, r -> !(a / (a + 0.5) > b) && r == 0.0 }
        )
    }

    @Test
    fun testSimpleSum() {
        checkDiscoveredProperties(
            DoubleExamples::simpleSum,
            ignoreNumberOfAnalysisResults,
            { _, a, b, r -> (a + b).isNaN() && r == 0.0 },
            { _, a, b, r -> a + 1.1 + b > 10.1 && a + 1.1 + b < 11.125 && r == 1.1 },
            { _, a, b, r -> (a + 1.1 + b >= 11.125) || (a + 1.1 + b <= 10.1) && r == 1.2 }
        )
    }

    @Test
    fun testSum() {
        checkDiscoveredProperties(
            DoubleExamples::sum,
            ignoreNumberOfAnalysisResults,
            { _, a, b, r -> (a + b).isNaN() && r == 0.0 },
            { _, a, b, r -> a + 0.123124 + b > 11.123124 && a + b + 0.123124 < 11.125 && r == 1.1 },
            { _, a, b, r -> (a + 0.123124 + b <= 11.123124) || (a + 0.123124 + b >= 11.125) && r == 1.2 },
        )
    }

    @Test
    fun testSimpleMul() {
        withOptions(options.copy(solverType = SolverType.YICES)) {
            checkDiscoveredProperties(
                DoubleExamples::simpleMul,
                ignoreNumberOfAnalysisResults,
                { _, a, b, r -> (a * b).isNaN() && r == 0.0 },
                { _, a, b, r -> a * b > 33.1 && a * b < 33.875 && r == 1.1 },
                { _, a, b, r -> a * b >= 33.875 || a * b <= 33.1 && r == 1.2 }
            )
        }
    }

    @Test
    fun testMul() {
        withOptions(options.copy(
            solverType = SolverType.YICES,
            // collect all states without coverage limit
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = -1
        )) {
            checkDiscoveredProperties(
                DoubleExamples::mul,
                eq(6),
                { _, a, b, r -> (a * b).isNaN() && r == 0.0 }, // 0 * inf || a == nan || b == nan
                { _, a, b, r -> !(a * b > 33.32) && !(a * b > 33.333) && r == 1.3 }, // 1.3, 1-1 false, 2-1 false
                { _, a, b, r -> a * b == 33.333 && r == 1.3 }, // 1.3, 1-1 true, 1-2 false, 2-1 false
                { _, a, b, r -> a * b > 33.32 && a * b < 33.333 && r == 1.1 }, // 1.1, 1st true
                { _, a, b, r -> a * b > 33.333 && a * b < 33.7592 && r == 1.2 }, // 1.2, 1st false, 2nd true
                { _, a, b, r -> a * b >= 33.7592 && r == 1.3 } // 1.3, 1-1 false, 2-1 true, 2-2 false
            )
        }
    }

    @Test
    fun testCheckNonInteger() {
        checkDiscoveredProperties(
            DoubleExamples::checkNonInteger,
            ignoreNumberOfAnalysisResults,
            { _, a, r -> !(a > 0.1 && a < 0.9) && r == 0.0 },
            { _, a, r -> a > 0.1 && a < 0.9 && r == 1.0 }
        )
    }

    @Test
    fun testDiv() {
        checkDiscoveredProperties(
            DoubleExamples::div,
            eq(1),
            { _, a, b, c, r -> r == (a + b) / c || (r != null && r.isNaN() && ((a + b) / c).isNaN()) }
        )
    }

    @Test
    fun testSimpleEquation() {
        checkDiscoveredProperties(
            DoubleExamples::simpleEquation,
            eq(2),
            { _, x, r -> x + x + x - 9 == x + 3 && r == 0 },
            { _, x, r -> x + x + x - 9 != x + 3 && r == 1 }
        )
    }

    @Test
    fun testSimpleNonLinearEquation() {
        withOptions(options.copy(solverType = SolverType.YICES)) {
            checkDiscoveredProperties(
                DoubleExamples::simpleNonLinearEquation,
                eq(2),
                { _, x, r -> 3 * x - 9 == x + 3 && r == 0 },
                { _, x, r -> 3 * x - 9 != x + 3 && r == 1 }
            )
        }
    }

    @Test
    fun testCheckNaN() {
        checkDiscoveredProperties(
            DoubleExamples::checkNaN,
            eq(4),
            { _, d, r -> !d.isNaN() && d < 0 && r == -1 },
            { _, d, r -> !d.isNaN() && d == 0.0 && r == 0 },
            { _, d, r -> !d.isNaN() && d > 0 && r == 1 },
            { _, d, r -> d.isNaN() && r == 100 }
        )
    }

    @Test
    fun testUnaryMinus() {
        checkDiscoveredProperties(
            DoubleExamples::unaryMinus,
            eq(2),
            { _, d, r -> !d.isNaN() && -d < 0 && r == -1 },
            { _, d, r -> d.isNaN() || -d >= 0 && r == 0 }
        )
    }

    @Test
    fun testDoubleInfinity() {
        checkDiscoveredProperties(
            DoubleExamples::doubleInfinity,
            eq(3),
            { _, d, r -> d == Double.POSITIVE_INFINITY && r == 1 },
            { _, d, r -> d == Double.NEGATIVE_INFINITY && r == 2 },
            { _, d, r -> !d.isInfinite() && r == 3 },
        )
    }
}
