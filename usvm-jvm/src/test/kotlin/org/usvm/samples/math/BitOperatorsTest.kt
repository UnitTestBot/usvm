package org.usvm.samples.math

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner


internal class BitOperatorsTest : JavaMethodTestRunner() {
    @Test
    fun testComplement() {
        checkExecutionMatches(
            BitOperators::complement,
            { _, x, r -> x == -2 && r == true },
            { _, x, r -> x != -2 && r == false }
        )
    }

    @Test
    fun testXor() {
        checkExecutionMatches(
            BitOperators::xor,
            { _, x, y, r -> x == y && r == true },
            { _, x, y, r -> x != y && r == false }
        )
    }

    @Test
    fun testOr() {
        checkExecutionMatches(
            BitOperators::or,
            { _, x, r -> x < 16 && (x and 0xfffffff8.toInt()) == 8 && r == true },
            { _, x, r -> x >= 16 || (x and 0xfffffff8.toInt()) != 8 && r == false }
        )
    }

    @Test
    @ExperimentalStdlibApi
    fun testAnd() {
        checkExecutionMatches(
            BitOperators::and,
            { _, x, r -> x.countOneBits() <= 1 && r == true },
            { _, x, r -> x.countOneBits() > 1 && r == false }
        )
    }

    @Test
    fun testBooleanNot() {
        checkExecutionMatches(
            BitOperators::booleanNot,
            { _, a, b, r -> a && b && r == 100 },
            { _, a, b, r -> a && !b && r == 200 },
            { _, a, b, r -> !a && !b && r == 200 },
        )
    }

    @Test
    fun testBooleanXor() {
        checkExecutionMatches(
            BitOperators::booleanXor,
        )
    }

    @Test
    fun testBooleanOr() {
        checkExecutionMatches(
            BitOperators::booleanOr,
        )
    }

    @Test
    fun testBooleanAnd() {
        checkExecutionMatches(
            BitOperators::booleanAnd,
        )
    }

    @Test
    fun testBooleanXorCompare() {
        checkExecutionMatches(
            BitOperators::booleanXorCompare,
            { _, a, b, r -> a != b && r == 1 },
            { _, a, b, r -> a == b && r == 0 }
        )
    }

    @Test
    fun testShl() {
        checkExecutionMatches(
            BitOperators::shl,
            { _, x, r -> x == 1 && r == true },
            { _, x, r -> x != 1 && r == false }
        )
    }

    @Test
    fun testShlLong() {
        checkExecutionMatches(
            BitOperators::shlLong,
            { _, x, r -> x == 1L && r == true },
            { _, x, r -> x != 1L && r == false }
        )
    }

    @Test
    fun testShlWithBigLongShift() {
        checkExecutionMatches(
            BitOperators::shlWithBigLongShift,
            { _, shift, r -> shift < 40 && r == 1 },
            { _, shift, r -> shift >= 40 && shift and 0b11111 == 4L && r == 2 },
            { _, shift, r -> shift >= 40 && shift and 0b11111 != 4L && r == 3 },
        )
    }

    @Test
    fun testShr() {
        checkExecutionMatches(
            BitOperators::shr,
            { _, x, r -> x shr 1 == 1 && r == true },
            { _, x, r -> x shr 1 != 1 && r == false }
        )
    }

    @Test
    fun testShrLong() {
        checkExecutionMatches(
            BitOperators::shrLong,
            { _, x, r -> x shr 1 == 1L && r == true },
            { _, x, r -> x shr 1 != 1L && r == false }
        )
    }

    @Test
    fun testUshr() {
        checkExecutionMatches(
            BitOperators::ushr,
            { _, x, r -> x ushr 1 == 1 && r == true },
            { _, x, r -> x ushr 1 != 1 && r == false }
        )
    }

    @Test
    fun testUshrLong() {
        checkExecutionMatches(
            BitOperators::ushrLong,
            { _, x, r -> x ushr 1 == 1L && r == true },
            { _, x, r -> x ushr 1 != 1L && r == false }
        )
    }

    @Test
    fun testSign() {
        checkExecutionMatches(
            BitOperators::sign,
            { _, x, r -> x > 0 && r == 1 },
            { _, x, r -> x == 0 && r == 0 },
            { _, x, r -> x < 0 && r == -1 }
        )
    }
}