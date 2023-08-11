package org.usvm.samples.threads

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
class ExecutorServiceExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testExceptionInExecute() {
        checkDiscoveredPropertiesWithExceptions(
            ExecutorServiceExamples::throwingInExecute,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IllegalStateException>() }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testChangingCollectionInExecute() {
        checkDiscoveredProperties(
            ExecutorServiceExamples::changingCollectionInExecute,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == 42 },
        )
    }
}
