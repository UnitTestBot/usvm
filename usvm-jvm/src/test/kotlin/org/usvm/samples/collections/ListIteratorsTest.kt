package org.usvm.samples.collections


import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import kotlin.math.min
import kotlin.test.Test

internal class ListIteratorsTest : JavaMethodTestRunner() {
    @Test
    fun testReturnIterator() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            ListIterators::returnIterator,
            ignoreNumberOfAnalysisResults,
            { _, l, r -> l.isEmpty() && r != null && r.asSequence().toList().isEmpty() },
            { _, l, r -> l.isNotEmpty() && r != null && r.asSequence().toList() == l },
        )
    }

    @Test
    fun testReturnListIterator() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            ListIterators::returnListIterator,
            ignoreNumberOfAnalysisResults,
            { _, l, r -> l.isEmpty() && r != null && r.asSequence().toList().isEmpty() },
            { _, l, r -> l.isNotEmpty() && r != null && r.asSequence().toList() == l },
        )
    }

    @Test
    fun testIterate() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            ListIterators::iterate,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, result -> l.isEmpty() && result == l },
            { _, l, result -> l.isNotEmpty() && result == l },
        )
    }

    @Test
    fun testIterateReversed() = disableTest("Expected exactly 3 executions, but 152 found") {
        checkDiscoveredProperties(
            ListIterators::iterateReversed,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, result -> l.isEmpty() && result == l },
            { _, l, result -> l.isNotEmpty() && result == l.reversed() },
        )
    }

    @Test
    fun testIterateForEach() = disableTest("Expected exactly 4 executions, but 328 found") {
        checkDiscoveredProperties(
            ListIterators::iterateForEach,
            eq(4),
            { _, l, _ -> l == null },
            { _, l, result -> l.isEmpty() && result == 0 },
            { _, l, _ -> l.isNotEmpty() && l.any { it == null } },
            { _, l, result -> l.isNotEmpty() && result == l.sum() },
        )
    }

    @Test
    fun testAddElements() = disableTest("Some properties were not discovered: OutOfMemory") {
        checkDiscoveredProperties(
            ListIterators::addElements,
            eq(5),
            { _, l, _, _ -> l == null },
            { _, l, _, result -> l != null && l.isEmpty() && result == l },
            { _, l, arr, _ -> l != null && l.size > 0 && arr == null },
            { _, l, arr, _ -> l != null && arr != null && l.isNotEmpty() && arr.isEmpty() },
            { _, l, arr, _ -> l != null && arr != null && l.size > arr.size },
        )
    }

    @Test
    fun testSetElements() = disableTest("Expected exactly 5 executions, but 203 found") {
        checkDiscoveredProperties(
            ListIterators::setElements,
            eq(5),
            { _, l, _, _ -> l == null },
            { _, l, _, result -> l != null && l.isEmpty() && result == l },
            { _, l, arr, _ -> l != null && arr != null && l.size > arr.size },
            { _, l, arr, _ -> l != null && l.size > 0 && arr == null },
            { _, l, arr, result ->
                l != null && arr != null && l.size <= arr.size && result == arr.asList().take(l.size)
            },
        )
    }

    @Test
    fun testRemoveElements() = disableTest("Some properties were not discovered at positions (from 0): [6]") {
        checkDiscoveredProperties(
            ListIterators::removeElements, // the exact number of the executions depends on the decisions made by PathSelector
            // so we can have either six results or seven, depending on the [pathSelectorType]
            // from UtSettings
            ignoreNumberOfAnalysisResults,
            { _, l, _, _ -> l == null },
            { _, l, i, _ -> l != null && i <= 0 },
            { _, l, i, _ -> l != null && l.isEmpty() && i > 0 },
            { _, l, i, _ -> l != null && i > 0 && l.subList(0, min(i, l.size)).any { it !is Int } },
            { _, l, i, _ -> l != null && i > 0 && l.subList(0, min(i, l.size)).any { it == null } },
            { _, l, i, _ -> l != null && l.isNotEmpty() && i > 0 },
            { _, l, i, result ->
                require(l != null)

                val precondition = l.isNotEmpty() && i > 0 && l.subList(0, i).all { it is Int }
                val postcondition = result == (l.subList(0, i - 1) + l.subList(min(l.size, i), l.size))

                precondition && postcondition
            },
        )
    }
}