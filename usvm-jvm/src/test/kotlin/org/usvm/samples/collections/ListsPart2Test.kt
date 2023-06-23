package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


@Disabled("Unsupported")
internal class ListsPart2Test : JavaMethodTestRunner() {
    @Test
    fun testCollectionContains() {
        checkDiscoveredProperties(
            Lists::collectionContains,
            ignoreNumberOfAnalysisResults,
            { _, collection, _ -> collection == null },
            { _, collection, r -> 1 in collection && r != null && r },
            { _, collection, r -> 1 !in collection && r != null && !r },
        )
    }
}