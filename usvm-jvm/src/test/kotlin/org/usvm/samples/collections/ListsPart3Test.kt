package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.util.isException


// TODO failed Kotlin compilation SAT-1332
internal class ListsPart3Test : JavaMethodTestRunner() {
    @Test
    fun createTest() {
        checkExecutionMatches(
            Lists::create,
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.isEmpty() && r!!.isEmpty() },
            { _, a, r -> a != null && a.isNotEmpty() && r != null && r.isNotEmpty() && a.toList() == r.also { println(r) } },
        )
    }

    @Test
    fun testBigListFromParameters() {
        checkExecutionMatches(
            Lists::bigListFromParameters,
            { _, list, r -> list.size == r && list.size == 11 },
        )
    }

    @Test
    fun testGetNonEmptyCollection() {
        checkExecutionMatches(
            Lists::getNonEmptyCollection,
            { _, collection, _ -> collection == null },
            { _, collection, r -> collection.isEmpty() && r == null },
            { _, collection, r -> collection.isNotEmpty() && collection == r },
        )
    }

    @Test
    fun testGetFromAnotherListToArray() {
        checkExecutionMatches(
            Lists::getFromAnotherListToArray,
            { _, l, _ -> l == null },
            { _, l, _ -> l.isEmpty() },
            { _, l, r -> l[0] == null && r == null },
            { _, l, r -> l[0] != null && r is Array<*> && r.isArrayOf<Int>() && r.size == 1 && r[0] == l[0] },
        )
    }

    @Test
    fun addElementsTest() {
        checkExecutionMatches(
            Lists::addElements,
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
    fun removeElementsTest() {
        checkWithExceptionExecutionMatches(
            Lists::removeElements,
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
    fun createArrayWithDifferentTypeTest() {
        checkExecutionMatches(
            Lists::createWithDifferentType,
            { _, x, r -> x % 2 != 0 && r is java.util.LinkedList && r == List(4) { it } },
            { _, x, r -> x % 2 == 0 && r is java.util.ArrayList && r == List(4) { it } },
        )
    }

    @Test
    fun getElementsTest() {
        checkExecutionMatches(
            Lists::getElements,
            { _, x, _ -> x == null },
            { _, x, r -> x != null && x.isEmpty() && r!!.isEmpty() },
            { _, x, _ -> x != null && x.isNotEmpty() && x.any { it == null } },
            { _, x, r -> x != null && x.isNotEmpty() && x.all { it is Int } && r!!.toList() == x },
        )
    }

    @Test
    fun setElementsTest() {
        checkExecutionMatches(
            Lists::setElements,
            { _, x, _ -> x == null },
            { _, x, r -> x != null && x.isEmpty() && r!!.isEmpty() },
            { _, x, r -> x != null && x.isNotEmpty() && r!!.containsAll(x.toList()) && r.size == x.size },
        )
    }

    @Test
    fun testClear() {
        checkExecutionMatches(
            Lists::clear,
            { _, list, _ -> list == null },
            { _, list, r -> list.size >= 2 && r == emptyList<Int>() },
            { _, list, r -> list.size < 2 && r == emptyList<Int>() },
        )
    }

    @Test
    fun testAddAll() {
        checkExecutionMatches(
            Lists::addAll,
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
    fun testAddAllInIndex() {
        checkExecutionMatches(
            Lists::addAllByIndex,
            { _, list, i, _ -> list == null && i >= 0 },
            { _, list, i, _ -> list == null && i < 0 },
            { _, list, i, r -> list != null && i >= list.size && r == list },
            { _, list, i, r ->
                list != null && i in 0..list.lastIndex && r == list.toMutableList().apply { addAll(i, listOf(0, 1)) }
            },
        )
    }

    @Test
    fun testAsListExample() {
        checkExecutionMatches(
            Lists::asListExample,
            { _, arr, r -> arr.isEmpty() && r!!.isEmpty() },
            { _, arr, r -> arr.isNotEmpty() && arr.contentEquals(r!!.toTypedArray()) },
        )
    }

    @Test
    @Disabled("TODO: add choosing proper type in list wrapper")
    fun testRemoveFromList() {
        checkWithExceptionExecutionMatches(
            Lists::removeFromList,
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