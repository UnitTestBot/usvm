package org.usvm.samples.primitives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ByteExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testNegByte() {
        checkExecutionMatches(
            ByteExamples::negByte,
            { _, b, r -> b > 0 && r == 0 },
            { _, b, r -> b <= 0 && r == 1 },
        )
    }

    @Test
    fun testNegConstByte() {
        checkExecutionMatches(
            ByteExamples::negConstByte,
            { _, b, r -> b <= -10 && r == 1 },
            { _, b, r -> b in -9..9 && r == 0 },
            { _, b, r -> b >= 10 && r == 1 },
        )
    }

    @Test
    fun testSumTwoBytes() {
        checkExecutionMatches(
            ByteExamples::sumTwoBytes,
            { _, a, b, r -> a + b > Byte.MAX_VALUE && r == 1 },
            { _, a, b, r -> a + b < Byte.MIN_VALUE && r == 2 },
            { _, a, b, r -> a + b in Byte.MIN_VALUE..Byte.MAX_VALUE && r == 3 },
        )
    }
}