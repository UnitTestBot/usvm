package org.usvm.samples.invokes

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


import kotlin.math.ln
import kotlin.math.sqrt

internal class NativeExampleTest : JavaMethodTestRunner() {
    @Test
    fun testPartialEx() {
        checkExecutionMatches(
            NativeExample::partialExecution,
        )
    }

    @Test
    fun testUnreachableNativeCall() {
        checkExecutionMatches(
            NativeExample::unreachableNativeCall,
            { _, d, r -> !d.isNaN() && r == 1 },
            { _, d, r -> d.isNaN() && r == 2 },
        )
    }

    @Test
    @Tag("slow")
    fun testSubstitution() {
        checkExecutionMatches(
            NativeExample::substitution,
            { _, x, r -> x > 4 && r == 1 },
            { _, x, r -> sqrt(x) <= 2 && r == 0 }
        )
    }

    @Test
    fun testUnreachableBranch() {
        checkExecutionMatches(
            NativeExample::unreachableBranch,
            { _, x, r -> x.isNaN() && r == 1 },
            { _, x, r -> (!ln(x).isNaN() || x < 0) && r == 2 },
        )
    }
}