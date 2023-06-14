package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.state.WrappedException
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestOneDimensional : JavaMethodTestRunner() {
    @Test
    fun `Test sumOf`() {
        checkWithExceptionExecutionMatches(
            OneDimensional::sumOf,
            ignoreNumberOfAnalysisResults,
            { _, arr, r -> arr == null && r.exceptionOrNull() is NullPointerException },
            { _, arr, r -> arr != null && arr.all { it >= 0 } && r.getOrNull()?.let { it >= 0 } ?: false },
            { _, arr, r -> arr != null && arr.all { it <= 0} && r.exceptionOrNull() is WrappedException}
        )
    }
}
