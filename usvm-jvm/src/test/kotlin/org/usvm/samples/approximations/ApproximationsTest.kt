package org.usvm.samples.approximations

import approximations.java.util.ArrayList_Tests
import approximations.java.util.OptionalDouble_Tests
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class ApproximationsTest : ApproximationsTestRunner() {
    @Test
    fun testOptionalDouble() {
        with(FixedExecutionVerifier(1)) {
            checkDiscoveredPropertiesWithExceptions(
                OptionalDouble_Tests::test_of_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testArrayList() {
        with(FixedExecutionVerifier(5, exceptionalExecutions = setOf(0))) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayList_Tests::test_get_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> o == 0 && onExecution(0, r.isException<IndexOutOfBoundsException>()) },
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }
}
