package org.usvm.samples.natives

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge


internal class NativeExamplesTest : JavaMethodTestRunner() {

    @Test
    @Disabled("slow on CI")
    fun testFindAndPrintSum() {
        checkDiscoveredProperties(
            NativeExamples::findAndPrintSum,
            ge(1),
        )
    }

    @Test
    @Disabled("Expected exactly 1 executions, but 301 found")
    fun testFindSumWithMathRandom() {
        checkDiscoveredProperties(
            NativeExamples::findSumWithMathRandom,
            eq(1),
        )
    }
}