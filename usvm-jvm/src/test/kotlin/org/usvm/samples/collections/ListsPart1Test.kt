package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


@Disabled
internal class ListsPart1Test : JavaMethodTestRunner() {
    @Test
    fun testIterableContains() {
        checkExecutionMatches(
            Lists::iterableContains,
            ignoreNumberOfAnalysisResults,
            { _, iterable, _ -> iterable == null },
            { _, iterable, r -> 1 in iterable && r },
            { _, iterable, r -> 1 !in iterable && !r },
        )
    }
}