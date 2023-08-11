package org.usvm.samples.invokes


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import kotlin.math.ln
import kotlin.math.sqrt

internal class NativeExampleTest : JavaMethodTestRunner() {
    @Test
    fun testPartialEx() {
        checkDiscoveredProperties(
            NativeExample::partialExecution,
            ge(1),
        )
    }

    @Test
    fun testUnreachableNativeCall() {
        checkDiscoveredProperties(
            NativeExample::unreachableNativeCall,
            eq(2),
            { _, d, r -> !d.isNaN() && r == 1 },
            { _, d, r -> d.isNaN() && r == 2 },
        )
    }

    @Test
    fun testSubstitution() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            NativeExample::substitution,
            ignoreNumberOfAnalysisResults,
            { _, x, r -> x > 4 && r == 1 },
            { _, x, r -> sqrt(x) <= 2 && r == 0 }
        )
    }

    @Test
    fun testUnreachableBranch() {
        checkDiscoveredProperties(
            NativeExample::unreachableBranch,
            ge(2),
            { _, x, r -> x.isNaN() && r == 1 },
            { _, x, r -> (!ln(x).isNaN() || x < 0) && r == 2 },
        )
    }
}