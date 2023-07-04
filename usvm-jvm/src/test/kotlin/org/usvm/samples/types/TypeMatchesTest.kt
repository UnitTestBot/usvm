package org.usvm.samples.types

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Suppress("SimplifyNegatedBinaryExpression")
internal class TypeMatchesTest : JavaMethodTestRunner() {
    @Test
    fun testCompareDoubleByte() {
        checkDiscoveredProperties(
            TypeMatches::compareDoubleByte,
            eq(2),
            { _, a, b, r -> a < b && r == 0.0 },
            { _, a, b, r -> !(a < b) && r == 1.0 }
        )
    }

    @Test
    fun testCompareShortLong() {
        checkDiscoveredProperties(
            TypeMatches::compareShortLong,
            eq(2),
            { _, a, b, r -> a < b && r == 0.toShort() },
            { _, a, b, r -> a >= b && r == 1.toShort() }
        )
    }

    @Test
    fun testCompareFloatDouble() {
        checkDiscoveredProperties(
            TypeMatches::compareFloatDouble,
            eq(2),
            { _, a, b, r -> a < b && r == 0.0f },
            { _, a, b, r -> !(a < b) && r == 1.0f }
        )
    }

    @Test
    fun testSumByteAndShort() {
        checkDiscoveredProperties(
            TypeMatches::sumByteAndShort,
            eq(3),
            { _, a, b, r -> a + b > Short.MAX_VALUE && r == 1 },
            { _, a, b, r -> a + b < Short.MIN_VALUE && r == 2 },
            { _, a, b, r -> a + b in Short.MIN_VALUE..Short.MAX_VALUE && r == 3 },
        )
    }

    @Test
    fun testSumShortAndChar() {
        checkDiscoveredProperties(
            TypeMatches::sumShortAndChar,
            eq(3),
            { _, a, b, r -> a + b.code > Char.MAX_VALUE.code && r == 1 },
            { _, a, b, r -> a + b.code < Char.MIN_VALUE.code && r == 2 },
            { _, a, b, r -> a + b.code in Char.MIN_VALUE.code..Char.MAX_VALUE.code && r == 3 },
        )
    }
}
