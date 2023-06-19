package org.usvm.samples.natives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge


internal class NativeExamplesTest : JavaMethodTestRunner() {

    @Test
    fun testFindAndPrintSum() {
        checkExecutionMatches(
            NativeExamples::findAndPrintSum,
        )
    }

    @Test
    fun testFindSumWithMathRandom() {
        checkExecutionMatches(
            NativeExamples::findSumWithMathRandom,
        )
    }
}