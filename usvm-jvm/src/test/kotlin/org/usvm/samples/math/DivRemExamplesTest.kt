package org.usvm.samples.math

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class DivRemExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testDiv() {
        checkDiscoveredPropertiesWithExceptions(
            DivRemExamples::div,
            ignoreNumberOfAnalysisResults,
            { _, _, y, r -> y == 0 && r.isException<ArithmeticException>() },
            { _, x, y, r -> y != 0 && r.getOrNull() == x / y }
        )
    }

    @Test
    fun testRem() {
        checkDiscoveredPropertiesWithExceptions(
            DivRemExamples::rem,
            ignoreNumberOfAnalysisResults,
            { _, _, y, r -> y == 0 && r.isException<ArithmeticException>() },
            { _, x, y, r -> y != 0 && r.getOrNull() == x % y }
        )
    }

    @Test
    fun testRemPositiveConditional() {
        checkDiscoveredPropertiesWithExceptions(
            DivRemExamples::remPositiveConditional,
            ignoreNumberOfAnalysisResults,
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d != 0 && 11 % d == 2 && r.getOrNull() == true },
            { _, d, r -> d != 0 && 11 % d != 2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemNegativeConditional() {
        checkDiscoveredPropertiesWithExceptions(
            DivRemExamples::remNegativeConditional,
            ignoreNumberOfAnalysisResults,
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d != 0 && -11 % d == -2 && r.getOrNull() == true },
            { _, d, r -> d != 0 && -11 % d != -2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemWithConditions() {
        checkDiscoveredPropertiesWithExceptions(
            DivRemExamples::remWithConditions,
            ignoreNumberOfAnalysisResults,
            { _, d, r -> d < 0 && r.getOrNull() == false },
            { _, d, r -> d == 0 && r.isException<ArithmeticException>() },
            { _, d, r -> d > 0 && -11 % d == -2 && r.getOrNull() == true },
            { _, d, r -> d > 0 && -11 % d != -2 && r.getOrNull() == false }
        )
    }

    @Test
    fun testRemDoubles() {
        checkDiscoveredProperties(
            DivRemExamples::remDoubles,
            eq(1)
        )
    }

    @Test
    fun testRemDoubleInt() {
        checkDiscoveredProperties(
            DivRemExamples::remDoubleInt,
            eq(1)
        )
    }
}