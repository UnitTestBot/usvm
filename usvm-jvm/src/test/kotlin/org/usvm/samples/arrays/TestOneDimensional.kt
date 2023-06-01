package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.TestRunner
import org.usvm.state.WrappedException

class TestOneDimensional : TestRunner() {
    @Test
    fun `Test sumOf`() {
        runWithException<OneDimensional, IntArray?, Int>(OneDimensional::sumOf,
            { _, arr, r -> arr == null && r.exceptionOrNull() is NullPointerException },
            { _, arr, r -> arr != null && arr.all { it >= 0 } && r.getOrNull()?.let { it >= 0 } ?: false },
            { _, arr, r -> arr != null && arr.all { it <= 0} && r.exceptionOrNull() is WrappedException}
        )
    }
}
