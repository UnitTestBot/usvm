package org.usvm.samples.threads

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException


// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
class ExecutorServiceExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testExceptionInExecute() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ExecutorServiceExamples::throwingInExecute,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IllegalStateException>() }
        )
    }

    @Test
    fun testChangingCollectionInExecute() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            ExecutorServiceExamples::changingCollectionInExecute,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == 42 },
        )
    }
}
