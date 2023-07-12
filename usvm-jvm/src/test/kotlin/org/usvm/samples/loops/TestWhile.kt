package org.usvm.samples.loops

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestWhile : JavaMethodTestRunner() {
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
            eq(3),
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
            { _, n, r -> n * (n + 1) / 2 == r },
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
}
