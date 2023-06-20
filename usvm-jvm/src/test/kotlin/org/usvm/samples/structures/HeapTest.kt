package org.usvm.samples.structures

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class HeapTest : JavaMethodTestRunner() {
    @Test
    fun testIsHeap() {
        val method = Heap::isHeap
        this.checkDiscoveredProperties(
            method,
            ignoreNumberOfAnalysisResults,
            { values, _ -> values == null },
            { values, _ -> values.size < 3 },
            { values, r -> values.size >= 3 && r == method(values) },
            { values, r -> values.size >= 3 && values[1] < values[0] && r == method(values) },
            { values, r -> values.size >= 3 && values[1] >= values[0] && values[2] < values[0] && r == method(values) },
            { values, r -> values.size >= 3 && r == method(values) },
        )
    }
}