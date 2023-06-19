package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner


// TODO failed Kotlin compilation SAT-1332
internal class SetsTest : JavaMethodTestRunner() {
    @Test
    fun createTest() {
        checkExecutionMatches(
            Sets::create,
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.isEmpty() && r!!.isEmpty() },
            { _, a, r -> a != null && a.isNotEmpty() && r != null && r.isNotEmpty() && r.containsAll(a.toList()) },
        )
    }

    @Test
    fun testSetContainsInteger() {
        checkExecutionMatches(
            Sets::setContainsInteger,
            { _, set, _, _, _ -> set == null },
            { _, set, a, _, r -> 1 + a in set && r != null && 1 + a !in r && set.remove(1 + a) && r == set },
            { _, set, a, _, r -> 1 + a !in set && set.isEmpty() && r == null },
            { _, set, a, b, r -> 1 + a !in set && set.isNotEmpty() && r != null && r == set && 4 + a + b !in r },
        )
    }

    @Test
    @Disabled("Does not find positive branches JIRA:1529")
    fun testSetContains() {
        checkExecutionMatches(
            Sets::setContains,
        )
    }

    @Test
    fun testSimpleContains() {
        checkExecutionMatches(
            Sets::simpleContains,
            { _, set, _ -> set == null },
            { _, set, r -> set != null && "aaa" in set && r == true },
            { _, set, r -> set != null && "aaa" !in set && r == false }
        )
    }

    @Test
    @Disabled("Same problem from testSetContains JIRA:1529")
    fun testMoreComplicatedContains() {
        checkExecutionMatches(
            Sets::moreComplicatedContains, // TODO how many branches do we have?
        )
    }


    @Test
    fun testFindAllChars() {
        checkExecutionMatches(
            Sets::findAllChars,
            { _, s, _ -> s == null },
            { _, s, result -> s == "" && result!!.isEmpty() },
            { _, s, result -> s != "" && result == s.toCollection(mutableSetOf()) },
        )
    }

    @Test
    fun testRemoveSpace() {
        val resultFun = { set: Set<Char> -> listOf(' ', '\t', '\r', '\n').intersect(set).size }
        checkExecutionMatches(
            Sets::removeSpace,
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
    fun addElementsTest() {
        checkExecutionMatches(
            Sets::addElements,
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
    fun removeElementsTest() {
        checkExecutionMatches(
            Sets::removeElements,
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
        checkExecutionMatches(
            Sets::createWithDifferentType,
            { _, seed, r -> seed % 2 != 0 && r is java.util.LinkedHashSet },
            { _, seed, r -> seed % 2 == 0 && r !is java.util.LinkedHashSet && r is java.util.HashSet },
        )
    }

    @Test
    fun removeCustomObjectTest() {
        checkExecutionMatches(
            Sets::removeCustomObject,
            { _, set, _, _ -> set == null },
            { _, set, _, result -> set.isEmpty() && result == 0 },
            { _, set, i, result -> set.isNotEmpty() && CustomClass(i) !in set && result == 0 },
            { _, set, i, result -> set.isNotEmpty() && CustomClass(i) in set && result == 1 },
        )
    }

    @Test
    fun testAddAllElements() {
        checkExecutionMatches(
            Sets::addAllElements,
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> set.containsAll(other) && result == 0 },
            { _, set, other, result -> !set.containsAll(other) && result == 1 },
            // TODO: Cannot find branch with result == 2
            { _, set, other, result -> !set.containsAll(other) && other.any { it in set } && result == 2 },
        )
    }

    @Test
    fun testRemoveAllElements() {
            checkExecutionMatches(
                Sets::removeAllElements,
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
    fun testRetainAllElements() {
        checkExecutionMatches(
            Sets::retainAllElements,
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> other.containsAll(set) && result == 1 },
            { _, set, other, result -> set.any { it !in other } && result == 0 },
        )
    }

    @Test
    fun testContainsAllElements() {
        checkExecutionMatches(
            Sets::containsAllElements,
            { _, set, _, _ -> set == null },
            { _, set, other, _ -> set != null && other == null },
            { _, set, other, result -> set.isEmpty() || other.isEmpty() && result == -1 },
            { _, set, other, result -> set.isNotEmpty() && other.isNotEmpty() && set.containsAll(other) && result == 1 },
            { _, set, other, result -> set.isNotEmpty() && other.isNotEmpty() && !set.containsAll(other) && result == 0 },
        )
    }


    @Test
    fun testClearElements() {
        checkExecutionMatches(
            Sets::clearElements,
            { _, set, _ -> set == null },
            { _, set, result -> set.isEmpty() && result == 0 },
            { _, set, result -> set.isNotEmpty() && result == 1 },
        )
    }


    @Test
    fun testContainsElement() {
        checkExecutionMatches(
            Sets::containsElement,
            { _, set, _, _ -> set == null },
            { _, set, i, result -> i !in set && result == 0 },
            { _, set, i, result -> i in set && result == 1 },
        )
    }
}