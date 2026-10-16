package org.usvm.ps

import org.junit.jupiter.api.Test
import org.usvm.TestInstruction
import org.usvm.TestState
import org.usvm.appGraph
import org.usvm.mockState
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoverageGuidedPathSelectorTest {
    @Test
    fun `reported coverage reprioritizes every pending state after a completed selection`() {
        val selector = selector(targets = listOf("left", "right"))
        selector.add(states = listOf("left", "right"))

        assertEquals(expected = "left", actual = selector.peek())

        selector.markCovered(targets = listOf("left"))
        selector.update(state = "left")

        assertEquals(expected = "right", actual = selector.peek())

        selector.addTargets(targets = listOf("new-left"))
        selector.markCovered(targets = listOf("right"))
        selector.update(state = "right")

        assertEquals(expected = "left", actual = selector.peek())
        assertEquals(expected = setOf("left", "right"), actual = selector.drain().toSet())
    }

    @Test
    fun `unchanged coverage does not recalculate queued weights`() {
        var evaluations = 0
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("goal"),
            distance = { _, _ ->
                evaluations++
                0u
            },
        )
        selector.add(states = listOf("first", "second"))
        val afterAdd = evaluations

        selector.peek()
        selector.peek()
        assertFalse(selector.markCovered(targets = listOf("unknown")))
        assertFalse(selector.addTargets(targets = listOf("goal")))
        selector.peek()

        assertEquals(expected = afterAdd, actual = evaluations)
    }

    @Test
    fun `multiple reports are applied together at the refresh boundary`() {
        var refreshing = false
        var evaluations = 0
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("first", "second", "third"),
            distance = { state, target ->
                if (refreshing) {
                    check(target == "third")
                    evaluations++
                }

                if (state == target) 0u else 10u
            },
            priorityRefreshInterval = 1,
        )
        selector.add(states = listOf("first", "third"))
        val selected = selector.peek()

        selector.markCovered(targets = listOf("first"))
        selector.markCovered(targets = listOf("second"))
        selector.update(state = selected)
        refreshing = true

        assertEquals(expected = "third", actual = selector.peek())
        assertEquals(expected = 2, actual = evaluations)
    }

    @Test
    fun `saturated coverage retains all queued states and accepts later work`() {
        val selector = selector(targets = listOf("left", "right"))
        selector.add(states = listOf("left", "right"))
        selector.markCovered(targets = listOf("left", "right"))

        selector.peek()
        val drained = selector.drain().toList()
        selector.add(states = listOf("later"))

        assertEquals(expected = setOf("left", "right"), actual = drained.toSet())
        assertEquals(expected = 2, actual = drained.size)
        assertEquals(expected = "later", actual = selector.peek())
    }

    @Test
    fun `edges sharing a successor remain distinct coverage goals`() {
        val firstEdge = Edge(from = 1, to = 3)
        val secondEdge = Edge(from = 2, to = 3)
        val firstPath = setOf(firstEdge)
        val secondPath = setOf(secondEdge)
        val selector = CoverageGuidedPathSelector<Set<Edge>, Edge>(
            targets = listOf(firstEdge, secondEdge),
            distance = { path, edge -> if (edge in path) 0u else UInt.MAX_VALUE },
            priorityRefreshInterval = 1,
        )
        selector.add(states = listOf(firstPath, secondPath))
        val selected = selector.peek()

        selector.markCovered(targets = listOf(firstEdge))
        selector.update(state = selected)

        assertSame(expected = secondPath, actual = selector.peek())
        assertEquals(expected = setOf(firstPath, secondPath), actual = selector.drain().toSet())
    }

    @Test
    fun `empty goals do not invoke the distance function or remove states`() {
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = emptyList(),
            distance = { _, _ -> error("There is no known goal") },
        )
        selector.add(states = listOf("first", "second"))

        selector.peek()

        assertEquals(expected = setOf("first", "second"), actual = selector.drain().toSet())
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `draining stopped work does not refresh weights after coverage changes`() {
        var stopped = false
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("left"),
            distance = { _, _ ->
                check(!stopped)
                0u
            },
        )
        selector.add(states = listOf("first", "second"))
        stopped = true
        selector.addTargets(targets = listOf("right"))

        val drained = selector.drain().toList()

        assertEquals(expected = setOf("first", "second"), actual = drained.toSet())
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `state updates additions and removals work after coverage changes`() {
        val distances = mutableMapOf("first" to 0u, "second" to 2u, "third" to 1u)
        val selector = CoverageGuidedPathSelector<String, String>(
            targets = listOf("left", "right"),
            distance = { state, _ -> distances.getValue(state) },
        )
        selector.add(states = listOf("first", "second"))
        assertEquals(expected = "first", actual = selector.peek())

        distances["first"] = 3u
        selector.update(state = "first")
        selector.add(states = listOf("third"))
        selector.markCovered(targets = listOf("left"))

        assertEquals(expected = "third", actual = selector.peek())
        selector.remove(state = "third")
        assertEquals(expected = "second", actual = selector.peek())
        assertEquals(expected = setOf("first", "second"), actual = selector.drain().toSet())
    }

    @Test
    fun `coverage reports are idempotent and unknown goals are ignored`() {
        val selector = selector(targets = listOf("first", "second"))

        assertFalse(selector.markCovered(targets = listOf("unknown")))
        assertTrue(selector.markCovered(targets = listOf("first")))
        assertFalse(selector.markCovered(targets = listOf("first")))

        assertEquals(expected = setOf("second"), actual = selector.uncoveredTargets)
    }

    @Test
    fun `new goals can be registered without resurrecting covered goals`() {
        val selector = selector(targets = listOf("first"))
        selector.markCovered(targets = listOf("first"))

        assertTrue(selector.addTargets(targets = listOf("first", "second", "second")))
        assertFalse(selector.addTargets(targets = listOf("first", "second")))

        assertEquals(expected = setOf("second"), actual = selector.uncoveredTargets)
    }

    @Test
    fun `goal snapshots and constructor inputs do not share mutable storage`() {
        val initial = mutableListOf("first", "second")
        val selector = selector(targets = initial)
        val snapshot = selector.uncoveredTargets

        initial.clear()
        selector.markCovered(targets = listOf("first", "second"))

        assertEquals(expected = setOf("first", "second"), actual = snapshot)
        assertTrue(selector.uncoveredTargets.isEmpty())
    }

    @Test
    fun `caller can reuse existing CFG distance calculators`() {
        val graph = appGraph {
            method(name = "method", instructionsCount = 5) {
                entryPoint(offset = 0)
                edge(from = 0, to = 1)
                edge(from = 0, to = 3)
                edge(from = 1, to = 2)
                edge(from = 3, to = 4)
                exitPoint(offset = 2)
                exitPoint(offset = 4)
            }
        }
        val cfgStatistics = CfgStatisticsImpl(graph)
        val distances = MultiTargetDistanceCalculator<String, TestInstruction, UInt> { target ->
            CallStackDistanceCalculator(
                targets = listOf(target),
                cfgStatistics = cfgStatistics,
                applicationGraph = graph,
            )
        }
        val firstTarget = TestInstruction(method = "method", offset = 2)
        val secondTarget = TestInstruction(method = "method", offset = 4)
        val selector = CoverageGuidedPathSelector<TestState, TestInstruction>(
            targets = listOf(firstTarget, secondTarget),
            distance = { state, target ->
                distances.calculateDistance(state.currentStatement, state.callStack, target)
            },
            priorityRefreshInterval = 1,
        )
        val left = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        val right = mockState(id = 2u, startMethod = "method", startInstruction = 3)
        selector.add(states = listOf(left, right))
        val selected = selector.peek()

        selector.markCovered(targets = listOf(firstTarget))
        selector.update(state = selected)

        assertSame(expected = right, actual = selector.peek())
        assertEquals(expected = setOf(left, right), actual = selector.drain().toSet())
    }

    @Test
    fun `coverage guidance composes with a standard selector on shared states`() {
        val coverage = selector(targets = listOf("left", "right"))
        val combined = InterleavedPathSelector(selectors = listOf(BfsPathSelector(), coverage))
        combined.add(states = listOf("left", "right"))
        assertEquals(expected = "left", actual = combined.peek())

        coverage.markCovered(targets = listOf("left"))
        combined.update(state = "left")

        assertEquals(expected = "right", actual = combined.peek())
        combined.add(states = listOf("fork"))
        combined.remove(state = "right")
        assertEquals(expected = setOf("left", "fork"), actual = combined.drain().toSet())
        assertTrue(combined.isEmpty())
    }

    private fun selector(targets: Collection<String>) = CoverageGuidedPathSelector<String, String>(
        targets = targets,
        distance = { state, target ->
            val destination = if (target == "new-left") "left" else target
            if (state == destination) 0u else 10u
        },
        priorityRefreshInterval = 1,
    )

    private data class Edge(val from: Int, val to: Int)
}
