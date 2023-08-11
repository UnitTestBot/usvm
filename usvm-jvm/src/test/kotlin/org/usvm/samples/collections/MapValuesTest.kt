package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class MapValuesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testRemoveFromValues() {
        checkDiscoveredPropertiesWithExceptions(
            MapValues::removeFromValues,
            ignoreNumberOfAnalysisResults,
            { _, map, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, i, result -> i !in map.values && result.getOrNull() == map },
            { _, map, i, result ->
                val resultMap = result.getOrNull()!!

                val iInMapValues = i in map.values
                val iWasDeletedFromValues =
                    resultMap.values.filter { it == i }.size == map.values.filter { it == i }.size - 1

                val firstKeyAssociatedWithI = map.keys.first { map[it] == i }
                val firstKeyAssociatedWIthIWasDeleted = firstKeyAssociatedWithI !in resultMap.keys

                val getCountExceptI: Collection<Int?>.() -> Map<Int, Int> =
                    { this.filter { it != i }.filterNotNull().groupingBy { it }.eachCount() }
                val mapContainsAllValuesFromResult =
                    map.values.getCountExceptI() == resultMap.values.getCountExceptI()

                iInMapValues && iWasDeletedFromValues && firstKeyAssociatedWIthIWasDeleted && mapContainsAllValuesFromResult
            },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1]")
    fun testAddToValues() {
        checkDiscoveredPropertiesWithExceptions(
            MapValues::addToValues,
            between(2..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map != null && result.isException<UnsupportedOperationException>() },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testGetFromValues() {
        checkDiscoveredProperties(
            MapValues::getFromValues,
            ignoreNumberOfAnalysisResults,
            { _, map, _, _ -> map == null },
            { _, map, i, result -> i !in map.values && result == 1 },
            { _, map, i, result -> i in map.values && result == 1 },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testIteratorHasNext() {
        checkDiscoveredProperties(
            MapValues::iteratorHasNext,
            between(3..4),
            { _, map, _ -> map == null },
            { _, map, result -> map.values.isEmpty() && result == 0 },
            { _, map, result -> map.values.isNotEmpty() && result == map.values.size },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testIteratorNext() {
        checkDiscoveredPropertiesWithExceptions(
            MapValues::iteratorNext,
            between(3..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            // We might lose this branch depending on the order of the exploration since
            // we do not register wrappers, and, therefore, do not try to cover all of their branches
            // { _, map, result -> map != null && map.values.isEmpty() && result.isException<NoSuchElementException>() },
            { _, map, result -> map != null && map.values.first() == null && result.isException<NullPointerException>() },
            // as map is LinkedHashmap by default this matcher would be correct
            { _, map, result -> map != null && map.values.isNotEmpty() && result.getOrNull() == map.values.first() },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testIteratorRemove() {
        checkDiscoveredPropertiesWithExceptions(
            MapValues::iteratorRemove,
            between(3..4),
            { _, map, result -> map == null && result.isException<NullPointerException>() },
            { _, map, result -> map.values.isEmpty() && result.isException<NoSuchElementException>() },
            // test should work as long as default class for map is LinkedHashMap
            { _, map, result ->
                val resultMap = result.getOrNull()!!
                val firstValue = map.values.first()

                val getCountsExceptFirstValue: Collection<Int?>.() -> Map<Int, Int> =
                    { this.filter { it != firstValue }.filterNotNull().groupingBy { it }.eachCount() }
                val mapContainsAllValuesFromResult =
                    map.values.getCountsExceptFirstValue() == resultMap.values.getCountsExceptFirstValue()

                val firstValueWasDeleted =
                    resultMap.values.filter { it == firstValue }.size == map.values.filter { it == firstValue }.size - 1

                val keyAssociatedWithFirstValueWasDeleted =
                    map.keys.first { map[it] == firstValue } !in resultMap.keys

                mapContainsAllValuesFromResult && firstValueWasDeleted && keyAssociatedWithFirstValueWasDeleted
            },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [2, 3, 4]")
    fun testIteratorRemoveOnIndex() {
        checkDiscoveredPropertiesWithExceptions(
            MapValues::iteratorRemoveOnIndex,
            ge(5),
            { _, _, i, result -> i == 0 && result.isSuccess && result.getOrNull() == null },
            { _, map, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, i, result -> map != null && i < 0 && result.isException<IllegalStateException>() },
            { _, map, i, result -> i > map.values.size && result.isException<NoSuchElementException>() },
            { _, map, i, result ->
                val iInIndexRange = i in 1..map.size
                val ithValue = map.values.toList()[i - 1]
                val resultMap = result.getOrNull()!!

                val getCountsExceptIthValue: Collection<Int?>.() -> Map<Int, Int> =
                    { this.filter { it != ithValue }.filterNotNull().groupingBy { it }.eachCount() }
                val mapContainsAllValuesFromResult =
                    map.values.getCountsExceptIthValue() == resultMap.values.getCountsExceptIthValue()
                val ithValueWasDeleted =
                    resultMap.values.filter { it == ithValue }.size == map.values.filter { it == ithValue }.size - 1
                val keyAssociatedWIthIthValueWasDeleted =
                    map.keys.filter { map[it] == ithValue }.any { it !in resultMap.keys }

                iInIndexRange && mapContainsAllValuesFromResult && ithValueWasDeleted && keyAssociatedWIthIthValueWasDeleted
            },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testIterateForEach() {
        checkDiscoveredProperties(
            MapValues::iterateForEach,
            between(3..5),
            { _, map, _ -> map == null },
            { _, map, _ -> null in map.values },
            { _, map, result -> map != null && result == map.values.sum() },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun testIterateWithIterator() {
        checkDiscoveredProperties(
            MapValues::iterateWithIterator,
            between(3..5),
            { _, map, _ -> map == null },
            { _, map, _ -> null in map.values },
            { _, map, result -> map != null && result == map.values.sum() },
        )
    }
}