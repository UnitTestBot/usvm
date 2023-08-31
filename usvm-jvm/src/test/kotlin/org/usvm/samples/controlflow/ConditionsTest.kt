package org.usvm.samples.controlflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class ConditionsTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleCondition() {
        checkDiscoveredProperties(
            Conditions::simpleCondition,
            eq(2),
            { _, condition, r -> !condition && r == 0 },
            { _, condition, r -> condition && r == 1 }
        )
    }

    @Test
    fun testIfLastStatement() {
        checkDiscoveredPropertiesWithExceptions(
            Conditions::emptyBranches,
            ignoreNumberOfAnalysisResults,
        )
    }
}
