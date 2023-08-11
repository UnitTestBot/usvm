package org.usvm.samples.threads

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.concurrent.ExecutionException

// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
class FutureExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testThrowingRunnable() = disableTest("Support invokedynamic") {
        checkDiscoveredPropertiesWithExceptions(
            FutureExamples::throwingRunnableExample,
            eq(1),
            { _, r -> r.isException<ExecutionException>() },
        )
    }

    @Test
    fun testResultFromGet() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            FutureExamples::resultFromGet,
            eq(1),
            { _, r -> r == 42 },
        )
    }

    @Test
    fun testChangingCollectionInFuture() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            FutureExamples::changingCollectionInFuture,
            eq(1),
            { _, r -> r == 42 },
        )
    }

    @Test
    fun testChangingCollectionInFutureWithoutGet() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            FutureExamples::changingCollectionInFutureWithoutGet,
            eq(1),
            { _, r -> r == 42 },
        )
    }
}
