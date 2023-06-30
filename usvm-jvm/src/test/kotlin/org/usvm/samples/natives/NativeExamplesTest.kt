package org.usvm.samples.natives

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge


internal class NativeExamplesTest : JavaMethodTestRunner() {

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@6ba88a47")
    fun testFindAndPrintSum() {
        checkDiscoveredProperties(
            NativeExamples::findAndPrintSum,
            ge(1),
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@53b2e5")
    fun testFindSumWithMathRandom() {
        checkDiscoveredProperties(
            NativeExamples::findSumWithMathRandom,
            eq(1),
        )
    }
}