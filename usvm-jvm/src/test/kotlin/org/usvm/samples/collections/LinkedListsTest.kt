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
            { _, l, _ -> l == null },
            { _, l, _ -> l.size <= 2 },
            { _, l, r -> l.size > 2 && r == 1 },
        )
    }

    @Test
    fun testOffer() {
        checkExecutionMatches(
            LinkedLists::offer,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == l + 1 },
        )
    }

    @Test
    fun testOfferLast() {
        checkExecutionMatches(
            LinkedLists::offerLast,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == l + 1 },
        )
    }


    @Test
    fun testAddLast() {
        checkExecutionMatches(
            LinkedLists::addLast,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && (r == l + 1) },
        )
    }

    @Test
    fun testPush() {
        checkExecutionMatches(
            LinkedLists::push,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == listOf(1) + l },
        )
    }

    @Test
    fun testOfferFirst() {
        checkExecutionMatches(
            LinkedLists::offerFirst,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == listOf(1) + l },
        )
    }

    @Test
    fun testAddFirst() {
        checkExecutionMatches(
            LinkedLists::addFirst,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r!!.size == l.size + 1 && r[0] == 1 },
        )
    }

    @Test
    fun testPeek() {
        checkWithExceptionExecutionMatches(
            LinkedLists::peek,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.first() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testPeekFirst() {
        checkWithExceptionExecutionMatches(
            LinkedLists::peekFirst,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.first() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testPeekLast() {
        checkWithExceptionExecutionMatches(
            LinkedLists::peekLast,
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.last() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l.last() },
        )
    }

    @Test
    fun testElement() {
        checkWithExceptionExecutionMatches(
            LinkedLists::element,
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
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.isEmpty() && r.isException<NoSuchElementException>() },
            { _, l, r -> l != null && l.size == 1 && r.getOrNull() == l },
            { _, l, _ -> l != null && l.size > 1 && l.last() == null },
            { _, l, r -> l != null && l.size > 1 && r.getOrNull() == l.subList(0, l.size - 1) },
        )
    }

}
