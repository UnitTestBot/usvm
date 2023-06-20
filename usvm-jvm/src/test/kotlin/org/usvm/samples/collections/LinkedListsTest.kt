package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner

import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

// TODO failed Kotlin compilation (generics) SAT-1332
internal class LinkedListsTest : JavaMethodTestRunner() {

    @Test
    fun testSet() {
        checkDiscoveredProperties(
            LinkedLists::set,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, _ -> l.size <= 2 },
            { _, l, r -> l.size > 2 && r == 1 },
        )
    }

    @Test
    fun testOffer() {
        checkDiscoveredProperties(
            LinkedLists::offer,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == l + 1 },
        )
    }

    @Test
    fun testOfferLast() {
        checkDiscoveredProperties(
            LinkedLists::offerLast,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == l + 1 },
        )
    }


    @Test
    fun testAddLast() {
        checkDiscoveredProperties(
            LinkedLists::addLast,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && (r == l + 1) },
        )
    }

    @Test
    fun testPush() {
        checkDiscoveredProperties(
            LinkedLists::push,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == listOf(1) + l },
        )
    }

    @Test
    fun testOfferFirst() {
        checkDiscoveredProperties(
            LinkedLists::offerFirst,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r == listOf(1) + l },
        )
    }

    @Test
    fun testAddFirst() {
        checkDiscoveredProperties(
            LinkedLists::addFirst,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && l.size <= 1 && r == l },
            { _, l, r -> l != null && l.size > 1 && r!!.size == l.size + 1 && r[0] == 1 },
        )
    }

    @Test
    fun testPeek() {
        checkDiscoveredPropertiesWithExceptions(
            LinkedLists::peek,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.first() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testPeekFirst() {
        checkDiscoveredPropertiesWithExceptions(
            LinkedLists::peekFirst,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.first() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l[0] },
        )
    }

    @Test
    fun testPeekLast() {
        checkDiscoveredPropertiesWithExceptions(
            LinkedLists::peekLast,
            eq(3),
            { _, l, _ -> l == null },
            { _, l, r -> l != null && (l.isEmpty() || l.last() == null) && r.isException<NullPointerException>() },
            { _, l, r -> l != null && l.isNotEmpty() && r.getOrNull() == l.last() },
        )
    }

    @Test
    fun testElement() {
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
        checkDiscoveredPropertiesWithExceptions(
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
