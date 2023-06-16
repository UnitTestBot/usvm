package org.usvm.samplesmath

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.math.BitOperators
import org.usvm.test.util.checkers.eq


internal class BitOperatorsTest : JavaMethodTestRunner() {
    @Test
    fun testComplement() {
        checkExecutionMatches(
            BitOperators::complement,
            eq(2),
            { _, x, r -> x == -2 && r == true },
            { _, x, r -> x != -2 && r == false }
        )
    }

    @Test
    fun testXor() {
        checkExecutionMatches(
            BitOperators::xor,
            eq(2),
            { _, x, y, r -> x == y && r == true },
            { _, x, y, r -> x != y && r == false }
        )
    }

    @Test
    fun testOr() {
        checkExecutionMatches(
            BitOperators::or,
            eq(2),
            { _, x, r -> x < 16 && (x and 0xfffffff8.toInt()) == 8 && r == true },
            { _, x, r -> x >= 16 || (x and 0xfffffff8.toInt()) != 8 && r == false }
        )
    }

    @Test
    @ExperimentalStdlibApi
    fun testAnd() {
        checkExecutionMatches(
            BitOperators::and,
            eq(2),
            { _, x, r -> x.countOneBits() <= 1 && r == true },
            { _, x, r -> x.countOneBits() > 1 && r == false }
        )
    }

    @Test
    fun testBooleanNot() {
        checkExecutionMatches(
            BitOperators::booleanNot,
            eq(3),
            { _, a, b, r -> a && b && r == 100 },
            { _, a, b, r -> a && !b && r == 200 },
            { _, a, b, r -> !a && !b && r == 200 },
        )
    }

    @Test
    fun testBooleanXor() {
        checkExecutionMatches(
            BitOperators::booleanXor,
            eq(1)
        )
    }

    @Test
    fun testBooleanOr() {
        checkExecutionMatches(
            BitOperators::booleanOr,
            eq(1)
        )
    }

    @Test
    fun testBooleanAnd() {
        checkExecutionMatches(
            BitOperators::booleanAnd,
            eq(1)
        )
    }

    @Test
    fun testBooleanXorCompare() {
        checkExecutionMatches(
            BitOperators::booleanXorCompare,
            eq(2),
            { _, a, b, r -> a != b && r == 1 },
            { _, a, b, r -> a == b && r == 0 }
        )
    }

    @Test
    fun testShl() {
        checkExecutionMatches(
            BitOperators::shl,
            eq(2),
            { _, x, r -> x == 1 && r == true },
            { _, x, r -> x != 1 && r == false }
        )
    }

    @Test
    fun testShlLong() {
        checkExecutionMatches(
            BitOperators::shlLong,
            eq(2),
            { _, x, r -> x == 1L && r == true },
            { _, x, r -> x != 1L && r == false }
        )
    }

    @Test
    fun testShlWithBigLongShift() {
        checkExecutionMatches(
            BitOperators::shlWithBigLongShift,
            eq(3),
            { _, shift, r -> shift < 40 && r == 1 },
            { _, shift, r -> shift >= 40 && shift and 0b11111 == 4L && r == 2 },
            { _, shift, r -> shift >= 40 && shift and 0b11111 != 4L && r == 3 },
        )
    }

    @Test
    fun testShr() {
        checkExecutionMatches(
            BitOperators::shr,
            eq(2),
            { _, x, r -> x shr 1 == 1 && r == true },
            { _, x, r -> x shr 1 != 1 && r == false }
        )
    }

    @Test
    fun testShrLong() {
        checkExecutionMatches(
            BitOperators::shrLong,
            eq(2),
            { _, x, r -> x shr 1 == 1L && r == true },
            { _, x, r -> x shr 1 != 1L && r == false }
        )
    }

    @Test
    fun testUshr() {
        checkExecutionMatches(
            BitOperators::ushr,
            eq(2),
            { _, x, r -> x ushr 1 == 1 && r == true },
            { _, x, r -> x ushr 1 != 1 && r == false }
        )
    }

    @Test
    fun testUshrLong() {
        checkExecutionMatches(
            BitOperators::ushrLong,
            eq(2),
            { _, x, r -> x ushr 1 == 1L && r == true },
            { _, x, r -> x ushr 1 != 1L && r == false }
        )
    }

    @Test
    fun testSign() {
        checkExecutionMatches(
            BitOperators::sign,
            eq(3),
            { _, x, r -> x > 0 && r == 1 },
            { _, x, r -> x == 0 && r == 0 },
            { _, x, r -> x < 0 && r == -1 }
        )
    }
}