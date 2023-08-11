package org.usvm.samples.threads

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException


// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
class ThreadExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testExceptionInStart() = disableTest("Support invokedynamic") {
        checkDiscoveredPropertiesWithExceptions(
            ThreadExamples::explicitExceptionInStart,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IllegalStateException>() }
        )
    }

    @Test
    fun testChangingCollectionInThread() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            ThreadExamples::changingCollectionInThread,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == 42 },
        )
    }

    @Test
    fun testChangingCollectionInThreadWithoutStart() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ThreadExamples::changingCollectionInThreadWithoutStart,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IndexOutOfBoundsException>() },
        )
    }
}
