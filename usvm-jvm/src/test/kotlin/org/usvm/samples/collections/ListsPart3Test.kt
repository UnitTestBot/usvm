package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.*


internal class ListsPart3Test : JavaMethodTestRunner() {
    @Test
    fun createTest() = disableTest("Expected exactly 3 executions, but 116 found") {
        checkDiscoveredProperties(
            Lists::create,
            eq(3),
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.isEmpty() && r != null && r.isEmpty() },
            { _, a, r -> a != null && a.isNotEmpty() && r != null && r.isNotEmpty() && a.toList() == r.also { println(r) } },
        )
    }

    @Test
    fun testBigListFromParameters() = disableTest("Expected exactly 1 executions, but 2 found") {
        checkDiscoveredProperties(
            Lists::bigListFromParameters,
            eq(1),
            { _, list, r -> list.size == r && list.size == 11 },
        )
    }

    @Test
    fun testGetNonEmptyCollection() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            Lists::getNonEmptyCollection,
            eq(3),
            { _, collection, _ -> collection == null },
            { _, collection, r -> collection.isEmpty() && r == null },
            { _, collection, r -> collection.isNotEmpty() && collection == r },
        )
    }

    @Test
    fun testGetFromAnotherListToArray() = disableTest("Expected exactly 4 executions, but 227 found") {
        checkDiscoveredProperties(
            Lists::getFromAnotherListToArray,
            eq(4),
            { _, l, _ -> l == null },
            { _, l, _ -> l.isEmpty() },
            { _, l, r -> l[0] == null && r == null },
            { _, l, r -> l[0] != null && r is Array<*> && r.isArrayOf<Int>() && r.size == 1 && r[0] == l[0] },
        )
    }

    @Test
    fun addElementsTest() = disableTest("Some properties were not discovered at positions (from 0): [2, 3, 4]") {
        checkDiscoveredProperties(
            Lists::addElements,
            eq(5),
            { _, list, _, _ -> list == null },
            { _, list, a, _ -> list != null && list.size >= 2 && a == null },
            { _, list, _, r -> list.size < 2 && r == list },
            { _, list, a, r -> list.size >= 2 && a.size < 2 && r == list },
            { _, list, a, r ->
                require(r != null)

                val sizeConstraint = list.size >= 2 && a.size >= 2 && r.size == list.size + a.size
                val content = r.mapIndexed { i, it -> if (i < r.size) it == r[i] else it == a[i - r.size] }.all { it }

                sizeConstraint && content
            },
        )
    }

    @Test
    fun removeElementsTest() = disableTest("Some properties were not discovered at positions (from 0): [3, 4, 5, 6]") {
        checkDiscoveredPropertiesWithExceptions(
            Lists::removeElements,
            between(7..8),
            { _, list, _, _, r -> list == null && r.isException<NullPointerException>() },
            { _, list, i, _, r -> list != null && i < 0 && r.isException<IndexOutOfBoundsException>() },
            { _, list, i, _, r -> list != null && i >= 0 && list.size > i && list[i] == null && r.isException<NullPointerException>() },
            { _, list, i, j, r ->
                require(list != null && list[i] != null)

                val listConstraints = i >= 0 && list.size > i && (list.size <= j + 1 || j < 0)
                val resultConstraint = r.isException<IndexOutOfBoundsException>()

                listConstraints && resultConstraint
            },
            { _, list, i, j, r ->
                require(list != null && list[i] != null)

                val k = j + if (i <= j) 1 else 0
                val indicesConstraint = i >= 0 && list.size > i && j >= 0 && list.size > j + 1
                val contentConstraint = list[i] != null && list[k] == null
                val resultConstraint = r.isException<NullPointerException>()

                indicesConstraint && contentConstraint && resultConstraint
            },
            { _, list, i, j, r ->
                require(list != null)

                val k = j + if (i <= j) 1 else 0

                val precondition = i >= 0 && list.size > i && j >= 0 && list.size > j + 1 && list[i] < list[k]
                val postcondition = r.getOrNull() == list[i]

                precondition && postcondition
            },
            { _, list, i, j, r ->
                require(list != null)

                val k = j + if (i <= j) 1 else 0

                val precondition = i >= 0 && list.size > i && j >= 0 && list.size > j + 1 && list[i] >= list[k]
                val postcondition = r.getOrNull() == list[k]

                precondition && postcondition
            },
        )
    }

    @Test
    fun createArrayWithDifferentTypeTest() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            Lists::createWithDifferentType,
            eq(2),
            { _, x, r -> x % 2 != 0 && r is LinkedList && r == List(4) { it } },
            { _, x, r -> x % 2 == 0 && r is ArrayList && r == List(4) { it } },
        )
    }

    @Test
    fun getElementsTest() = disableTest("Expected exactly 4 executions, but 767 found") {
        checkDiscoveredProperties(
            Lists::getElements,
            eq(4),
            { _, x, _ -> x == null },
            { _, x, r -> x != null && x.isEmpty() && r != null && r.isEmpty() },
            { _, x, _ -> x != null && x.isNotEmpty() && x.any { it == null } },
            { _, x, r -> x != null && x.isNotEmpty() && x.all { it is Int } && r != null && r.toList() == x },
        )
    }

    @Test
    fun setElementsTest() = disableTest("Expected exactly 3 executions, but 100 found") {
        checkDiscoveredProperties(
            Lists::setElements,
            eq(3),
            { _, x, _ -> x == null },
            { _, x, r -> x != null && x.isEmpty() && r != null && r.isEmpty() },
            { _, x, r -> x != null && x.isNotEmpty() && r != null && r.containsAll(x.toList()) && r.size == x.size },
        )
    }

    @Test
    fun testClear() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            Lists::clear,
            eq(3),
            { _, list, _ -> list == null },
            { _, list, r -> list.size >= 2 && r == emptyList<Int>() },
            { _, list, r -> list.size < 2 && r == emptyList<Int>() },
        )
    }

    @Test
    fun testAddAll() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            Lists::addAll,
            eq(3),
            { _, list, _, _ -> list == null },
            { _, list, i, r ->
                list != null && list.isEmpty() && r != null && r.size == 1 && r[0] == i
            },
            { _, list, i, r ->
                list != null && list.isNotEmpty() && r != null && r.size == 1 + list.size && r == listOf(i) + list
            },
        )
    }

    @Test
    fun testAddAllInIndex() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2, 3]") {
        checkDiscoveredProperties(
            Lists::addAllByIndex,
            eq(4),
            { _, list, i, _ -> list == null && i >= 0 },
            { _, list, i, _ -> list == null && i < 0 },
            { _, list, i, r -> list != null && i >= list.size && r == list },
            { _, list, i, r ->
                list != null && i in 0..list.lastIndex && r == list.toMutableList().apply { addAll(i, listOf(0, 1)) }
            },
        )
    }

    @Test
    fun testAsListExample() = disableTest("Expected exactly 2 executions, but 3 found") {
        checkDiscoveredProperties(
            Lists::asListExample,
            eq(2),
            { _, arr, r -> arr.isEmpty() && r != null && r.isEmpty() },
            { _, arr, r -> arr.isNotEmpty() && arr.contentEquals(r!!.toTypedArray()) },
        )
    }

    @Test
    fun testRemoveFromList() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2, 3]") {
        checkDiscoveredPropertiesWithExceptions(
            Lists::removeFromList,
            ge(4),
            { _, list, _, r -> list == null && r.isException<NullPointerException>() },
            { _, list, _, r -> list != null && list.isEmpty() && r.isException<IndexOutOfBoundsException>() },
            { _, list, i, r ->
                require(list != null && list.lastOrNull() != null)

                list.isNotEmpty() && (i < 0 || i >= list.size) && r.isException<IndexOutOfBoundsException>()
            },
            { _, list, i, r ->
                require(list != null && list.lastOrNull() != null)

                val changedList = list.toMutableList().apply {
                    set(i, last())
                    removeLast()
                }

                val precondition = list.isNotEmpty() && i >= 0 && i < list.size
                val postcondition = changedList == r.getOrNull()

                precondition && postcondition
            },
            // TODO: add branches with conditions (list is LinkedList) and (list !is ArrayList && list !is LinkedList)
        )
    }

}