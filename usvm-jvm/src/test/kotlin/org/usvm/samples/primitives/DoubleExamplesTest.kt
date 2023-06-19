package org.usvm.samples.primitives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SimplifyNegatedBinaryExpression")
internal class DoubleExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testCompareSum() {
        checkExecutionMatches(
            DoubleExamples::compareSum,
            { _, a, b, r -> a + b > 5.6 && r == 1.0 },
            { _, a, b, r -> (a + b).isNaN() || a + b <= 5.6 && r == 0.0 }
        )
    }

    @Test
    fun testCompare() {
        checkExecutionMatches(
            DoubleExamples::compare,
            { _, a, b, r -> a > b && r == 1.0 },
            { _, a, b, r -> !(a > b) && r == 0.0 }
        )
    }

    @Test
    fun testCompareWithDiv() {
        checkExecutionMatches(
            DoubleExamples::compareWithDiv, // only two branches because division by zero is not an error with doubles
            { _, a, b, r -> a / (a + 0.5) > b && r == 1.0 },
            { _, a, b, r -> !(a / (a + 0.5) > b) && r == 0.0 }
        )
    }

    @Test
    fun testSimpleSum() {
        checkExecutionMatches(
            DoubleExamples::simpleSum,
            { _, a, b, r -> (a + b).isNaN() && r == 0.0 },
            { _, a, b, r -> a + 1.1 + b > 10.1 && a + 1.1 + b < 11.125 && r == 1.1 },
            { _, a, b, r -> a + 1.1 + b <= 10.1 && r == 1.2 },
            { _, a, b, r -> a + 1.1 + b >= 11.125 && r == 1.2 }
        )
    }

    @Test
    fun testSum() {
        checkExecutionMatches(
            DoubleExamples::sum,
            { _, a, b, r -> (a + b).isNaN() && r == 0.0 },
            { _, a, b, r -> a + 0.123124 + b > 11.123124 && a + b + 0.123124 < 11.125 && r == 1.1 },
            { _, a, b, r -> a + 0.123124 + b <= 11.123124 && r == 1.2 },
            { _, a, b, r -> a + 0.123124 + b >= 11.125 && r == 1.2 }
        )
    }

    @Test
    fun testSimpleMul() {
        checkExecutionMatches(
            DoubleExamples::simpleMul,
            { _, a, b, r -> (a * b).isNaN() && r == 0.0 },
            { _, a, b, r -> a * b > 33.1 && a * b < 33.875 && r == 1.1 },
            { _, a, b, r -> a * b <= 33.1 && r == 1.2 },
            { _, a, b, r -> a * b >= 33.875 && r == 1.2 }
        )
    }

    @Test
    fun testMul() {
        checkExecutionMatches(
            DoubleExamples::mul,
            { _, a, b, r -> (a * b).isNaN() && r == 0.0 }, // 0 * inf || a == nan || b == nan
            { _, a, b, r -> !(a * b > 33.32) && !(a * b > 33.333) && r == 1.3 }, // 1.3, 1-1 false, 2-1 false
            { _, a, b, r -> a * b == 33.333 && r == 1.3 }, // 1.3, 1-1 true, 1-2 false, 2-1 false
            { _, a, b, r -> a * b > 33.32 && a * b < 33.333 && r == 1.1 }, // 1.1, 1st true
            { _, a, b, r -> a * b > 33.333 && a * b < 33.7592 && r == 1.2 }, // 1.2, 1st false, 2nd true
            { _, a, b, r -> a * b >= 33.7592 && r == 1.3 } // 1.3, 1-1 false, 2-1 true, 2-2 false
        )
    }

    @Test
    fun testCheckNonInteger() {
        checkExecutionMatches(
            DoubleExamples::checkNonInteger,
            { _, a, r -> !(a > 0.1) && r == 0.0 },
            { _, a, r -> a > 0.1 && !(a < 0.9) && r == 0.0 },
            { _, a, r -> a > 0.1 && a < 0.9 && r == 1.0 }
        )
    }

    @Test
    fun testDiv() {
        checkExecutionMatches(
            DoubleExamples::div,
            { _, a, b, c, r -> r == (a + b) / c || (r.isNaN() && (a + b + c).isNaN()) }
        )
    }

    @Test
    fun testSimpleEquation() {
        checkExecutionMatches(
            DoubleExamples::simpleEquation,
            { _, x, r -> x + x + x - 9 == x + 3 && r == 0 },
            { _, x, r -> x + x + x - 9 != x + 3 && r == 1 }
        )
    }

    @Test
    fun testSimpleNonLinearEquation() {
        checkExecutionMatches(
            DoubleExamples::simpleNonLinearEquation,
            { _, x, r -> 3 * x - 9 == x + 3 && r == 0 },
            { _, x, r -> 3 * x - 9 != x + 3 && r == 1 }
        )
    }

    @Test
    fun testCheckNaN() {
        checkExecutionMatches(
            DoubleExamples::checkNaN,
            { _, d, r -> !d.isNaN() && d < 0 && r == -1 },
            { _, d, r -> !d.isNaN() && d == 0.0 && r == 0 },
            { _, d, r -> !d.isNaN() && d > 0 && r == 1 },
            { _, d, r -> d.isNaN() && r == 100 }
        )
    }

    @Test
    fun testUnaryMinus() {
        checkExecutionMatches(
            DoubleExamples::unaryMinus,
            { _, d, r -> !d.isNaN() && -d < 0 && r == -1 },
            { _, d, r -> d.isNaN() || -d >= 0 && r == 0 }
        )
    }

    @Test
    fun testDoubleInfinity() {
        checkExecutionMatches(
            DoubleExamples::doubleInfinity,
            { _, d, r -> d == Double.POSITIVE_INFINITY && r == 1 },
            { _, d, r -> d == Double.NEGATIVE_INFINITY && r == 2 },
            { _, d, r -> !d.isInfinite() && r == 3 },
        )
    }
}
