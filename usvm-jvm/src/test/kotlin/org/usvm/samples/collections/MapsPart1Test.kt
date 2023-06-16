package org.usvm.samples.collections

import org.junit.jupiter.api.Tag
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
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
            Maps::mapToString,
            eq(1),
            { _, a, b, c, r -> r == Maps().mapToString(a, b, c) }
        )
    }

    @Test
    fun testMapPutAndGet() {
        checkExecutionMatches(
            Maps::mapPutAndGet,
            eq(1),
            { _, r -> r == 3 }
        )
    }

    @Test
    fun testPutInMapFromParameters() {
        checkExecutionMatches(
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
        checkExecutionMatches(
            Maps::containsKeyAndPuts,
            ignoreNumberOfAnalysisResults,
            { _, values, _ -> values == null },
            { _, values, r -> 1 !in values.keys && r == 3 },
        )
    }

    @Test
    fun testFindAllChars() {
        checkExecutionMatches(
            Maps::countChars,
            eq(3),
            { _, s, _ -> s == null },
            { _, s, result -> s == "" && result!!.isEmpty() },
            { _, s, result -> s != "" && result == s.groupingBy { it }.eachCount() },
        )
    }

    @Test
    fun putElementsTest() {
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
            Maps::createWithDifferentType,
            eq(2),
            { _, seed, result -> seed % 2 != 0 && result is java.util.LinkedHashMap },
            { _, seed, result -> seed % 2 == 0 && result !is java.util.LinkedHashMap && result is java.util.HashMap },
        )
    }

    @Test
    fun removeCustomObjectTest() {
        checkExecutionMatches(
            Maps::removeCustomObject,
            ge(3),
            { _, map, _, _ -> map == null },
            { _, map, i, result -> (map.isEmpty() || CustomClass(i) !in map) && result == null },
            { _, map, i, result -> map.isNotEmpty() && CustomClass(i) in map && result == map[CustomClass(i)] },
        )
    }

    @Test
    @Tag("slow") // it takes about 20 minutes to execute this test
    fun testMapOperator() {
        checkExecutionMatches(
            Maps::mapOperator,
            ignoreNumberOfAnalysisResults
        )
    }

    @Test
    fun testComputeValue() {
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
            Maps::clearEntries,
            between(3..4),
            { _, map, _ -> map == null },
            { _, map, result -> map.isEmpty() && result == 0 },
            { _, map, result -> map.isNotEmpty() && result == 1 },
        )
    }

    @Test
    fun testContainsKey() {
        checkExecutionMatches(
            Maps::containsKey,
            between(3..5),
            { _, map, _, _ -> map == null },
            { _, map, key, result -> key !in map && result == 0 },
            { _, map, key, result -> key in map && result == 1 },
        )
    }

    @Test
    fun testContainsValue() {
        checkExecutionMatches(
            Maps::containsValue,
            between(3..6),
            { _, map, _, _ -> map == null },
            { _, map, value, result -> value !in map.values && result == 0 },
            { _, map, value, result -> value in map.values && result == 1 },
        )
    }

    @Test
    fun testGetOrDefaultElement() {
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
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
        checkExecutionMatches(
            Maps::createMapWithString,
            eq(1),
            { _, r -> r!!.isEmpty() }
        )
    }

    @Test
    fun testCreateMapWithEnum() {
        checkExecutionMatches(
            Maps::createMapWithEnum,
            eq(1),
            { _, r -> r != null && r.size == 2 && r[Maps.WorkDays.Monday] == 112 && r[Maps.WorkDays.Friday] == 567 }
        )
    }
}