package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class GenericListsExampleTest : JavaMethodTestRunner() {
    @Test
    fun testListOfListsOfT() {
        checkExecutionMatches(
            GenericListsExample<Long>::listOfListsOfT,
            eq(-1)
        )
    }

    @Test
    fun testListOfComparable() {
        checkExecutionMatches(
            GenericListsExample<Long>::listOfComparable,
            eq(1),
            { _, v, r -> v != null && v.size > 1 && v[0] != null && v.all { it is Comparable<*> || it == null } && v == r },
        )
    }

    @Test
    fun testListOfT() {
        checkExecutionMatches(
            GenericListsExample<Long>::listOfT,
            eq(1),
            { _, v, r -> v != null && v.size >= 2 && v[0] != null && v == r },
        )
    }

    @Test
    fun testListOfTArray() {
        checkExecutionMatches(
            GenericListsExample<Long>::listOfTArray,
            eq(1)
        )
    }

    @Test
    fun testListOfExtendsTArray() {
        checkExecutionMatches(
            GenericListsExample<Long>::listOfExtendsTArray,
            eq(-1)
        )
    }

    @Test
    fun testListOfPrimitiveArrayInheritors() {
        checkExecutionMatches(
            GenericListsExample<Long>::listOfPrimitiveArrayInheritors,
            eq(-1)
        )
    }

    @Test
    fun createWildcard() {
        checkExecutionMatches(
            GenericListsExample<*>::wildcard,
            eq(4),
            { _, v, r -> v == null && r?.isEmpty() == true },
            { _, v, r -> v != null && v.size == 1 && v[0] != null && v == r && v.all { it is Number || it == null } },
            { _, v, r -> v != null && (v.size != 1 || v[0] == null) && v == r && v.all { it is Number || it == null } },
        )
    }

    @Suppress("NestedLambdaShadowedImplicitParameter")
    @Test
    fun createListOfLists() {
        checkExecutionMatches(
            GenericListsExample<*>::listOfLists,
            eq(1),
            { _, v, r ->
                val valueCondition = v != null && v[0] != null && v[0].isNotEmpty()
                val typeCondition = v.all { (it is List<*> && it.all { it is Int || it == null }) || it == null }

                valueCondition && typeCondition && v == r
            },
        )
    }

    @Test
    fun createWildcardWithOnlyQuestionMark() {
        checkExecutionMatches(
            GenericListsExample<*>::wildcardWithOnlyQuestionMark,
            eq(3),
            { _, v, r -> v == null && r?.isEmpty() == true },
            { _, v, r -> v.size == 1 && v == r },
            { _, v, r -> v.size != 1 && v == r },
        )
    }


    @Test
    fun testGenericWithArrayOfPrimitives() {
        checkExecutionMatches(
            GenericListsExample<*>::genericWithArrayOfPrimitives,
            eq(1),
            { _, v, _ ->
                val valueCondition = v != null && v.size >= 2 && v[0] != null && v[0].isNotEmpty() && v[0][0] != 0L
                val typeCondition = v.all { it is LongArray || it == null }

                valueCondition && typeCondition
            },
        )
    }


    @Test
    fun testGenericWithObject() {
        checkExecutionMatches(
            GenericListsExample<*>::genericWithObject,
            eq(1),
            { _, v, r -> v != null && v.size >= 2 && v[0] != null && v[0] is Long && v == r },
        )
    }


    @Test
    fun testGenericWithArrayOfArrays() {
        checkExecutionMatches(
            GenericListsExample<*>::genericWithArrayOfArrays,
            eq(1),
            { _, v, _ ->
                val valueCondition = v != null && v.size >= 2 && v[0] != null && v[0].isNotEmpty() && v[0][0] != null
                val typeCondition = v.all {
                    (it is Array<*> && it.isArrayOf<Array<*>>() && it.all { it.isArrayOf<Long>() || it == null }) || it == null
                }

                valueCondition && typeCondition
            },
        )
    }
}