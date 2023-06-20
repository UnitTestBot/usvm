package org.usvm.samples.arrays


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException


internal class PrimitiveArraysTest : JavaMethodTestRunner() {
    @Test
    fun testDefaultIntValues() {
        checkDiscoveredProperties(
            PrimitiveArrays::defaultIntValues,
            eq(1),
            { _, r -> r != null && r.all { it == 0 } },
        )
    }

    @Test
    fun testDefaultDoubleValues() {
        checkDiscoveredProperties(
            PrimitiveArrays::defaultDoubleValues,
            eq(1),
            { _, r -> r != null && r.all { it == 0.0 } },
        )
    }

    @Test
    fun testDefaultBooleanValues() {
        checkDiscoveredProperties(
            PrimitiveArrays::defaultBooleanValues,
            eq(1),
            { _, r -> r != null && r.none { it } },
        )
    }

    @Test
    fun testByteArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::byteArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    fun testShortArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::shortArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    fun testCharArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::charArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20.toChar() && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20.toChar() && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    fun testIntArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::intArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    fun testLongArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::longArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toLong() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toLong() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toLong() }
        )
    }

    @Suppress("SimplifyNegatedBinaryExpression")
    @Test
    fun testFloatArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::floatArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toFloat() },
            { _, a, x, r -> a != null && a.size == 2 && !(x + 5 > 20) && r.getOrNull() == 0.toFloat() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toFloat() }
        )
    }

    @Suppress("SimplifyNegatedBinaryExpression")
    @Test
    fun testDoubleArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::doubleArray,
            eq(4),
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toDouble() },
            { _, a, x, r -> a != null && a.size == 2 && !(x + 5 > 20) && r.getOrNull() == 0.toDouble() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toDouble() }
        )
    }

    @Test
    fun testBooleanArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::booleanArray,
            eq(4),
            { _, a, _, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, _, r -> a != null && a.size != 2 && r.getOrNull() == -1 },
            { _, a, x, y, r -> a != null && a.size == 2 && !(x xor y) && r.getOrNull() == 0 },
            { _, a, x, y, r -> a != null && a.size == 2 && (x xor y) && r.getOrNull() == 1 }
        )
    }

    @Test
    fun testByteSizeAndIndex() {
        checkDiscoveredProperties(
            PrimitiveArrays::byteSizeAndIndex,
            eq(5),
            { _, a, _, r -> a == null && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size <= x.toInt() && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() < 1 && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 <= 7 && r == 0.toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 > 7 && r == 1.toByte() }
        )
    }

    @Test
    fun testShortSizeAndIndex() {
        checkDiscoveredProperties(
            PrimitiveArrays::shortSizeAndIndex,
            eq(5),
            { _, a, _, r -> a == null && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size <= x.toInt() && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() < 1 && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 <= 7 && r == 0.toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 > 7 && r == 1.toByte() }
        )
    }

    @Test
    fun testCharSizeAndIndex() {
        checkDiscoveredProperties(
            PrimitiveArrays::charSizeAndIndex,
            eq(5),
            { _, a, _, r -> a == null && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size <= x.code && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.code && x.code < 1 && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.code && x.code > 0 && x + 5 <= 7.toChar() && r == 0.toByte() },
            { _, a, x, r -> a != null && a.size > x.code && x.code > 0 && x + 5 > 7.toChar() && r == 1.toByte() }
        )
    }
}