package org.usvm.samples.symbolic

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

internal class SymbolicAssumptionsTest : JavaMethodTestRunner() {
    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    @Test
    fun testRejectedSymbolicAssumptionStopsOnlyItsState() {
        checkDiscoveredProperties(
            SymbolicAssumptions::rejectNegativeSymbolicInput,
            eq(2),
            { _, instance, _, result -> instance == null && result == 0 },
            { _, instance, value, result -> instance != null && value >= 0 && result == 1 },
            invariants = arrayOf({ _, _, _, result -> result != -1 }),
        )
    }

    @Test
    fun testConcreteInputDoesNotApplySymbolicAssumption() {
        checkDiscoveredProperties(
            SymbolicAssumptions::ignoreConcreteInput,
            eq(1),
            { _, result -> result == 1 },
        )
    }

    @Test
    fun testNullInputDoesNotApplySymbolicAssumption() {
        checkDiscoveredProperties(
            SymbolicAssumptions::ignoreNullInput,
            eq(1),
            { _, result -> result == 1 },
        )
    }

    @Test
    fun testGuardedConcreteAlternativeSurvivesRejectedSymbolicAssumption() {
        checkDiscoveredProperties(
            SymbolicAssumptions::selectSymbolicOrConcreteInput,
            ignoreNumberOfAnalysisResults,
            { _, _, _, result -> result == 0 },
            { _, instance, index, result -> instance != null && index == 1 && result == 2 },
            invariants = arrayOf({ _, _, _, result -> result != 1 }),
        )
    }
}
