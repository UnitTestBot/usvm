package org.usvm.samples.natives

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge


internal class NativeExamplesTest : JavaMethodTestRunner() {

    @Test
    fun testFindAndPrintSum() {
        checkDiscoveredProperties(
            NativeExamples::findAndPrintSum,
            ge(1),
        )
    }

    @Test
    fun testFindSumWithMathRandom() {
        checkDiscoveredProperties(
            NativeExamples::findSumWithMathRandom,
            eq(1),
        )
    }
}