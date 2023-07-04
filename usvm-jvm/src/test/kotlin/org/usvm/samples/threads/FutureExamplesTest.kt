package org.usvm.samples.threads

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException
import java.util.concurrent.ExecutionException

// IMPORTANT: most of the these tests test only the symbolic engine
// and should not be used for testing conrete or code generation since they are possibly flaky in the concrete execution
// (see https://github.com/UnitTestBot/UTBotJava/issues/1610)
class FutureExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testThrowingRunnable() {
        checkDiscoveredPropertiesWithExceptions(
            FutureExamples::throwingRunnableExample,
            eq(1),
            { _, r -> r.isException<ExecutionException>() },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testResultFromGet() {
        checkDiscoveredProperties(
            FutureExamples::resultFromGet,
            eq(1),
            { _, r -> r == 42 },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testChangingCollectionInFuture() {
        checkDiscoveredProperties(
            FutureExamples::changingCollectionInFuture,
            eq(1),
            { _, r -> r == 42 },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testChangingCollectionInFutureWithoutGet() {
        checkDiscoveredProperties(
            FutureExamples::changingCollectionInFutureWithoutGet,
            eq(1),
            { _, r -> r == 42 },
        )
    }
}
