package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.*
import kotlin.collections.ArrayDeque

class QueueUsagesTest : JavaMethodTestRunner() {
    @Test
    fun testCreateArrayDeque() = disableTest("Unexpected expr of type void: JcLambdaExpr") {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::createArrayDeque,
            eq(3),
            { _, init, next, r -> init == null && next == null && r.isException<NullPointerException>() },
            { _, init, next, r -> init != null && next == null && r.isException<NullPointerException>() },
            { _, init, next, r -> init != null && next != null && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testCreateLinkedList() {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::createLinkedList,
            eq(1),
            { _, _, _, r -> r.getOrNull()!! == 2 },
        )
    }

    @Test
    fun testCreateLinkedBlockingDeque() = disableTest("Some properties were not discovered at positions (from 0): [0, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::createLinkedBlockingDeque,
            eq(3),
            { _, init, next, r -> init == null && next == null && r.isException<NullPointerException>()  },
            { _, init, next, r -> init != null && next == null && r.isException<NullPointerException>() },
            { _, init, next, r -> init != null && next != null && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testContainsQueue() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::containsQueue,
            eq(3),
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> x in q && r.getOrNull() == 1 },
            { _, q, x, r -> x !in q && r.getOrNull() == 0 },
        )
    }

    @Test
    fun testAddQueue() = disableTest("Expected exactly 3 executions, but 11 found") {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::addQueue,
            eq(3),
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> q != null && x in r.getOrNull()!! },
            { _, q, x, r -> q != null && x == null && r.isException<NullPointerException>() },        )
    }

    @Test
    fun testAddAllQueue() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::addAllQueue,
            eq(3),
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> q != null && x in r.getOrNull()!! }, // we can cover this line with x == null or x != null
            { _, q, x, r -> q != null && x == null && r.isException<NullPointerException>() },
        )
    }

    @Test
    fun testCastQueueToDeque() {
        checkDiscoveredProperties(
            QueueUsages::castQueueToDeque,
            eq(2),
            { _, q, r -> q !is Deque<*> && r == null },
            { _, q, r -> q is Deque<*> && r is Deque<*> },
        )
    }

    @Test
    fun testCheckSubtypesOfQueue() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredProperties(
            QueueUsages::checkSubtypesOfQueue,
            eq(4),
            { _, q, r -> q == null && r == 0 },
            { _, q, r -> q is LinkedList<*> && r == 1 },
            { _, q, r -> q is ArrayDeque<*> && r == 2 },
            { _, q, r -> q !is LinkedList<*> && q !is ArrayDeque<*> && r == 3 }
        )
    }

    @Test
    fun testCheckSubtypesOfQueueWithUsage() = disableTest("Some properties were not discovered at positions (from 0): [1, 2, 3]") {
        checkDiscoveredProperties(
            QueueUsages::checkSubtypesOfQueueWithUsage,
            eq(4),
            { _, q, r -> q == null && r == 0 },
            { _, q, r -> q is LinkedList<*> && r == 1 },
            { _, q, r -> q is ArrayDeque<*> && r == 2 },
            { _, q, r -> q !is LinkedList<*> && q !is ArrayDeque<*> && r == 3 } // this is uncovered
        )
    }

    @Test
    fun testAddConcurrentLinkedQueue() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            QueueUsages::addConcurrentLinkedQueue,
            eq(3),
            { _, q, _, r -> q == null && r.isException<NullPointerException>() },
            { _, q, x, r -> q != null && x != null && x in r.getOrNull()!! },
            { _, q, x, r -> q != null && x == null && r.isException<NullPointerException>() },
        )
    }
}