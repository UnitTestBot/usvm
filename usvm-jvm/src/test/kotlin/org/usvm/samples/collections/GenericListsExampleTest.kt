package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest

internal class GenericListsExampleTest : JavaMethodTestRunner() {
    @Test
    fun testListOfListsOfT() = disableTest("Expected exactly -1 executions, but 41 found") {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfListsOfT,
            eq(-1)
        )
    }

    @Test
    fun testListOfComparable() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfComparable,
            eq(1),
            { _, v, r -> v != null && v.size > 1 && v[0] != null && v.all { it is Comparable<*> || it == null } && v == r },
        )
    }

    @Test
    fun testListOfT() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfT,
            eq(1),
            { _, v, r -> v != null && v.size >= 2 && v[0] != null && v == r },
        )
    }

    @Test
    fun testListOfTArray() = disableTest("Expected exactly 1 executions, but 29 found") {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfTArray,
            eq(1)
        )
    }

    @Test
    fun testListOfExtendsTArray() = disableTest("Expected exactly -1 executions, but 29 found") {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfExtendsTArray,
            eq(-1)
        )
    }

    @Test
    fun testListOfPrimitiveArrayInheritors() = disableTest("Expected exactly -1 executions, but 29 found") {
        checkDiscoveredProperties(
            GenericListsExample<Long>::listOfPrimitiveArrayInheritors,
            eq(-1)
        )
    }

    @Test
    fun createWildcard() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
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
    fun createListOfLists() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
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
    fun createWildcardWithOnlyQuestionMark() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredProperties(
            GenericListsExample<*>::wildcardWithOnlyQuestionMark,
            eq(3),
            { _, v, r -> v == null && r?.isEmpty() == true },
            { _, v, r -> v.size == 1 && v == r },
            { _, v, r -> v.size != 1 && v == r },
        )
    }


    @Test
    fun testGenericWithArrayOfPrimitives() = disableTest("Expected exactly 1 executions, but 32 found") {
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
    fun testGenericWithObject() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            GenericListsExample<*>::genericWithObject,
            eq(1),
            { _, v, r -> v != null && v.size >= 2 && v[0] != null && v[0] is Long && v == r },
        )
    }


    @Test
    fun testGenericWithArrayOfArrays() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
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