package org.usvm.samples.arrays


import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
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
    @Disabled("Sort mismatch")
    fun testByteArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::byteArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    @Disabled("Sort mismatch")
    fun testShortArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::shortArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    @Disabled("Sort mismatch")
    fun testCharArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::charArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20.toChar() && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20.toChar() && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testIntArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::intArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toByte() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toByte() }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testLongArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::longArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toLong() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 <= 20 && r.getOrNull() == 0.toLong() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toLong() }
        )
    }

    @Suppress("SimplifyNegatedBinaryExpression")
    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testFloatArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::floatArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toFloat() },
            { _, a, x, r -> a != null && a.size == 2 && !(x + 5 > 20) && r.getOrNull() == 0.toFloat() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toFloat() }
        )
    }

    @Suppress("SimplifyNegatedBinaryExpression")
    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testDoubleArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::doubleArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, r -> a != null && a.size != 2 && r.getOrNull() == (-1).toDouble() },
            { _, a, x, r -> a != null && a.size == 2 && !(x + 5 > 20) && r.getOrNull() == 0.toDouble() },
            { _, a, x, r -> a != null && a.size == 2 && x + 5 > 20 && r.getOrNull() == 1.toDouble() }
        )
    }

    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testBooleanArray() {
        checkDiscoveredPropertiesWithExceptions(
            PrimitiveArrays::booleanArray,
            ignoreNumberOfAnalysisResults,
            { _, a, _, _, r -> a == null && r.isException<NullPointerException>() },
            { _, a, _, _, r -> a != null && a.size != 2 && r.getOrNull() == -1 },
            { _, a, x, y, r -> a != null && a.size == 2 && !(x xor y) && r.getOrNull() == 0 },
            { _, a, x, y, r -> a != null && a.size == 2 && (x xor y) && r.getOrNull() == 1 }
        )
    }

    @Test
    @Disabled("Sort mismatch")
    fun testByteSizeAndIndex() {
        checkDiscoveredProperties(
            PrimitiveArrays::byteSizeAndIndex,
            ignoreNumberOfAnalysisResults,
            { _, a, x, r -> (a == null || a.size <= x || x < 1) && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 <= 7 && r == 0.toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 > 7 && r == 1.toByte() }
        )
    }

    @Test
    @Disabled("Sort mismatch")
    fun testShortSizeAndIndex() {
        checkDiscoveredProperties(
            PrimitiveArrays::shortSizeAndIndex,
            ignoreNumberOfAnalysisResults,
            { _, a, x, r -> (a == null || a.size <= x || x < 1) && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 <= 7 && r == 0.toByte() },
            { _, a, x, r -> a != null && a.size > x.toInt() && x.toInt() > 0 && x + 5 > 7 && r == 1.toByte() }
        )
    }

    @Test
    @Disabled("java.lang.OutOfMemoryError: Java heap space")
    fun testCharSizeAndIndex() {
        checkDiscoveredProperties(
            PrimitiveArrays::charSizeAndIndex,
            ignoreNumberOfAnalysisResults,
            { _, a, _, r -> a == null && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size <= x.code && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.code && x.code < 1 && r == (-1).toByte() },
            { _, a, x, r -> a != null && a.size > x.code && x.code > 0 && x + 5 <= 7.toChar() && r == 0.toByte() },
            { _, a, x, r -> a != null && a.size > x.code && x.code > 0 && x + 5 > 7.toChar() && r == 1.toByte() }
        )
    }
}