package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


@Disabled("Unsupported")
class ListAlgorithmsTest : JavaMethodTestRunner() {

    @Test
    fun testMergeLists() {
        checkDiscoveredProperties(
            ListAlgorithms::mergeListsInplace,
            eq(4),
            { _, a, b, r -> b.subList(0, b.size - 1).any { a.last() < it } && r != null && r == r.sorted() },
            { _, a, b, r -> (a.subList(0, a.size - 1).any { b.last() <= it } || a.any { ai -> b.any { ai < it } }) && r != null && r == r.sorted() },
            { _, a, b, r -> a[0] < b[0] && r != null && r == r.sorted() },
            { _, a, b, r -> a[0] >= b[0] && r != null && r == r.sorted() },
        )
    }
}