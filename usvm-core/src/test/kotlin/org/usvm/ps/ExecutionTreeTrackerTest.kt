package org.usvm.ps

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.usvm.PathNode
import org.usvm.TestInstruction
import org.usvm.TestState
import kotlin.test.assertTrue

class ExecutionTreeTrackerTest {
    @Test
    fun smokeTest() {
        val (stateA, stateB) = List(2) {
            mockk<TestState> {
                every { pathNode } answers { callOriginal() }
                every { pathNode = any() } answers { callOriginal() }
            }
        }
        val root = PathNode.root<TestInstruction>()

        val pathA = root + TestInstruction("", 1)
        val pathB = root + TestInstruction("", 2)

        stateA.pathNode = pathA
        stateB.pathNode = pathB

        val treeTracker = ExecutionTreeTracker<TestState, TestInstruction>(root) { it.pathNode }
        treeTracker.add(listOf(stateA, stateB))

        assertTrue { treeTracker.statesAt(root).isEmpty() }
        assertTrue { treeTracker.childrenOf(root).size == 2 }

        assertTrue { treeTracker.statesAt(pathA).single() == stateA }
        assertTrue { treeTracker.childrenOf(pathA).isEmpty() }

        assertTrue { treeTracker.statesAt(pathB).single() == stateB }
        assertTrue { treeTracker.childrenOf(pathB).isEmpty() }
    }

    @Test
    fun severalStatesInOneNode() {
        val (stateA, stateB) = List(2) {
            mockk<TestState> {
                every { pathNode } answers { callOriginal() }
                every { pathNode = any() } answers { callOriginal() }
            }
        }

        val root = PathNode.root<TestInstruction>()

        val path = root + TestInstruction("", 2)
        val samePath = root + TestInstruction("", 2)

        stateA.pathNode = path
        stateB.pathNode = samePath

        val tracker = ExecutionTreeTracker<TestState, TestInstruction>(root) { it.pathNode }
        tracker.add(listOf(stateA, stateB))

        assertTrue { tracker.childrenOf(path).isEmpty() }
        assertTrue { tracker.statesAt(path).size == 2 }
    }
}