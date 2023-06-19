package org.usvm.samples.primitives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

internal class CharExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testCharDiv() {
        checkWithExceptionExecutionMatches(
            CharExamples::charDiv,
            { _, _, b, r -> b == '\u0000' && r.isException<ArithmeticException>() },
            { _, a, b, r -> b != '\u0000' && r.getOrNull() == a.code / b.code }
        )
    }

    @Test
    fun testCharNeg() {
        checkExecutionMatches(
            CharExamples::charNeg,
            { _, c, r -> c !in '\u0000'..'\uC350' && r == 1 },
            { _, c, r -> c in '\u0000'..'\uC350' && r == 2 },
        )
    }

    @Test
    fun testByteToChar() {
        checkExecutionMatches(
            CharExamples::byteToChar,
            { _, b, r -> b == (-1).toByte() && r == -1 },
            { _, b, r -> b == (-128).toByte() && r == -128 },
            { _, b, r -> b == 0.toByte() && r == 0 },
            { _, b, r -> b == 127.toByte() && r == 127 },
            { _, b, r -> b != (-1).toByte() && b != (-128).toByte() && b != 0.toByte() && b != 127.toByte() && r == 200 },
        )
    }

    @Test
    fun testUpdateObject() {
        checkWithExceptionExecutionMatches(
            CharExamples::updateObject,
            { _, obj, _, r -> obj == null && r.isException<NullPointerException>() },
            { _, obj, i, r -> obj != null && i <= 50000 && r.getOrNull()!!.c == '\u0444' },
            { _, obj, i, r -> obj != null && i.toChar() > 50000.toChar() && r.getOrNull()?.c == i.toChar() },
        )
    }
}