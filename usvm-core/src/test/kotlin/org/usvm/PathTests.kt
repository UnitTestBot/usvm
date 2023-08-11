package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PathTests {
    @Test
    fun smokeTest() {
        val initialState = mockk<TestState>()
        val fork = mockk<TestState>()

        val root = RootNode<TestState, Int>()

        val firstRealNode = root.propagateState(1, initialState)

        val updatedInitialState = firstRealNode.propagateState(statement = 2, initialState)
        val forkedState = firstRealNode.propagateState(statement = 3, fork)

        assertTrue { root.children.size == 1 }
        assertTrue { root.children.values.single() == firstRealNode }

        assertTrue { firstRealNode.states.isEmpty() }
        assertTrue { firstRealNode.children.size == 2 }

        assertTrue { updatedInitialState.states.single() == initialState }
        assertTrue { forkedState.states.single() == fork }

        assertTrue { updatedInitialState.children.isEmpty() }
        assertTrue { forkedState.children.isEmpty() }
    }

    @Test
    fun severalStatesInOneNode() {
        val firstState = mockk<TestState>()
        val secondState = mockk<TestState>()

        val root = RootNode<TestState, Int>()

        val firstRealNode = root.propagateState(1, firstState)

        val updatedFirstState = firstRealNode.propagateState(statement = 2, firstState)
        val updatedSecondState = firstRealNode.propagateState(statement = 2, secondState)

        assertSame(updatedFirstState, updatedSecondState)

        assertTrue { updatedFirstState.children.isEmpty() }
        assertTrue { updatedFirstState.states.size == 2 }
    }
}