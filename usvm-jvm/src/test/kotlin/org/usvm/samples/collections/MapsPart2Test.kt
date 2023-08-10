package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge
import org.usvm.util.isException

internal class MapsPart2Test : JavaMethodTestRunner() {
    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2, 3, 4, 5]")
    fun testReplaceEntryWithValue() {
        checkDiscoveredProperties(
            Maps::replaceEntryWithValue,
            ge(6),
            { _, map, _, _, _ -> map == null },
            { _, map, key, value, result -> key !in map && value !in map.values && result == 0 },
            { _, map, key, value, result -> key in map && value !in map.values && result == -1 },
            { _, map, key, value, result -> key !in map && value in map.values && result == -2 },
            { _, map, key, value, result -> key in map && map[key] == value && result == 3 },
            { _, map, key, value, result -> key in map && value in map.values && map[key] != value && result == -3 },
        )
    }

    @Test
    @Disabled("Index 1 out of bounds for length 1")
    fun testMerge() {
        checkDiscoveredPropertiesWithExceptions(
            Maps::merge,
            ge(5),
            { _, map, _, _, result -> map == null && result.isException<NullPointerException>() },
            { _, map, _, value, result -> map != null && value == null && result.isException<NullPointerException>() },
            { _, map, key, value, result ->
                val resultMap = result.getOrNull()!!
                val entryWasPut = resultMap.entries.all { it.key == key && it.value == value || it in map.entries }
                key !in map && value != null && entryWasPut
            },
            { _, map, key, value, result ->
                val resultMap = result.getOrNull()!!
                val valueInMapIsNull = key in map && map[key] == null
                val valueWasReplaced = resultMap[key] == value
                val otherValuesWerentTouched = resultMap.entries.all { it.key == key || it in map.entries }
                value != null && valueInMapIsNull && valueWasReplaced && otherValuesWerentTouched
            },
            { _, map, key, value, result ->
                val resultMap = result.getOrNull()!!
                val valueInMapIsNotNull = map[key] != null
                val valueWasMerged = resultMap[key] == map[key]!! + value
                val otherValuesWerentTouched = resultMap.entries.all { it.key == key || it in map.entries }
                value != null && valueInMapIsNotNull && valueWasMerged && otherValuesWerentTouched
            },
        )
    }

    fun testPutAllEntries() {
        checkDiscoveredProperties(
            Maps::putAllEntries,
            ge(5),
            { _, map, _, _ -> map == null },
            { _, map, other, _ -> map != null && other == null },
            { _, map, other, result -> map != null && other != null && map.keys.containsAll(other.keys) && result == 0 },
            { _, map, other, result -> map != null && other != null && other.keys.all { it !in map.keys } && result == 1 },
            { _, map, other, result ->
                val notNull = map != null && other != null
                val mapContainsAtLeastOneKeyOfOther = other.keys.any { it in map.keys }
                val mapDoesNotContainAllKeysOfOther = !map.keys.containsAll(other.keys)
                notNull && mapContainsAtLeastOneKeyOfOther && mapDoesNotContainAllKeysOfOther && result == 2
            },
        )
    }
}