package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

internal class GenericListsExampleTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Can't find method (id:26)com.sun.tools.javac.code.Symbol#flags()")
    fun testListOfListsOfT() {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfListsOfT,
            eq(-1)
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testListOfComparable() {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfComparable,
            eq(1),
            { _, v, r -> v != null && v.size > 1 && v[0] != null && v.all { it is Comparable<*> || it == null } && v == r },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testListOfT() {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfT,
            eq(1),
            { _, v, r -> v != null && v.size >= 2 && v[0] != null && v == r },
        )
    }

    @Test
    @Disabled("Expected exactly 1 executions, but 29 found")
    fun testListOfTArray() {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfTArray,
            eq(1)
        )
    }

    @Test
    @Disabled("Expected exactly -1 executions, but 29 found")
    fun testListOfExtendsTArray() {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfExtendsTArray,
            eq(-1)
        )
    }

    @Test
    @Disabled("Expected exactly -1 executions, but 29 found")
    fun testListOfPrimitiveArrayInheritors() {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfPrimitiveArrayInheritors,
            eq(-1)
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1]")
    fun createWildcard() {
        checkDiscoveredProperties(
            GenericListsExample<*>::wildcard,
            eq(4),
            { _, v, r -> v == null && r?.isEmpty() == true },
            { _, v, r -> v != null && v.size == 1 && v[0] != null && v == r && v.all { it is Number || it == null } },
            { _, v, r -> v != null && (v.size != 1 || v[0] == null) && v == r && v.all { it is Number || it == null } },
        )
    }

    @Suppress("NestedLambdaShadowedImplicitParameter")
    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun createListOfLists() {
        checkDiscoveredProperties(
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
    @Disabled("Some properties were not discovered at positions (from 0): [1, 2]")
    fun createWildcardWithOnlyQuestionMark() {
        checkDiscoveredProperties(
            GenericListsExample<*>::wildcardWithOnlyQuestionMark,
            eq(3),
            { _, v, r -> v == null && r?.isEmpty() == true },
            { _, v, r -> v.size == 1 && v == r },
            { _, v, r -> v.size != 1 && v == r },
        )
    }


    @Test
    @Disabled("Expected exactly 1 executions, but 32 found")
    fun testGenericWithArrayOfPrimitives() {
        checkDiscoveredProperties(
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
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testGenericWithObject() {
        checkDiscoveredProperties(
            GenericListsExample<*>::genericWithObject,
            eq(1),
            { _, v, r -> v != null && v.size >= 2 && v[0] != null && v[0] is Long && v == r },
        )
    }


    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testGenericWithArrayOfArrays() {
        checkDiscoveredProperties(
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