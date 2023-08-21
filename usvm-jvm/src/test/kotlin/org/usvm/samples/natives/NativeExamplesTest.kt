package org.usvm.samples.natives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.util.disableTest


internal class NativeExamplesTest : JavaMethodTestRunner() {

    @Test
    fun testFindAndPrintSum() = disableTest("slow on CI") {
        checkDiscoveredProperties(
            NativeExamples::findAndPrintSum,
            ge(1),
        )
    }

    @Test
    fun testFindSumWithMathRandom() = disableTest("Expected exactly 1 executions, but 301 found") {
        checkDiscoveredProperties(
            NativeExamples::findSumWithMathRandom,
            eq(1),
        )
    }
}