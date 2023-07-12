package org.usvm.samples.threads

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
// (see https://github.com/UnitTestBot/UTBotJava/issues/1610)
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testChangingCollectionInThread() {
        checkDiscoveredProperties(
            ThreadExamples::changingCollectionInThread,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == 42 },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testChangingCollectionInThreadWithoutStart() {
        checkDiscoveredPropertiesWithExceptions(
            ThreadExamples::changingCollectionInThreadWithoutStart,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IndexOutOfBoundsException>() },
        )
    }
}
