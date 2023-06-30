package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


@Disabled("Unsupported")
internal class ListsPart1Test : JavaMethodTestRunner() {
    @Test
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