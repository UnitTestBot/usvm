package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

// TODO rename it
class FirstExamplesTest : PandaMethodTestRunner() {
    @Test
    fun testDataFlowSecurity() {
        internalCheck(
            target = "DataFlowSecurity" to "validate",
            ignoreNumberOfAnalysisResults,
            emptyArray(),
            emptyArray(),
            { _ -> emptyList() },
            emptyArray(),
            CheckMode.MATCH_PROPERTIES,
            { _ -> true }
        )
    }
}