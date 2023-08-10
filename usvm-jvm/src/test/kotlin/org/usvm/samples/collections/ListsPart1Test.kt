package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class ListsPart1Test : JavaMethodTestRunner() {
    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testIterableContains() {
        checkDiscoveredProperties(
            Lists::iterableContains,
            ignoreNumberOfAnalysisResults,
            { _, iterable, _ -> iterable == null },
            { _, iterable, r -> 1 in iterable && r != null && r },
            { _, iterable, r -> 1 !in iterable && r != null && !r },
        )
    }
}