package org.usvm.samples.math

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class BitOperatorsTest : JavaMethodTestRunner() {
    @Test
    fun testComplement() {
        checkDiscoveredProperties(
            BitOperators::complement,
            eq(2),
            { _, x, r -> x == -2 && r == true },
            { _, x, r -> x != -2 && r == false }
        )
    }

    @Test
    fun testXor() {
        checkDiscoveredProperties(
            BitOperators::xor,
            eq(2),
            { _, x, y, r -> x == y && r == true },
            { _, x, y, r -> x != y && r == false }
        )
    }

    @Test
    fun testOr() {
        checkDiscoveredProperties(
            BitOperators::or,
            eq(2),
            { _, x, r -> x < 16 && (x and 0xfffffff8.toInt()) == 8 && r == true },
            { _, x, r -> x >= 16 || (x and 0xfffffff8.toInt()) != 8 && r == false }
        )
    }

    @Test
    @ExperimentalStdlibApi
    fun testAnd() {
        checkDiscoveredProperties(
            BitOperators::and,
            eq(2),
            { _, x, r -> x.countOneBits() <= 1 && r == true },
            { _, x, r -> x.countOneBits() > 1 && r == false }
        )
    }

    @Test
    fun testBooleanNot() {
        checkDiscoveredProperties(
            BitOperators::booleanNot,
            eq(3),
            { _, a, b, r -> a && b && r == 100 },
            { _, a, b, r -> a && !b && r == 200 },
            { _, a, b, r -> !a && !b && r == 200 },
        )
    }

    @Test
    fun testBooleanXor() {
        checkDiscoveredProperties(
            BitOperators::booleanXor,
            eq(1)
        )
    }

    @Test
    fun testBooleanOr() {
        checkDiscoveredProperties(
            BitOperators::booleanOr,
            eq(1)
        )
    }

    @Test
    fun testBooleanAnd() {
        checkDiscoveredProperties(
            BitOperators::booleanAnd,
            eq(1)
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testBooleanXorCompare() {
        checkDiscoveredProperties(
            BitOperators::booleanXorCompare,
            eq(2),
            { _, a, b, r -> a != b && r == 1 },
            { _, a, b, r -> a == b && r == 0 }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testShl() {
        checkDiscoveredProperties(
            BitOperators::shl,
            eq(2),
            { _, x, r -> x == 1 && r == true },
            { _, x, r -> x != 1 && r == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testShlLong() {
        checkDiscoveredProperties(
            BitOperators::shlLong,
            eq(2),
            { _, x, r -> x == 1L && r == true },
            { _, x, r -> x != 1L && r == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testShlWithBigLongShift() {
        checkDiscoveredProperties(
            BitOperators::shlWithBigLongShift,
            eq(3),
            { _, shift, r -> shift < 40 && r == 1 },
            { _, shift, r -> shift >= 40 && shift and 0b11111 == 4L && r == 2 },
            { _, shift, r -> shift >= 40 && shift and 0b11111 != 4L && r == 3 },
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testShr() {
        checkDiscoveredProperties(
            BitOperators::shr,
            eq(2),
            { _, x, r -> x shr 1 == 1 && r == true },
            { _, x, r -> x shr 1 != 1 && r == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testShrLong() {
        checkDiscoveredProperties(
            BitOperators::shrLong,
            eq(2),
            { _, x, r -> x shr 1 == 1L && r == true },
            { _, x, r -> x shr 1 != 1L && r == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testUshr() {
        checkDiscoveredProperties(
            BitOperators::ushr,
            eq(2),
            { _, x, r -> x ushr 1 == 1 && r == true },
            { _, x, r -> x ushr 1 != 1 && r == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testUshrLong() {
        checkDiscoveredProperties(
            BitOperators::ushrLong,
            eq(2),
            { _, x, r -> x ushr 1 == 1L && r == true },
            { _, x, r -> x ushr 1 != 1L && r == false }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testSign() {
        checkDiscoveredProperties(
            BitOperators::sign,
            eq(3),
            { _, x, r -> x > 0 && r == 1 },
            { _, x, r -> x == 0 && r == 0 },
            { _, x, r -> x < 0 && r == -1 }
        )
    }
}