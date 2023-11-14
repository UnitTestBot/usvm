package org.usvm.samples.approximations

import approximations.java.util.ArrayList_Tests
import approximations.java.util.OptionalDouble_Tests
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class ApproximationsTest : ApproximationsTestRunner() {
    @Test
    fun testOptionalDouble() {
        checkDiscoveredPropertiesWithExceptions(
            OptionalDouble_Tests::test_of_0,
            eq(1),
            invariants = arrayOf(
                { execution, r -> r.getOrThrow() == execution }
            )
        )
    }

    @Test
    fun testArrayList() {
        checkDiscoveredProperties(
            ArrayList_Tests::test_get_0,
            ignoreNumberOfAnalysisResults,
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            { o, _ -> o == 2 },
            { o, _ -> o == 3 },
            { o, _ -> o == 4 },
            invariants = arrayOf(
                { execution, r -> execution !in 0..4 || r == execution }
            )
        )
    }
}
