package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class TestOneDimensional : JavaMethodTestRunner() {
    @Test
    fun `Test sumOf`() {
        checkDiscoveredPropertiesWithExceptions(
            OneDimensional::sumOf,
            ignoreNumberOfAnalysisResults,
            { arr, r -> arr == null && r.exceptionOrNull() is NullPointerException },
            { arr, r -> arr != null && arr.all { it >= 0 } && r.getOrNull()?.let { it >= 0 } ?: false },
            { arr, r -> arr != null && arr.all { it >= 0 } && r.exceptionOrNull() is IllegalArgumentException }
        )
    }

    @Test
    fun `Test minus`() {
        checkDiscoveredPropertiesWithExceptions(
            OneDimensional::minus,
            ignoreNumberOfAnalysisResults,
            { a, _, r -> a == null && r.exceptionOrNull() is NullPointerException },
            { a, b, r -> a != null && b == null && r.exceptionOrNull() is NullPointerException },
            { a, b, r -> a != null && b != null && a.size > b.size && r.exceptionOrNull() is IndexOutOfBoundsException },
            { a, b, r ->
                val correctResult = (r
                    .getOrNull() ?: return@checkDiscoveredPropertiesWithExceptions false)
                    .withIndex()
                    .all { (idx, expr) -> expr == a[idx] - b[idx] }
                a != null && b != null && a.size <= b.size && correctResult
            }
        )
    }
}
