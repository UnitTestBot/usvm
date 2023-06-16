package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner

import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

// TODO failed Kotlin compilation (generics) SAT-1332
internal class LinkedListsTest : JavaMethodTestRunner() {

    @Test
    fun testSet() {
        checkExecutionMatches(
            LinkedLists::set,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, _ -> l.size <= 2 },
            { _, l, r -> l.size > 2 && r == 1 },
        )
    }

    @Test
    fun testOffer() {
        checkExecutionMatches(
            LinkedLists::offer,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == l + 1 },
        )
    }

    @Test
    fun testOfferLast() {
        checkExecutionMatches(
            LinkedLists::offerLast,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == l + 1 },
        )
    }


    @Test
    fun testAddLast() {
        checkExecutionMatches(
            LinkedLists::addLast,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && (r == l + 1) },
        )
    }

    @Test
    fun testPush() {
        checkExecutionMatches(
            LinkedLists::push,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == listOf(1) + l },
        )
    }

    @Test
    fun testOfferFirst() {
        checkExecutionMatches(
            LinkedLists::offerFirst,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == listOf(1) + l },
        )
    }

    @Test
    fun testAddFirst() {
        checkExecutionMatches(
            LinkedLists::addFirst,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r!!.size == l.size + 1 && r[0] == 1 },
        )
    }

    @Test
    fun testPeek() {
        checkWithExceptionExecutionMatches(
            LinkedLists::peek,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.first() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testPeekFirst() {
        checkWithExceptionExecutionMatches(
            LinkedLists::peekFirst,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.first() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testPeekLast() {
        checkWithExceptionExecutionMatches(
            LinkedLists::peekLast,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.last() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l.last() },
        )
    }

    @Test
    fun testElement() {
        checkWithExceptionExecutionMatches(
            LinkedLists::element,
            eq(4),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, r -> l != null && l.isNotEmpty() && l[0] == null && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && l[0] != null && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testGetFirst() {
        checkWithExceptionExecutionMatches(
            LinkedLists::getFirst,
            eq(4),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, _ -> l != null && l.isNotEmpty() && l[0] == null },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testGetLast() {
        checkWithExceptionExecutionMatches(
            LinkedLists::getLast,
            eq(4),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, _ -> l != null && l.isNotEmpty() && l.last() == null },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l.last() },
        )
    }

    @Test
    fun testPoll() {
        checkWithExceptionExecutionMatches(
            LinkedLists::poll,
            eq(5),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.first() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(1, l.size) },
        )
    }

    @Test
    fun testPollFirst() {
        checkWithExceptionExecutionMatches(
            LinkedLists::pollFirst,
            eq(5),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.first() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(1, l.size) },
        )
    }

    @Test
    fun testPollLast() {
        checkWithExceptionExecutionMatches(
            LinkedLists::pollLast,
            eq(5),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.last() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(0, l.size - 1) },
        )
    }

    @Test
    fun testRemove() {
        checkWithExceptionExecutionMatches(
            LinkedLists::removeFirst,
            eq(5),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.first() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(1, l.size) },
        )
    }

    @Test
    fun testRemoveFirst() {
        checkWithExceptionExecutionMatches(
            LinkedLists::removeFirst,
            eq(5),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.first() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(1, l.size) },
        )
    }

    @Test
    fun testRemoveLast() {
        checkWithExceptionExecutionMatches(
            LinkedLists::removeLast,
            eq(5),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.last() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(0, l.size - 1) },
        )
    }

}
