package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


// TODO failed Kotlin compilation ($ in names, generics) SAT-1220 SAT-1332
internal class MapsPart1Test : JavaMethodTestRunner() {
    @Test
    fun testPutElementIfAbsent() {
        checkDiscoveredProperties(
            Maps::putElementIfAbsent,
            ignoreNumberOfAnalysisResults,
            { _, map, _, _, _ -> map == null },
            { _, map, key, _, result -> map != null && key in map && result == map },
            { _, map, key, value, result ->
                val valueWasPut = result!![key] == value && result.size == map.size + 1
                val otherValuesWerentTouched = result.entries.containsAll(map.entries)
                key !in map && valueWasPut && otherValuesWerentTouched
            },
        )
    }

    @Test
    fun testReplaceEntry() {
        checkDiscoveredProperties(
            Maps::replaceEntry,
            between(3..6),
            { _, map, _, _, _ -> map == null },
            { _, map, key, _, result -> key !in map && result == map },
            { _, map, key, value, result ->
                val valueWasReplaced = result!![key] == value
                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
                key in map && valueWasReplaced && otherValuesWerentTouched
            },
        )
    }

    @Test
    fun createTest() {
        checkDiscoveredProperties(
            Maps::create,
            eq(5),
            { _, keys, _, _ -> keys == null },
            { _, keys, _, result -> keys.isEmpty() && result!!.isEmpty() },
            { _, keys, values, _ -> keys.isNotEmpty() && values == null },
            { _, keys, values, _ -> keys.isNotEmpty() && values.size < keys.size },
            { _, keys, values, result ->
                keys.isNotEmpty() && values.size >= keys.size &&
                        result!!.size == keys.size && keys.indices.all { result[keys[it]] == values[it] }
            },
        )
    }

    @Test
    fun testToString() {
        checkDiscoveredProperties(
            Maps::mapToString,
            eq(1),
            { _, a, b, c, r -> r == Maps().mapToString(a, b, c) }
        )
    }

    @Test
    fun testMapPutAndGet() {
        checkDiscoveredProperties(
            Maps::mapPutAndGet,
            eq(1),
            { _, r -> r == 3 }
        )
    }

    @Test
    fun testPutInMapFromParameters() {
        checkDiscoveredProperties(
            Maps::putInMapFromParameters,
            ignoreNumberOfAnalysisResults,
            { _, values, _ -> values == null },
            { _, values, r -> 1 in values.keys && r == 3 },
            { _, values, r -> 1 !in values.keys && r == 3 },
        )
    }

    // This test doesn't check anything specific, but the code from MUT
    // caused errors with NPE as results while debugging `testPutInMapFromParameters`.
    @Test
    fun testContainsKeyAndPuts() {
        checkDiscoveredProperties(
            Maps::containsKeyAndPuts,
            ignoreNumberOfAnalysisResults,
            { _, values, _ -> values == null },
            { _, values, r -> 1 !in values.keys && r == 3 },
        )
    }

    @Test
    fun testFindAllChars() {
        checkDiscoveredProperties(
            Maps::countChars,
            eq(3),
            { _, s, _ -> s == null },
            { _, s, result -> s == "" && result!!.isEmpty() },
            { _, s, result -> s != "" && result == s.groupingBy { it }.eachCount() },
        )
    }

    @Test
    fun putElementsTest() {
        checkDiscoveredProperties(
            Maps::putElements,
            ge(5),
            { _, map, _, _ -> map == null },
            { _, map, array, _ -> map != null && map.isNotEmpty() && array == null },
            { _, map, _, result -> map.isEmpty() && result == map },
            { _, map, array, result -> map.isNotEmpty() && array.isEmpty() && result == map },
            { _, map, array, result ->
                map.size >= 1 && array.isNotEmpty()
                        && result == map.toMutableMap().apply { putAll(array.map { it to it }) }
            },
        )
    }

    @Test
    fun removeEntries() {
        checkDiscoveredProperties(
            Maps::removeElements,
            ge(6),
            { _, map, _, _, _ -> map == null },
            { _, map, i, j, res -> map != null && (i !in map || map[i] == null) && (j !in map || map[j] == null) && res == -1 },
            { _, map, i, j, res -> map != null && map.isNotEmpty() && i !in map && j in map && res == 4 },
            { _, map, i, j, res -> map != null && map.isNotEmpty() && i in map && (j !in map || j == i) && res == 3 },
            { _, map, i, j, res -> map != null && map.size >= 2 && i in map && j in map && i > j && res == 2 },
            { _, map, i, j, res -> map != null && map.size >= 2 && i in map && j in map && i < j && res == 1 },
        )
    }

    @Test
    fun createWithDifferentTypeTest() {
        checkDiscoveredProperties(
            Maps::createWithDifferentType,
            eq(2),
            { _, seed, result -> seed % 2 != 0 && result is LinkedHashMap },
            { _, seed, result -> seed % 2 == 0 && result !is LinkedHashMap && result is HashMap },
        )
    }

    @Test
    fun removeCustomObjectTest() {
        checkDiscoveredProperties(
            Maps::removeCustomObject,
            ge(3),
            { _, map, _, _ -> map == null },
            { _, map, i, result -> (map.isEmpty() || CustomClass(i) !in map) && result == null },
            { _, map, i, result -> map.isNotEmpty() && CustomClass(i) in map && result == map[CustomClass(i)] },
        )
    }

