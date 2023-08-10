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
    @Disabled("Can't find method (id:1)java.lang.Thread#getThreadGroup() in type java.lang.Object")
    fun testExceptionInExecute() {
        checkDiscoveredPropertiesWithExceptions(
            ExecutorServiceExamples::throwingInExecute,
            ignoreNumberOfAnalysisResults,
            { _, r -> r.isException<IllegalStateException>() }
        )
    }

    @Test
    @Disabled("Can't find method (id:1)java.lang.Thread#getThreadGroup() in type java.lang.Object")
    fun testChangingCollectionInExecute() {
        checkDiscoveredProperties(
            ExecutorServiceExamples::changingCollectionInExecute,
            ignoreNumberOfAnalysisResults,
            { _, r -> r == 42 },
        )
    }
}
