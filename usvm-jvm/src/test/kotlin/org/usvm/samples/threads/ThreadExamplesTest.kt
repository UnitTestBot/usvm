package org.usvm.samples.threads

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
class ThreadExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Support invokedynamic")
    fun testExceptionInStart() {
        checkDiscoveredPropertiesWithExceptions(
            ThreadExamples::explicitExceptionInStart,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IllegalStateException>() }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testChangingCollectionInThread() {
        checkDiscoveredProperties(
            ThreadExamples::changingCollectionInThread,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == 42 },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testChangingCollectionInThreadWithoutStart() {
        checkDiscoveredPropertiesWithExceptions(
            ThreadExamples::changingCollectionInThreadWithoutStart,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IndexOutOfBoundsException>() },
        )
    }
}
