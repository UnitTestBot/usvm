package org.usvm.samples.loops

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestWhile : JavaMethodTestRunner() {

    // Increased limits for loop tests
    override var options: UMachineOptions = super.options.copy(
        stepLimit = 100_000UL,
        timeoutMs = 100_000,
        stepsFromLastCovered = 100_000,
    )

    @Test
    fun `Test singleLoop`() {
        checkDiscoveredProperties(
            While::singleLoop,
            eq(3),
            { _, n, r -> r == 0 && n >= 5 },
            { _, n, r -> r == 1 && n <= 0 },
            { _, n, r -> r == 2 && (n in 1..4) },
            coverageChecker = { _ -> true }
        )
    }

    @Test
    fun `Test smallestPowerOfTwo`() {
        checkDiscoveredProperties(
            While::smallestPowerOfTwo,
            ge(3),
            { _, n, r -> r == 0 && n.and(n - 1) == 0 },
            { _, n, r -> r == 1 && n <= 0 },
            { _, n, r -> r == 2 && n > 0 && n.and(n - 1) != 0 },
            coverageChecker = { _ -> true }
        )
    }

    @Test
    fun `Test sumOf`() {
        checkDiscoveredProperties(
            While::sumOf,
            ignoreNumberOfAnalysisResults,
            { _, n, r -> n * (n - 1) / 2 == r },
            coverageChecker = { _ -> true }
        )
    }

    @Test
    fun `Test while1000`() {
        checkDiscoveredProperties(
            While::while1000,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, r -> r == 1 },
            { _, _, _, _, r -> r == 2 },
        )
    }

    @Test
    fun `Test while1000 slow constraints`() {
        checkDiscoveredProperties(
            While::while1000slowConstraints,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, r -> r == 1 },
            { _, _, _, _, r -> r == 2 },
        )
    }
}
