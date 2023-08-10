package org.usvm.samples.structures

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import java.util.LinkedList
import java.util.TreeMap

internal class StandardStructuresTest : JavaMethodTestRunner() {
    @Test
    fun testGetList() {
        checkDiscoveredProperties(
            StandardStructures::getList,
            eq(4),
            { _, l, r -> l is ArrayList && r is ArrayList },
            { _, l, r -> l is LinkedList && r is LinkedList },
            { _, l, r -> l == null && r == null },
            { _, l, r ->
                l !is ArrayList && l !is LinkedList && l != null && r !is ArrayList && r !is LinkedList && r != null
            },
        )
    }

    @Test
    fun testGetMap() {
        checkDiscoveredProperties(
            StandardStructures::getMap,
            eq(3),
            { _, m, r -> m is TreeMap && r is TreeMap },
            { _, m, r -> m == null && r == null },
            { _, m, r -> m !is TreeMap && m != null && r !is TreeMap && r != null },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testGetDeque() {
        checkDiscoveredProperties(
            StandardStructures::getDeque,
            eq(4),
            { _, d, r -> d is ArrayDeque<*> && r is ArrayDeque<*> },
            { _, d, r -> d is LinkedList && r is LinkedList },
            { _, d, r -> d == null && r == null },
            { _, d, r ->
                d !is ArrayDeque<*> && d !is LinkedList && d != null && r !is ArrayDeque<*> && r !is LinkedList && r != null
            },
        )
    }
}