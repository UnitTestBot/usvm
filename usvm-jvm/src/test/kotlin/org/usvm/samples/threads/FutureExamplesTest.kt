package org.usvm.samples.threads

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
        checkWithExceptionExecutionMatches(
            FutureExamples::throwingRunnableExample,
            eq(1),
            { _, r -> r.isException<ExecutionException>() },
        )
    }

    @Test
    fun testResultFromGet() {
        checkExecutionMatches(
            FutureExamples::resultFromGet,
            eq(1),
            { _, r -> r == 42 },
        )
    }

    @Test
    fun testChangingCollectionInFuture() {
        checkExecutionMatches(
            FutureExamples::changingCollectionInFuture,
            eq(1),
            { _, r -> r == 42 },
        )
    }

    @Test
    fun testChangingCollectionInFutureWithoutGet() {
        checkExecutionMatches(
            FutureExamples::changingCollectionInFutureWithoutGet,
            eq(1),
            { _, r -> r == 42 },
        )
    }
}
