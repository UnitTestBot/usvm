package org.usvm.ps

import io.mockk.every
import org.junit.jupiter.api.Test
import org.usvm.*
import org.usvm.TestInstruction
import org.usvm.appGraph
import org.usvm.statistics.DistanceStatistics
import kotlin.test.assertEquals

internal class ClosestToTargetsPathSelectorIntegrationTests {

    private val appGraph1 = appGraph {
        method("A", 8) {
            entryPoint(0)
            edge(0, 1)
            edge(0, 2)
            edge(1, 7)
            edge(7, 3)
            edge(2, 4)
            edge(4, 5)
            edge(5, 6)
            edge(6, 3)
            exitPoint(3)
        }
    }

    @Test
    fun `Smoke test, single target and two states, one is closer`() {
        val pathSelector = createClosestToTargetsPathSelector<String, TestInstruction, TestTarget, TestState>(DistanceStatistics(appGraph1))
        val target = TestTarget("A", 3)

        val state1 = mockState(1u, TestInstruction("A", 1), UCallStack("A"), listOf(target))
        val state2 = mockState(2u, TestInstruction("A", 2), UCallStack("A"), listOf(target))

        pathSelector.add(listOf(state1, state2))
        assertEquals(state1, pathSelector.peek())
    }

    @Test
    fun `Multiple targets smoke test`() {
        val pathSelector = createClosestToTargetsPathSelector<String, TestInstruction, TestTarget, TestState>(DistanceStatistics(appGraph1))
        val target = TestTarget("A", 4).apply {
            addChild(TestTarget("A", 3))
        }

        val state1 = mockState(1u, TestInstruction("A", 1), UCallStack("A"), listOf(target))
        val state2 = mockState(2u, TestInstruction("A", 2), UCallStack("A"), listOf(target))

        pathSelector.add(listOf(state1, state2))
        assertEquals(state2, pathSelector.peek())
    }

    @Test
    fun `State steps to target and becomes not closest`() {
        val pathSelector = createClosestToTargetsPathSelector<String, TestInstruction, TestTarget, TestState>(DistanceStatistics(appGraph1))
        val target1 = TestTarget("A", 3)

        val target2 = TestTarget("A", 4).apply {
            addChild(TestTarget("A", 3))
        }

        val state1 = mockState(1u, TestInstruction("A", 1), UCallStack("A"), listOf(target1))
        val state2 = mockState(2u, TestInstruction("A", 2), UCallStack("A"), listOf(target2))

        pathSelector.add(listOf(state1, state2))
        assertEquals(state2, pathSelector.peek())
        target2.reach(state2)
        every { state2.currentStatement } returns TestInstruction("A", 4)
        pathSelector.update(state2)
        assertEquals(state1, pathSelector.peek())
    }
}
