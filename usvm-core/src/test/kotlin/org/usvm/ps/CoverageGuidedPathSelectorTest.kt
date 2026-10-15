package org.usvm.ps

import io.mockk.every
import org.junit.jupiter.api.Test
import org.usvm.PathNode
import org.usvm.PathSelectionStrategy
import org.usvm.TestInstruction
import org.usvm.TestState
import org.usvm.TestTarget
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.appGraph
import org.usvm.mockState
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.ExternalCoverageTargets
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoverageGuidedPathSelectorTest {
    @Test
    fun `external coverage reprioritizes every pending state without state updates`() {
        val targets = ExternalCoverageTargets(targets = listOf("left", "right"))
        val selector = selector(targets)
        selector.add(states = listOf("left", "right"))

        targets.markCovered(targets = listOf("left"))
        assertEquals(expected = "right", actual = selector.peek())

        targets.addTargets(targets = listOf("new-left"))
        targets.markCovered(targets = listOf("right"))
        assertEquals(expected = "left", actual = selector.peek())
        assertEquals(expected = setOf("left", "right"), actual = selector.drain().toSet())
    }

    @Test
    fun `stable evidence does not recalculate queued weights`() {
        val targets = ExternalCoverageTargets(targets = listOf("goal"))
        var evaluations = 0
        val selector = createCoverageGuidedPathSelector<String, String>(
            coverageTargets = targets,
            distance = { _, _ ->
                evaluations++
                0u
            },
        )
        selector.add(states = listOf("first", "second"))
        val afterAdd = evaluations

        selector.peek()
        selector.peek()
        assertFalse(targets.markCovered(targets = listOf("unknown")))
        selector.peek()

        assertEquals(expected = afterAdd, actual = evaluations)
    }

    @Test
    fun `saturated coverage retains all queued states and accepts later work`() {
        val targets = ExternalCoverageTargets(targets = listOf("left", "right"))
        val selector = selector(targets)
        selector.add(states = listOf("left", "right"))
        targets.markCovered(targets = listOf("left", "right"))

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
        val targets = ExternalCoverageTargets(targets = listOf(firstEdge, secondEdge))
        val firstPath = setOf(firstEdge)
        val secondPath = setOf(secondEdge)
        val selector = createCoverageGuidedPathSelector<Set<Edge>, Edge>(
            coverageTargets = targets,
            distance = { path, edge -> if (edge in path) 0u else UInt.MAX_VALUE },
        )
        selector.add(states = listOf(firstPath, secondPath))

        targets.markCovered(targets = listOf(firstEdge))

        assertSame(expected = secondPath, actual = selector.peek())
        assertEquals(expected = setOf(firstPath, secondPath), actual = selector.drain().toSet())
    }

    @Test
    fun `empty initial evidence does not invoke the distance function or remove states`() {
        val targets = ExternalCoverageTargets<String>(targets = emptyList())
        val selector = createCoverageGuidedPathSelector<String, String>(
            coverageTargets = targets,
            distance = { _, _ -> error("There is no known goal") },
        )
        selector.add(states = listOf("first", "second"))

        selector.peek()

        assertEquals(expected = setOf("first", "second"), actual = selector.drain().toSet())
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `draining stopped work does not refresh weights after new evidence`() {
        val targets = ExternalCoverageTargets(targets = listOf("left"))
        var stopped = false
        val selector = createCoverageGuidedPathSelector<String, String>(
            coverageTargets = targets,
            distance = { _, _ ->
                check(!stopped)
                0u
            },
        )
        selector.add(states = listOf("first", "second"))
        stopped = true
        targets.addTargets(targets = listOf("right"))

        val drained = selector.drain().toList()

        assertEquals(expected = setOf("first", "second"), actual = drained.toSet())
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `state updates additions and removals work across coverage revisions`() {
        val targets = ExternalCoverageTargets(targets = listOf("left", "right"))
        val distances = mutableMapOf("first" to 0u, "second" to 2u, "third" to 1u)
        val selector = createCoverageGuidedPathSelector<String, String>(
            coverageTargets = targets,
            distance = { state, _ -> distances.getValue(state) },
        )
        selector.add(states = listOf("first", "second"))
        assertEquals(expected = "first", actual = selector.peek())

        distances["first"] = 3u
        selector.update(state = "first")
        selector.add(states = listOf("third"))
        targets.markCovered(targets = listOf("left"))

        assertEquals(expected = "third", actual = selector.peek())
        selector.remove(state = "third")
        assertEquals(expected = "second", actual = selector.peek())
        assertEquals(expected = setOf("first", "second"), actual = selector.drain().toSet())
    }

    @Test
    fun `normal factory accepts external goals without replacing machine statistics`() {
        val statistics = CoverageStatistics<String, TestInstruction, TestState>(
            methods = setOf("method"),
            applicationGraph = graph,
        )
        val targets = ExternalCoverageTargets(targets = listOf(instruction(offset = 2), instruction(offset = 4)))
        targets.markCovered(targets = listOf(instruction(offset = 2)))
        val left = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        val right = mockState(id = 2u, startMethod = "method", startInstruction = 3)
        val selector = createPathSelector<String, TestInstruction, TestTarget, TestState>(
            initialState = left,
            options = options(),
            applicationGraph = graph,
            coverageStatisticsFactory = { statistics },
            cfgStatisticsFactory = { CfgStatisticsImpl(graph) },
            coverageTargetsFactory = { targets },
        )
        selector.add(states = listOf(right))
        assertSame(expected = right, actual = selector.peek())

        targets.markCovered(targets = listOf(instruction(offset = 4)))
        targets.addTargets(targets = listOf(instruction(offset = 1)))

        assertSame(expected = left, actual = selector.peek())
        assertEquals(expected = setOf(left, right), actual = selector.drain().toSet())
        assertEquals(expected = 0, actual = statistics.getTotalCoveredStatements())
    }

    @Test
    fun `map factory forwards external goals to the standard selector`() {
        val targets = ExternalCoverageTargets(targets = listOf(instruction(offset = 4)))
        val left = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        val right = mockState(id = 2u, startMethod = "method", startInstruction = 3)
        val selector = createPathSelector<String, TestInstruction, TestTarget, TestState>(
            initialStates = mapOf("method" to left),
            options = options(),
            applicationGraph = graph,
            timeStatistics = TimeStatistics(),
            cfgStatisticsFactory = { CfgStatisticsImpl(graph) },
            coverageTargetsFactory = { targets },
        )
        selector.add(states = listOf(right))

        assertSame(expected = right, actual = selector.peek())
    }

    @Test
    fun `default statistics refresh weights for states that were not stepped`() {
        val statistics = CoverageStatistics<String, TestInstruction, TestState>(
            methods = setOf("method"),
            applicationGraph = graph,
        )
        val left = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        val right = mockState(id = 2u, startMethod = "method", startInstruction = 3)
        val completed = mockState(id = 3u, startMethod = "method", startInstruction = 2)
        every { completed.pathNode } returns PathNode.root<TestInstruction>() +
            instruction(offset = 0) + instruction(offset = 1) + instruction(offset = 2)
        val selector = createPathSelector<String, TestInstruction, TestTarget, TestState>(
            initialState = left,
            options = options(),
            applicationGraph = graph,
            coverageStatisticsFactory = { statistics },
            cfgStatisticsFactory = { CfgStatisticsImpl(graph) },
        )
        selector.add(states = listOf(right))
        selector.peek()

        statistics.onStateTerminated(state = completed, stateReachable = true)

        assertSame(expected = right, actual = selector.peek())
        assertEquals(expected = 3, actual = statistics.getTotalCoveredStatements())
    }

    @Test
    fun `randomized closest selector also refreshes all pending weights`() {
        val targets = ExternalCoverageTargets(targets = listOf(instruction(offset = 4)))
        val left = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        val right = mockState(id = 2u, startMethod = "method", startInstruction = 3)
        val selector = createClosestToUncoveredPathSelector<String, TestInstruction, TestState>(
            coverageTargets = targets,
            cfgStatistics = CfgStatisticsImpl(graph),
            applicationGraph = graph,
            random = Random(seed = 42),
        )
        selector.add(states = listOf(left, right))
        assertSame(expected = right, actual = selector.peek())

        targets.markCovered(targets = listOf(instruction(offset = 4)))
        targets.addTargets(targets = listOf(instruction(offset = 1)))

        assertSame(expected = left, actual = selector.peek())
    }

    private fun selector(targets: ExternalCoverageTargets<String>): UPathSelector<String> =
        createCoverageGuidedPathSelector(
            coverageTargets = targets,
            distance = { state, target ->
                val destination = if (target == "new-left") "left" else target
                if (state == destination) 0u else 10u
            },
        )

    private fun instruction(offset: Int) = TestInstruction(method = "method", offset = offset)

    private fun options() = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED),
        exceptionsPropagation = false,
    )

    private val graph = appGraph {
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

    private data class Edge(val from: Int, val to: Int)
}