    @Test
    fun testMapOperator() {
        checkDiscoveredProperties(
            Maps::mapOperator,
            ignoreNumberOfAnalysisResults
        )
    }

    @Test
    fun testComputeValue() {
        checkDiscoveredProperties(
            Maps::computeValue,
            between(3..5),
            { _, map, _, _ -> map == null },
            { _, map, key, result ->
                val valueWasUpdated = result!![key] == key + 1
                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
                map[key] == null && valueWasUpdated && otherValuesWerentTouched
            },
            { _, map, key, result ->
                val valueWasUpdated = result!![key] == map[key]!! + 1
                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
                map[key] != null && valueWasUpdated && otherValuesWerentTouched
            },
        )
    }

    // TODO unsupported

//    @Test
//    fun testComputeValueWithMocks() {
//        checkExecutionMatches(
//            Maps::computeValue,
//            between(3..5),
//            { _, map, _, _ -> map == null },
//            { _, map, key, result ->
//                val valueWasUpdated = result!![key] == key + 1
//                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
//                map[key] == null && valueWasUpdated && otherValuesWerentTouched
//            },
//            { _, map, key, result ->
//                val valueWasUpdated = result!![key] == map[key]!! + 1
//                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
//                map[key] != null && valueWasUpdated && otherValuesWerentTouched
//            },
//            mockStrategy = MockStrategyApi.OTHER_PACKAGES, // checks that we do not generate mocks for lambda classes
//        )
//    }

    @Test
    fun testComputeValueIfAbsent() {
        checkDiscoveredProperties(
            Maps::computeValueIfAbsent,
            between(3..5),
            { _, map, _, _ -> map == null },
            { _, map, key, result -> map[key] != null && result == map },
            { _, map, key, result ->
                val valueWasUpdated = result!![key] == key + 1
                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
                map[key] == null && valueWasUpdated && otherValuesWerentTouched
            },
        )
    }

    @Test
    fun testComputeValueIfPresent() {
        checkDiscoveredProperties(
            Maps::computeValueIfPresent,
            between(3..5),
            { _, map, _, _ -> map == null },
            { _, map, key, result -> map[key] == null && result == map },
            { _, map, key, result ->
                val valueWasUpdated = result!![key] == map[key]!! + 1
                val otherValuesWerentTouched = result.entries.all { it.key == key || it in map.entries }
                map[key] != null && valueWasUpdated && otherValuesWerentTouched
            },
        )
    }

    @Test
    fun testClearEntries() {
        checkDiscoveredProperties(
            Maps::clearEntries,
            between(3..4),
            { _, map, _ -> map == null },
            { _, map, result -> map.isEmpty() && result == 0 },
            { _, map, result -> map.isNotEmpty() && result == 1 },
        )
    }

    @Test
    fun testContainsKey() {
        checkDiscoveredProperties(
            Maps::containsKey,
            between(3..5),
            { _, map, _, _ -> map == null },
            { _, map, key, result -> key !in map && result == 0 },
            { _, map, key, result -> key in map && result == 1 },
        )
    }

    @Test
    fun testContainsValue() {
        checkDiscoveredProperties(
            Maps::containsValue,
            between(3..6),
            { _, map, _, _ -> map == null },
            { _, map, value, result -> value !in map.values && result == 0 },
            { _, map, value, result -> value in map.values && result == 1 },
        )
    }

    @Test
    fun testGetOrDefaultElement() {
        checkDiscoveredProperties(
            Maps::getOrDefaultElement,
            between(4..6),
            { _, map, _, _ -> map == null },
            { _, map, i, result -> i !in map && result == 1 },
            { _, map, i, result -> i in map && map[i] == null && result == 0 },
            { _, map, i, result -> i in map && map[i] != null && result == map[i] },
        )
    }

    @Test
    fun testRemoveKeyWithValue() {
        checkDiscoveredProperties(
            Maps::removeKeyWithValue,
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
    fun testReplaceAllEntries() {
        checkDiscoveredProperties(
            Maps::replaceAllEntries,
            between(5..6),
            { _, map, _ -> map == null },
            { _, map, result -> map.isEmpty() && result == null },
            { _, map, _ -> map.isNotEmpty() && map.containsValue(null) },
            { _, map, result ->
                val precondition = map.isNotEmpty() && !map.containsValue(null)
                val firstBranchInLambdaExists = map.entries.any { it.key > it.value }
                val valuesWereReplaced =
                    result == map.mapValues { if (it.key > it.value) it.value + 1 else it.value - 1 }
                precondition && firstBranchInLambdaExists && valuesWereReplaced
            },
            { _, map, result ->
                val precondition = map.isNotEmpty() && !map.containsValue(null)
                val secondBranchInLambdaExists = map.entries.any { it.key <= it.value }
                val valuesWereReplaced =
                    result == map.mapValues { if (it.key > it.value) it.value + 1 else it.value - 1 }
                precondition && secondBranchInLambdaExists && valuesWereReplaced
            },
        )
    }

    @Test
    fun testCreateMapWithString() {
        checkDiscoveredProperties(
            Maps::createMapWithString,
            eq(1),
            { _, r -> r!!.isEmpty() }
        )
    }

    @Test
    fun testCreateMapWithEnum() {
        checkDiscoveredProperties(
            Maps::createMapWithEnum,
            eq(1),
            { _, r -> r != null && r.size == 2 && r[Maps.WorkDays.Monday] == 112 && r[Maps.WorkDays.Friday] == 567 }
        )
    }
}