package org.usvm.samples.collections

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class QueueUsagesTest : JavaMethodTestRunner() {
    @Test
    fun testCreateArrayDeque() {
        checkWithExceptionExecutionMatches(
            QueueUsages::createArrayDeque,
            { _, init, next, r -> init == null && next == null && r.isException<NullPointerException>() },
            { _, init, next, r -> init != null && next == null && r.isException<NullPointerException>() },
            { _, init, next, r -> init != null && next != null && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testCreateLinkedList() {
        checkWithExceptionExecutionMatches(
            QueueUsages::createLinkedList,
            { _, _, _, r -> r.getOrNull()!! == 2 },
        )
    }

    @Test
    fun testCreateLinkedBlockingDeque() {
        checkWithExceptionExecutionMatches(
            QueueUsages::createLinkedBlockingDeque,
            { _, init, next, r -> init == null && next == null && r.isException<NullPointerException>()  },
            { _, init, next, r -> init != null && next == null && r.isException<NullPointerException>() },
            { _, init, next, r -> init != null && next != null && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testContainsQueue() {
        checkWithExceptionExecutionMatches(
            QueueUsages::containsQueue,
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> x in q && r.getOrNull() == 1 },
            { _, q, x, r -> x !in q && r.getOrNull() == 0 },
        )
    }

    @Test
    fun testAddQueue() {
        checkWithExceptionExecutionMatches(
            QueueUsages::addQueue,
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> q != null && x in r.getOrNull()!! },
            { _, q, x, r -> q != null && x == null && r.isException<NullPointerException>() },        )
    }

    @Test
    fun testAddAllQueue() {
        checkWithExceptionExecutionMatches(
            QueueUsages::addAllQueue,
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> q != null && x in r.getOrNull()!! }, // we can cover this line with x == null or x != null
            { _, q, x, r -> q != null && x == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCastQueueToDeque() {
        checkExecutionMatches(
            QueueUsages::castQueueToDeque,
            { _, q, r -> q !is java.util.Deque<*> && r == null },
            { _, q, r -> q is java.util.Deque<*> && r is java.util.Deque<*> },
        )
    }

    @Test
    fun testCheckSubtypesOfQueue() {
        checkExecutionMatches(
            QueueUsages::checkSubtypesOfQueue,
            { _, q, r -> q == null && r == 0 },
            { _, q, r -> q is java.util.LinkedList<*> && r == 1 },
            { _, q, r -> q is java.util.ArrayDeque<*> && r == 2 },
            { _, q, r -> q !is java.util.LinkedList<*> && q !is java.util.ArrayDeque && r == 3 }
        )
    }

    @Test
    @Disabled("TODO: Related to https://github.com/UnitTestBot/UTBotJava/issues/820")
    fun testCheckSubtypesOfQueueWithUsage() {
        checkExecutionMatches(
            QueueUsages::checkSubtypesOfQueueWithUsage,
            { _, q, r -> q == null && r == 0 },
            { _, q, r -> q is java.util.LinkedList<*> && r == 1 },
            { _, q, r -> q is java.util.ArrayDeque<*> && r == 2 },
            { _, q, r -> q !is java.util.LinkedList<*> && q !is java.util.ArrayDeque && r == 3 } // this is uncovered
        )
    }

    @Test
    fun testAddConcurrentLinkedQueue() {
        checkWithExceptionExecutionMatches(
            QueueUsages::addConcurrentLinkedQueue,
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> q != null && x != null && x in r.getOrNull()!! },
            { _, q, x, r -> q != null && x == null && r.isException<NullPointerException>() },
        )
    }
}