package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException

class MapEntrySetTest : JavaMethodTestRunner() {
    @Test
    fun testRemoveFromEntrySet() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            MapEntrySet::removeFromEntrySet,
            between(3..7),
            { _, map, _, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, i, j, result -> map.entries.none { it.key == i && it.value == j } && result.getOrNull() == map },
            { _, map, i, j, result ->
                val resultMap = result.getOrNull()!!
                val mapContainsIJ = map.entries.any { it.key == i && it.value == j }
                val mapContainsAllEntriesFromResult = map.entries.containsAll(resultMap.entries)
                val resultDoesntContainIJ =
                    resultMap.entries.size == map.entries.size - 1 && resultMap.entries.none { it.key == i && it.value == j }
                mapContainsIJ && mapContainsAllEntriesFromResult && resultDoesntContainIJ
            },
        )
    }

    @Test
    fun testAddToEntrySet() = disableTest("Expected number of executions in bounds 2..4, but 21 found") {
        checkDiscoveredPropertiesWithExceptions(
            MapEntrySet::addToEntrySet,
            between(2..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map != null && result.isException<UnsupportedOperationException>() },
        )
    }

    @Test
    fun testGetFromEntrySet() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            MapEntrySet::getFromEntrySet,
            between(3..7),
            { _, map, _, _, _ -> map == null },
            { _, map, i, j, result -> map.none { it.key == i && it.value == j } && result == 1 },
            { _, map, i, j, result -> map.any { it.key == i && it.value == j } && result == 1 },
        )
    }

    @Test
    fun testIteratorHasNext() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            MapEntrySet::iteratorHasNext,
            between(3..4),
            { _, map, _ -> map == null },
            { _, map, result -> map.entries.isEmpty() && result == 0 },
            { _, map, result -> map.entries.isNotEmpty() && result == map.entries.size },
        )
    }

    @Test
    fun testIteratorNext() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            MapEntrySet::iteratorNext,
            between(3..5),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map.entries.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, result ->
                val resultEntry = result.getOrNull()!!
                val (entryKey, entryValue) = map.entries.first()
                val (resultKey, resultValue) = resultEntry
                map.entries.isNotEmpty() && entryKey == resultKey && entryValue == resultValue
            },
        )
    }

    @Test
    fun testIteratorRemove() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            MapEntrySet::iteratorRemove,
            between(3..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map.entries.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, result ->
                val resultMap = result.getOrNull()!!
                val mapContainsAllEntriesInResult = map.entries.containsAll(resultMap.entries)
                val resultDoesntContainFirstEntry =
                    resultMap.entries.size == map.entries.size - 1 && map.entries.first() !in resultMap.entries
                map.entries.isNotEmpty() && mapContainsAllEntriesInResult && resultDoesntContainFirstEntry
            },
        )
    }

    @Test
    fun testIteratorRemoveOnIndex() = disableTest("Some properties were not discovered at positions (from 0): [4]") {
        checkDiscoveredPropertiesWithExceptions(
            MapEntrySet::iteratorRemoveOnIndex,
            ge(5),
            { _, _, i, result -> i == 0 && result.isSuccess && result.getOrNull() == null },
            { _, map, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, i, result -> map != null && i < 0 && result.isException<IllegalStateException>() },
            { _, map, i, result -> i > map.entries.size && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, i, result ->
                val resultMap = result.getOrNull()!!
                val iInIndexRange = i in 0..map.entries.size
                val mapContainsAllEntriesInResult = map.entries.containsAll(resultMap.entries)
                val resultDoesntContainIthEntry = map.entries.toList()[i - 1] !in resultMap.entries
                iInIndexRange && mapContainsAllEntriesInResult && resultDoesntContainIthEntry
            },
        )
    }

    @Test
    fun testIterateForEach() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            MapEntrySet::iterateForEach,
            between(3..5),
            { _, map, _ -> map == null },
            { _, map, _ -> null in map.values },
            { _, map, result -> result != null && result[0] == map.keys.sum() && result[1] == map.values.sum() },
        )
    }


    @Test
    fun testIterateWithIterator() = disableTest("Some properties were not discovered at positions (from 0): [2, 3, 4, 5]") {
        checkDiscoveredPropertiesWithExceptions(
            MapEntrySet::iterateWithIterator,
            ignoreNumberOfAnalysisResults,
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map.isEmpty() && result.getOrThrow().contentEquals(intArrayOf(0, 0)) },
            { _, map, result -> map.size % 2 == 1 && result.isException<NoSuchElementException>() },
            { _, map, result ->
                val evenEntryHasNullKey = map.keys.indexOf(null) % 2 == 0
                evenEntryHasNullKey && result.isException<NullPointerException>()
            },
            { _, map, result ->
                val twoElementsOrMore = map.size > 1
                val oddEntryHasNullKey = map.values.indexOf(null) % 2 == 1
                twoElementsOrMore && oddEntryHasNullKey && result.isException<NullPointerException>()
            },
            { _, map, result ->
                val mapIsNotEmptyAndSizeIsEven = map != null && map.isNotEmpty() && map.size % 2 == 0
                val arrayResult = result.getOrThrow()
                val evenKeysSum = map.keys.withIndex().filter { it.index % 2 == 0 }.sumOf { it.value }
                val oddValuesSum = map.values.withIndex().filter { it.index % 2 == 0 }.sumOf { it.value }
                mapIsNotEmptyAndSizeIsEven && arrayResult[0] == evenKeysSum && arrayResult[1] == oddValuesSum
            },
        )
    }
}