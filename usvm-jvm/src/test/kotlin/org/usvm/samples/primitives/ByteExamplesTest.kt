package org.usvm.samples.primitives

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class ByteExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Bytes are unsupported")
    fun testNegByte() {
        checkDiscoveredProperties(
            ByteExamples::negByte,
            eq(2),
            { _, b, r -> b > 0 && r == 0 },
            { _, b, r -> b <= 0 && r == 1 },
        )
    }

    @Test
    fun testNegConstByte() {
        checkDiscoveredProperties(
            ByteExamples::negConstByte,
            ignoreNumberOfAnalysisResults,
            { _, b, r -> b in -9..9 && r == 0 },
            { _, b, r -> b !in -9..9 && r == 1 },
        )
    }

    @Test
    @Disabled("Bytes are unsupported")
    fun testSumTwoBytes() {
        checkDiscoveredProperties(
            ByteExamples::sumTwoBytes,
            eq(3),
            { _, a, b, r -> a + b > Byte.MAX_VALUE && r == 1 },
            { _, a, b, r -> a + b < Byte.MIN_VALUE && r == 2 },
            { _, a, b, r -> a + b in Byte.MIN_VALUE..Byte.MAX_VALUE && r == 3 },
        )
    }
}