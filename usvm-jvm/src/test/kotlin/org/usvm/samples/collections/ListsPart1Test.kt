package org.usvm.samples.collections

import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import kotlin.test.Test


internal class ListsPart1Test : JavaMethodTestRunner() {
    @Test
    fun testIterableContains() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            Lists::iterableContains,
            ignoreNumberOfAnalysisResults,
            { _, iterable, _ -> iterable == null },
            { _, iterable, r -> 1 in iterable && r != null && r },
            { _, iterable, r -> 1 !in iterable && r != null && !r },
        )
    }
}