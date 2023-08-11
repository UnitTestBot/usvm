package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest

internal class SetsTest : JavaMethodTestRunner() {
    @Test
    fun createTest() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            Sets::create,
            eq(3),
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.isEmpty() && r != null && r.isEmpty() },
            { _, a, r -> a != null && a.isNotEmpty() && r != null && r.isNotEmpty() && r.containsAll(a.toList()) },
        )
    }

    @Test
    fun testSetContainsInteger() = disableTest("Some properties were not discovered at positions (from 0): [1, 2, 3]") {
        checkDiscoveredProperties(
            Sets::setContainsInteger,
            ignoreNumberOfAnalysisResults,
            { _, set, _, _, _ -> set == null },
            { _, set, a, _, r -> 1 + a in set && r != null && 1 + a !in r && set.remove(1 + a) && r == set },
            { _, set, a, _, r -> 1 + a !in set && set.isEmpty() && r == null },
            { _, set, a, b, r -> 1 + a !in set && set.isNotEmpty() && r != null && r == set && 4 + a + b !in r },
        )
    }

    @Test
    fun testSetContains() = disableTest("Expected exactly -1 executions, but 1666 found") {
        checkDiscoveredProperties(
            Sets::setContains,
            eq(-1),
        )
    }

    @Test
    fun testSimpleContains() = disableTest("Solver timeout") {
        checkDiscoveredProperties(
            Sets::simpleContains,
            ignoreNumberOfAnalysisResults,
            { _, set, _ -> set == null },
            { _, set, r -> set != null && "aaa" in set && r == true },
            { _, set, r -> set != null && "aaa" !in set && r == false }
        )
    }

    @Test
    fun testMoreComplicatedContains() = disableTest("Expected exactly -1 executions, but 93 found") {
        checkDiscoveredProperties(
            Sets::moreComplicatedContains, // TODO how many branches do we have?
            eq(-1),
        )
    }


    @Test
    fun testFindAllChars() = disableTest("Solver timeout") {
        checkDiscoveredProperties(
            Sets::findAllChars,
            eq(3),
            { _, s, _ -> s == null },
            { _, s, result -> s == "" && result != null && result.isEmpty() },
            { _, s, result -> s != "" && result == s.toCollection(mutableSetOf()) },
        )
    }

    @Test
    fun testRemoveSpace() = disableTest("Some properties were not discovered at positions (from 0): [1, 2, 3, 4]") {
        val resultFun = { set: Set<Char> -> listOf(' ', '\t', '\r', '\n').intersect(set).size }
        checkDiscoveredProperties(
            Sets::removeSpace,
            ge(3),
            { _, set, _ -> set == null },
            { _, set, res -> ' ' in set && resultFun(set) == res },
            { _, set, res -> '\t' in set && resultFun(set) == res },
            { _, set, res -> '\n' in set && resultFun(set) == res },
            { _, set, res -> '\r' in set && resultFun(set) == res },
            { _, set, res -> ' ' !in set && resultFun(set) == res },
            { _, set, res -> '\t' !in set && resultFun(set) == res },
            { _, set, res -> '\n' !in set && resultFun(set) == res },
            { _, set, res -> '\r' !in set && resultFun(set) == res },
        )
    }

    @Test
    fun addElementsTest() = disableTest("Some properties were not discovered at positions (from 0): [3, 4]") {
        checkDiscoveredProperties(
            Sets::addElements,
            ge(5),
            { _, set, _, _ -> set == null },
            { _, set, a, _ -> set != null && set.isNotEmpty() && a == null },
            { _, set, _, r -> set.isEmpty() && r == set },
            { _, set, a, r -> set.isNotEmpty() && a.isEmpty() && r == set },
            { _, set, a, r ->
                set.size >= 1 && a.isNotEmpty() && r == set.toMutableSet().apply { addAll(a.toTypedArray()) }
            },
        )
    }

    @Test
    fun removeElementsTest() = disableTest("Some properties were not discovered at positions (from 0): [2, 3, 4, 5]") {
        checkDiscoveredProperties(
            Sets::removeElements,
            between(6..8),
            { _, set, _, _, _ -> set == null },
            { _, set, i, j, res -> set != null && i !in set && j !in set && res == -1 },
            { _, set, i, j, res -> set != null && set.size >= 1 && i !in set && j in set && res == 4 },
            { _, set, i, j, res -> set != null && set.size >= 1 && i in set && (j !in set || j == i) && res == 3 },
            { _, set, i, j, res -> set != null && set.size >= 2 && i in set && j in set && i > j && res == 2 },
            { _, set, i, j, res -> set != null && set.size >= 2 && i in set && j in set && i < j && res == 1 },
            // unreachable branch
        )
    }

    @Test
    fun createWithDifferentTypeTest() {
        checkDiscoveredProperties(
            Sets::createWithDifferentType,
            eq(2),
            { _, seed, r -> seed % 2 != 0 && r is LinkedHashSet },
            { _, seed, r -> seed % 2 == 0 && r !is LinkedHashSet && r is HashSet },
        )
    }

    @Test
    fun removeCustomObjectTest() = disableTest("Some properties were not discovered at positions (from 0): [2, 3]") {
        checkDiscoveredProperties(
            Sets::removeCustomObject,
            ge(4),
            { _, set, _, _ -> set == null },
            { _, set, _, result -> set.isEmpty() && result == 0 },
            { _, set, i, result -> set.isNotEmpty() && CustomClass(i) !in set && result == 0 },
            { _, set, i, result -> set.isNotEmpty() && CustomClass(i) in set && result == 1 },
        )
    }

    @Test
    fun testAddAllElements() = disableTest("Some properties were not discovered at positions (from 0): [3, 4]") {
        checkDiscoveredProperties(
            Sets::addAllElements,
            ignoreNumberOfAnalysisResults,
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> set.containsAll(other) && result == 0 },
            { _, set, other, result -> !set.containsAll(other) && result == 1 },
            // TODO: Cannot find branch with result == 2
            { _, set, other, result -> !set.containsAll(other) && other.any { it in set } && result == 2 },
        )
    }

    @Test
    fun testRemoveAllElements() = disableTest("Some properties were not discovered at positions (from 0): [1, 2, 3]") {
        checkDiscoveredProperties(
            Sets::removeAllElements,
            eq(4),
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> other.all { it !in set } && result == 0 },
            { _, set, other, result -> set.containsAll(other) && result == 1 },
            //TODO: JIRA:1666 -- Engine ignores branches in Wrappers sometimes
            // TODO: cannot find branch with result == 2
            // { _, set, other, result -> !set.containsAll(other) && other.any { it in set } && result == 2 },
        )
    }

    @Test
    fun testRetainAllElements() = disableTest("Some properties were not discovered at positions (from 0): [3]") {
        checkDiscoveredProperties(
            Sets::retainAllElements,
            ge(4),
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> other.containsAll(set) && result == 1 },
            { _, set, other, result -> set.any { it !in other } && result == 0 },
        )
    }

    @Test
    fun testContainsAllElements() = disableTest("Some properties were not discovered at positions (from 0): [3, 4]") {
        checkDiscoveredProperties(
            Sets::containsAllElements,
            ge(5),
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> set.isEmpty() || other.isEmpty() && result == -1 },
            { _, set, other, result -> set.isNotEmpty() && other.isNotEmpty() && set.containsAll(other) && result == 1 },
            { _, set, other, result -> set.isNotEmpty() && other.isNotEmpty() && !set.containsAll(other) && result == 0 },
        )
    }


    @Test
    fun testClearElements() = disableTest("Expected exactly 3 executions, but 13 found") {
        checkDiscoveredProperties(
            Sets::clearElements,
            eq(3),
            { _, set, _ -> set == null },
            { _, set, result -> set.isEmpty() && result == 0 },
            { _, set, result -> set.isNotEmpty() && result == 1 },
        )
    }


    @Test
    fun testContainsElement() = disableTest("Some properties were not discovered at positions (from 0): [1, 2]") {
        checkDiscoveredProperties(
            Sets::containsElement,
            between(3..5),
            { _, set, _, _ -> set == null },
            { _, set, i, result -> i !in set && result == 0 },
            { _, set, i, result -> i in set && result == 1 },
        )
    }
}