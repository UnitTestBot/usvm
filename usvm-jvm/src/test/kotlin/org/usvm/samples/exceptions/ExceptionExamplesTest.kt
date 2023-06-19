package org.usvm.samples.exceptions

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class ExceptionExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testInitAnArray() {
        checkExecutionMatches(
            ExceptionExamples::initAnArray,
            { _, n, r -> n < 0 && r == -2 },
            { _, n, r -> n == 0 || n == 1 && r == -3 },
            { _, n, r -> n > 1 && r == 2 * n + 3 }
        )
    }

    @Test
    fun testNestedExceptions() {
        checkExecutionMatches(
            ExceptionExamples::nestedExceptions,
            { _, i, r -> i < 0 && r == -100 },
            { _, i, r -> i > 0 && r == 100 },
            { _, i, r -> i == 0 && r == 0 },
        )
    }

    @Test
    fun testDoNotCatchNested() {
        checkWithExceptionExecutionMatches(
            ExceptionExamples::doNotCatchNested,
            { _, i, r -> i < 0 && r.isException<IllegalArgumentException>() },
            { _, i, r -> i > 0 && r.isException<NullPointerException>() },
            { _, i, r -> i == 0 && r.getOrThrow() == 0 },
        )
    }

    @Test
    fun testFinallyThrowing() {
        checkWithExceptionExecutionMatches(
            ExceptionExamples::finallyThrowing,
            { _, i, r -> i <= 0 && r.isException<IllegalStateException>() },
            { _, i, r -> i > 0 && r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testFinallyChanging() {
        checkExecutionMatches(
            ExceptionExamples::finallyChanging,
            { _, i, r -> i * 2 <= 0 && r == i * 2 + 10 },
            { _, i, r -> i * 2 > 0 && r == i * 2 + 110 } // differs from JaCoCo
        )
    }

    @Test
    fun testThrowException() {
        checkWithExceptionExecutionMatches(
            ExceptionExamples::throwException,
            { _, i, r -> i <= 0 && r.getOrNull() == 101 },
            { _, i, r -> i > 0 && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCreateException() {
        checkExecutionMatches(
            ExceptionExamples::createException,
            { _, r -> r is java.lang.IllegalArgumentException },
        )
    }

    /**
     * Used for path generation check in [org.utbot.engine.Traverser.fullPath]
     */
    @Test
    fun testCatchDeepNestedThrow() {
        checkWithExceptionExecutionMatches(
            ExceptionExamples::catchDeepNestedThrow,
            { _, i, r -> i < 0 && r.isException<NullPointerException>() },
            { _, i, r -> i >= 0 && r.getOrThrow() == i },
        )
    }

    /**
     * Covers [#656](https://github.com/UnitTestBot/UTBotJava/issues/656).
     */
    @Test
    fun testCatchExceptionAfterOtherPossibleException() {
        checkWithExceptionExecutionMatches(
            ExceptionExamples::catchExceptionAfterOtherPossibleException,
            { _, i, r -> i == -1 && r.isException<ArithmeticException>() },
            { _, i, r -> i == 0 && r.getOrThrow() == 2 },
            { _, _, r -> r.getOrThrow() == 1 },
        )
    }

    /**
     * Used for path generation check in [org.utbot.engine.Traverser.fullPath]
     */
    @Test
    fun testDontCatchDeepNestedThrow() {
        checkWithExceptionExecutionMatches(
            ExceptionExamples::dontCatchDeepNestedThrow,
            { _, i, r -> i < 0 && r.isException<IllegalArgumentException>() },
            { _, i, r -> i >= 0 && r.getOrThrow() == i },
        )
    }
}