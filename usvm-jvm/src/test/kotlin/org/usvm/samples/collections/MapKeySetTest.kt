package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException

class MapKeySetTest : JavaMethodTestRunner() {
    @Test
    fun testRemoveFromKeySet() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            MapKeySet::removeFromKeySet,
            ignoreNumberOfAnalysisResults,
            { _, map, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, i, result -> i !in map.keys && result.getOrNull() == map }, // one of these will be minimized
            { _, map, i, result -> // one of these will be minimized
                val resultMap = result.getOrNull()!!
                val mapKeysContainsI = i in map.keys
                val mapContainsAllKeysInResult = map.keys.containsAll(resultMap.keys)
                val resultDoesntContainI = resultMap.keys.size == map.keys.size - 1 && i !in resultMap.keys
                mapKeysContainsI && mapContainsAllKeysInResult && resultDoesntContainI
            },
        )
    }

    @Test
    fun testAddToKeySet() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredPropertiesWithExceptions(
            MapKeySet::addToKeySet,
            between(2..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map != null && result.isException<UnsupportedOperationException>() },
        )
    }

    @Test
    fun testGetFromKeySet() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            MapKeySet::getFromKeySet, // branches with null keys may appear
            eq(3),
            { _, map, _, _ -> map == null },
            { _, map, i, result -> i !in map && result == 1 }, // one of these will be minimized
            { _, map, i, result -> i in map && result == 1 }, // one of these will be minimized
        )
    }

    @Test
    fun testIteratorHasNext() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            MapKeySet::iteratorHasNext,
            between(3..4),
            { _, map, _ -> map == null },
            { _, map, result -> map.keys.isEmpty() && result == 0 },
            { _, map, result -> map.keys.isNotEmpty() && result == map.keys.size },
        )
    }

    @Test
    fun testIteratorNext() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            MapKeySet::iteratorNext,
            between(3..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map.keys.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, result -> map.keys.isNotEmpty() && result.getOrNull() == map.keys.first() },
        )
    }

    @Test
    fun testIteratorRemove() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            MapKeySet::iteratorRemove,
            between(3..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map.keys.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, result ->
                val resultMap = result.getOrNull()!!
                val mapContainsAllKeysInResult = map.keys.isNotEmpty() && map.keys.containsAll(resultMap.keys)
                val resultDoesntContainFirstKey =
                    resultMap.keys.size == map.keys.size - 1 && map.keys.first() !in resultMap.keys
                mapContainsAllKeysInResult && resultDoesntContainFirstKey
            },
        )
    }

    @Test
    fun testIteratorRemoveOnIndex() = disableTest("Some properties were not discovered at positions (from 0): [2, 3, 4]") {
        checkDiscoveredPropertiesWithExceptions(
            MapKeySet::iteratorRemoveOnIndex,
            ge(5),
            { _, _, i, result -> i == 0 && result.isSuccess && result.getOrNull() == null },
            { _, map, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, i, result -> map != null && i < 0 && result.isException<IllegalStateException>() },
            { _, map, i, result -> i > map.keys.size && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, i, result ->
                val resultMap = result.getOrNull()!!
                val iInIndexRange = i in 0..map.keys.size
                val mapContainsAllKeysInResult = map.keys.containsAll(resultMap.keys)
                val resultDoesntContainIthKey = map.keys.toList()[i - 1] !in resultMap.keys
                iInIndexRange && mapContainsAllKeysInResult && resultDoesntContainIthKey
            },
        )
    }

    @Test
    fun testIterateForEach() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            MapKeySet::iterateForEach,
            ignoreNumberOfAnalysisResults,
            { _, map, _ -> map == null },
            { _, map, _ -> map != null && null in map.keys },
            { _, map, result -> map != null && result == map.keys.sum() },
        )
    }

    @Test
    fun testIterateWithIterator() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            MapKeySet::iterateWithIterator,
            ignoreNumberOfAnalysisResults,
            { _, map, _ -> map == null },
            { _, map, _ -> map != null && null in map.keys },
            { _, map, result -> map != null && result == map.keys.sum() },
        )
    }

    @Test
    fun testNullKey() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            MapKeySet::nullKey,
            eq(3),
            { _, map, _ -> map == null },
            { _, map, result -> map != null && null in map.keys && map[null] == result },
            { _, map, _ -> map != null && null !in map.keys }
        )
    }
}