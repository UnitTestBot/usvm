package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class FinalStaticFieldArrayTest : JavaMethodTestRunner() {
    @Test
    fun testFactorial() {
        checkExecutionMatches(
            FinalStaticFieldArray::factorial,
            ignoreNumberOfAnalysisResults,
            { n, r -> n >= 0 && n < FinalStaticFieldArray.MAX_FACTORIAL && r == FinalStaticFieldArray.factorial(n) },
            { n, _ -> n < 0 },
            { n, r -> n > FinalStaticFieldArray.MAX_FACTORIAL && r == FinalStaticFieldArray.factorial(n) },
        )
    }
}