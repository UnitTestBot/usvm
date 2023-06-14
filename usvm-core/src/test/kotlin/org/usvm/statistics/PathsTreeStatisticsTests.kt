package org.usvm.statistics

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.usvm.UState
import org.usvm.hash
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathsTreeStatisticsTests {

    @Test
    fun smokeTest() {
        val initialState = mockk<UState<*, *, Any, Any>>()
        val fork = mockk<UState<*, *, Any, Any>>()
        every { initialState.id } returns 0u
        every { fork.id } returns 1u
        val pathsTreeStatistics = PathsTreeStatistics(initialState)
        pathsTreeStatistics.onStateForked(initialState, listOf(fork))
        assertNull(pathsTreeStatistics.root.state)
        assertEquals(2, pathsTreeStatistics.root.children.size)
        assertTrue(pathsTreeStatistics.root.children.any { it.state == initialState && it.children.isEmpty() })
        assertTrue(pathsTreeStatistics.root.children.any { it.state == fork && it.children.isEmpty() })
    }

    @Test
    fun linearForksTest() {
        val initialState = mockk<UState<*, *, Any, Any>>()
        every { initialState.id } returns 0u
        val pathsTreeStatistics = PathsTreeStatistics(initialState)
        val forks = mutableListOf<UState<*, *, Any, Any>>()
        for (i in 1 until 100) {
            val fork = mockk<UState<*, *, Any, Any>>()
            every { fork.id } returns i.toUInt()
            pathsTreeStatistics.onStateForked(initialState, listOf(fork))
            forks.add(fork)
        }
        var current = pathsTreeStatistics.root
        for (i in 1 until 100) {
            assertEquals(2, current.children.size)
            val next = current.children.single { it.state == null || it.state == initialState }
            assertTrue(current.children.any { it.state == forks[i - 1] && it.children.isEmpty() })
            current = next
        }
        assertEquals(initialState, current.state)
        assertTrue(current.children.isEmpty())
    }

    private fun <State> PathsTreeNode<State>.traversal(): Sequence<PathsTreeNode<State>> {
        val me = this
        return sequence {
            yield(me)
            yieldAll(me.children.flatMap { it.traversal() })
        }
    }

    @Test
    fun randomForksTest() {
        val initialState = mockk<UState<*, *, Any, Any>>()
        every { initialState.id } returns 0u
        val pathsTreeStatistics = PathsTreeStatistics(initialState)
        val states = mutableListOf(initialState)
        for (i in 1 until 100) {
            val stateIdToFork = (hash(i) % i)
            val stateToFork = states[stateIdToFork]
            val forkedStateId = i.toUInt()
            val forkedState = mockk<UState<*, *, Any, Any>>()
            every { forkedState.id } returns forkedStateId
            pathsTreeStatistics.onStateForked(stateToFork, listOf(forkedState))
            assertNotNull(pathsTreeStatistics.root.traversal().singleOrNull {
                it.children.singleOrNull { it.state == stateToFork } != null &&
                    it.children.singleOrNull { it.state == forkedState } != null
            })
        }
    }

    @Test
    fun multipleForksTest() {
        val initialState = mockk<UState<*, *, Any, Any>>()
        every { initialState.id } returns 0u
        val pathsTreeStatistics = PathsTreeStatistics(initialState)

        val fork1 = mockk<UState<*, *, Any, Any>>()
        val fork2 = mockk<UState<*, *, Any, Any>>()
        val fork3 = mockk<UState<*, *, Any, Any>>()
        every { fork1.id } returns 1u
        every { fork2.id } returns 2u
        every { fork3.id } returns 3u
        pathsTreeStatistics.onStateForked(initialState, listOf(fork1, fork2, fork3))
        assertNull(pathsTreeStatistics.root.state)
        assertEquals(4, pathsTreeStatistics.root.children.size)
        assertTrue(pathsTreeStatistics.root.children.any { it.state == initialState && it.children.isEmpty() })
        assertTrue(pathsTreeStatistics.root.children.any { it.state == fork1 && it.children.isEmpty() })
        assertTrue(pathsTreeStatistics.root.children.any { it.state == fork2 && it.children.isEmpty() })
        assertTrue(pathsTreeStatistics.root.children.any { it.state == fork3 && it.children.isEmpty() })

        val fork4 = mockk<UState<*, *, Any, Any>>()
        val fork5 = mockk<UState<*, *, Any, Any>>()
        val fork6 = mockk<UState<*, *, Any, Any>>()
        every { fork4.id } returns 4u
        every { fork5.id } returns 5u
        every { fork6.id } returns 6u
        pathsTreeStatistics.onStateForked(fork2, listOf(fork4, fork5, fork6))
        assertNull(pathsTreeStatistics.root.state)
        assertEquals(4, pathsTreeStatistics.root.children.size)
        assertTrue(pathsTreeStatistics.root.children.any { it.state == initialState && it.children.isEmpty() })
        assertTrue(pathsTreeStatistics.root.children.any { it.state == fork1 && it.children.isEmpty() })
        assertTrue(pathsTreeStatistics.root.children.any { it.state == fork3 && it.children.isEmpty() })
        val secondForkNode = pathsTreeStatistics.root.children.single { it.state == null }
        assertEquals(4, secondForkNode.children.size)
        assertTrue(secondForkNode.children.any { it.state == fork2 && it.children.isEmpty() })
        assertTrue(secondForkNode.children.any { it.state == fork4 && it.children.isEmpty() })
        assertTrue(secondForkNode.children.any { it.state == fork5 && it.children.isEmpty() })
        assertTrue(secondForkNode.children.any { it.state == fork6 && it.children.isEmpty() })
    }

    @Test
    fun emptyForksTest() {
        val initialState = mockk<UState<*, *, Any, Any>>()
        every { initialState.id } returns 0u
        val pathsTreeStatistics = PathsTreeStatistics(initialState)
        pathsTreeStatistics.onStateForked(initialState, emptyList())
        pathsTreeStatistics.onStateForked(initialState, emptyList())
        pathsTreeStatistics.onStateForked(initialState, emptyList())
        var previous: PathsTreeNode<UState<*, *, Any, Any>>? = null
        var currentNode = pathsTreeStatistics.root
        for (i in 0 until 3) {
            assertNull(currentNode.state)
            assertEquals(previous, currentNode.parent)
            assertEquals(1, currentNode.children.size)
            previous = currentNode
            currentNode = currentNode.children.single()
        }
        assertEquals(previous, currentNode.parent)
        assertEquals(initialState, currentNode.state)
        assertTrue(currentNode.children.isEmpty())
    }
}
