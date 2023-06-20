package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class TypeBordersTest : JavaMethodTestRunner() {
    @Test
    fun testByteBorder() {
        checkDiscoveredProperties(
            TypeBorders::byteBorder,
            eq(3),
            { _, x, r -> x == Byte.MIN_VALUE && r == 3 },
            { _, x, r -> x == Byte.MAX_VALUE && r == 2 },
            { _, x, r -> x > Byte.MIN_VALUE && x < Byte.MAX_VALUE && r == 4 }
        )
    }

    @Test
    fun testShortBorder() {
        checkDiscoveredProperties(
            TypeBorders::shortBorder,
            eq(3),
            { _, x, r -> x == Short.MIN_VALUE && r == 3 },
            { _, x, r -> x == Short.MAX_VALUE && r == 2 },
            { _, x, r -> x > Short.MIN_VALUE && x < Short.MAX_VALUE && r == 4 }
        )
    }

    @Test
    fun testCharBorder() {
        checkDiscoveredProperties(
            TypeBorders::charBorder,
            eq(3),
            { _, x, r -> x == Char.MIN_VALUE && r == 3 },
            { _, x, r -> x == Char.MAX_VALUE && r == 2 },
            { _, x, r -> x > Char.MIN_VALUE && x < Char.MAX_VALUE && r == 4 }
        )
    }

    @Test
    fun testIntBorder() {
        checkDiscoveredProperties(
            TypeBorders::intBorder,
            eq(3),
            { _, x, r -> x == Int.MIN_VALUE && r == 3 },
            { _, x, r -> x == Int.MAX_VALUE && r == 2 },
            { _, x, r -> x > Int.MIN_VALUE && x < Int.MAX_VALUE && r == 4 }
        )
    }

    @Test
    fun testLongBorder() {
        checkDiscoveredProperties(
            TypeBorders::longBorder,
            eq(3),
            { _, x, r -> x == Long.MIN_VALUE && r == 3 },
            { _, x, r -> x == Long.MAX_VALUE && r == 2 },
            { _, x, r -> x > Long.MIN_VALUE && x < Long.MAX_VALUE && r == 4 }
        )
    }

    @Test
    fun testUnreachableByteValue() {
        checkDiscoveredProperties(
            TypeBorders::unreachableByteValue,
            eq(1), // should generate one branch with legal byte value
            { _, x, r -> r == 0 && x < 200 },
        )
    }
}
