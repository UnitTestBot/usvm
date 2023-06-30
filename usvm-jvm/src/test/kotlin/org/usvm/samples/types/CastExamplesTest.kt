package org.usvm.samples.types

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SimplifyNegatedBinaryExpression")
internal class CastExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Sort mismatch")
    fun testLongToByte() {
        checkDiscoveredProperties(
            CastExamples::longToByte,
            eq(3),
            { _, a, b, r -> (a.toByte() + b.toByte()).toByte() > 10 && r == (a.toByte() + b.toByte()).toByte() },
            { _, a, b, r -> (a.toByte() + b.toByte()).toByte() <= 10 && a.toByte() > b.toByte() && r == (-1).toByte() },
            { _, a, b, r -> (a.toByte() + b.toByte()).toByte() <= 10 && a.toByte() <= b.toByte() && r == (0).toByte() },
        )
    }

    @Test
    fun testShortToLong() {
        checkDiscoveredProperties(
            CastExamples::shortToLong,
            eq(3),
            { _, a, b, r -> a + b > 10 && r == a.toLong() + b.toLong() },
            { _, a, b, r -> a + b <= 10 && a > b && r == -1L },
            { _, a, b, r -> a + b <= 10 && a <= b && r == 0L },
        )
    }

    @Test
    fun testFloatToDouble() {
        checkDiscoveredProperties(
            CastExamples::floatToDouble,
            eq(4),
            { _, a, b, r -> a.toDouble() + b.toDouble() > Float.MAX_VALUE && r == 2.0 },
            { _, a, b, r -> a.toDouble() + b.toDouble() > 10 && r == 1.0 },
            { _, a, b, r -> !(a.toDouble() + b.toDouble() > 10) && !(a.toDouble() > b.toDouble()) && r == 0.0 },
            { _, a, b, r -> !(a.toDouble() + b.toDouble() > 10) && a.toDouble() > b.toDouble() && r == -1.0 },
        )
    }

    @Test
    fun testDoubleToFloatArray() {
        checkDiscoveredProperties(
            CastExamples::doubleToFloatArray,
            eq(2),
            { _, x, r -> x.toFloat() + 5 > 20 && r == 1.0f },
            { _, x, r -> !(x.toFloat() + 5 > 20) && r == 0.0f }
        )
    }

    @Test
    fun testFloatToInt() {
        checkDiscoveredProperties(
            CastExamples::floatToInt,
            eq(3),
            { _, x, r -> x < 0 && x.toInt() < 0 && r == 1 },
            { _, x, r -> x < 0 && x.toInt() >= 0 && r == 2 },
            { _, x, r -> !(x < 0) && r == 3 },
        )
    }

    @Test
    @Disabled("Sort mismatch")
    fun testShortToChar() {
        checkDiscoveredProperties(
            CastExamples::shortToChar,
            eq(3),
            { _, a, b, r -> (a.charInt() + b.charInt()).charInt() > 10 && r == (a.charInt() + b.charInt()).toChar() },
            { _, a, b, r -> (a.charInt() + b.charInt()).charInt() <= 10 && a.charInt() <= b.charInt() && r == (0).toChar() },
            { _, a, b, r -> (a.charInt() + b.charInt()).charInt() <= 10 && a.charInt() > b.charInt() && r == (-1).toChar() },
        )
    }

    // Special cast to emulate Java binary numeric promotion
    private fun Number.charInt() = toChar().code
}