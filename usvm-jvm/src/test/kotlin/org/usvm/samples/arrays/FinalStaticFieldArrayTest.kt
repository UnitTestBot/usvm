package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest


internal class FinalStaticFieldArrayTest : JavaMethodTestRunner() {
    @Test
    fun testFactorial() = disableTest("slow on CI") {
        checkDiscoveredProperties(
            FinalStaticFieldArray::factorial,
            ignoreNumberOfAnalysisResults,
            { n, r -> n >= 0 && n < FinalStaticFieldArray.MAX_FACTORIAL && r == FinalStaticFieldArray.factorial(n) },
            { n, _ -> n < 0 },
            { n, r -> n > FinalStaticFieldArray.MAX_FACTORIAL && r == FinalStaticFieldArray.factorial(n) },
        )
    }
}