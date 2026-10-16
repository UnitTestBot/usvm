package org.usvm.ps

import io.mockk.every
import org.junit.jupiter.api.Test
import org.usvm.PathNode
import org.usvm.PathSelectionStrategy
import org.usvm.TestInstruction
import org.usvm.TestState
import org.usvm.UMachineOptions
import org.usvm.appGraph
import org.usvm.mockState
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClosestToUncoveredRandomPathSelectorTest {
    private val graph = appGraph {
        method(name = "method", instructionsCount = 7) {
            entryPoint(offset = 0)
            edge(from = 0, to = 1)
            edge(from = 1, to = 2)
            edge(from = 0, to = 3)
            edge(from = 3, to = 4)
            edge(from = 4, to = 5)
            edge(from = 5, to = 6)
            exitPoint(offset = 2)
            exitPoint(offset = 6)
        }
    }

    @Test
    fun `coverage and peek never recalculate queued weights while update touches only its state`() {
        val cfg = CfgStatisticsImpl(graph)
        val evaluated = mutableListOf<TestInstruction>()
        val countedCfg = object : CfgStatistics<String, TestInstruction> by cfg {
            override fun getShortestDistanceToExit(method: String, stmtFrom: TestInstruction): UInt {
                evaluated.add(stmtFrom)
                return cfg.getShortestDistanceToExit(method, stmtFrom)
            }
        }
        val leftGoal = TestInstruction(method = "method", offset = 1)
        val rightGoal = TestInstruction(method = "method", offset = 6)
        val calculator = CallStackDistanceCalculator(
            targets = listOf(leftGoal, rightGoal),
            cfgStatistics = countedCfg,
            applicationGraph = graph,
        )
        val nativeCoverage = CoverageStatistics<String, TestInstruction, TestState>(
            methods = setOf("method"),
            applicationGraph = graph,
        )
        val left = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        val right = mockState(id = 2u, startMethod = "method", startInstruction = 3)
        val selector = createPathSelector(
            initialState = left,
            options = UMachineOptions(
                pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
                randomSeed = 0,
            ),
            applicationGraph = graph,
            coverageStatisticsFactory = { nativeCoverage },
            cfgStatisticsFactory = { countedCfg },
            coverageDistanceCalculatorFactory = { calculator },
        )
        selector.add(states = listOf(right))
        assertEquals(expected = listOf(left.currentStatement, right.currentStatement), actual = evaluated)
        assertSame(expected = left, actual = selector.peek())
        evaluated.clear()

        assertTrue(calculator.removeTarget(method = "method", statement = leftGoal))
        repeat(256) { selector.peek() }

        assertTrue(evaluated.isEmpty())
        assertEquals(expected = 0, actual = nativeCoverage.getTotalCoveredStatements())

        selector.update(state = left)

        assertEquals(expected = listOf(left.currentStatement), actual = evaluated)
        assertSame(expected = right, actual = selector.peek())
        evaluated.clear()

        val fork = mockState(id = 3u, startMethod = "method", startInstruction = 2)
        selector.add(states = listOf(fork))

        assertEquals(expected = listOf(fork.currentStatement), actual = evaluated)
        assertSame(expected = right, actual = selector.peek())
        assertEquals(expected = setOf(left, right, fork), actual = selector.drain().toHashSet())
    }

    @Test
    fun `native coverage still removes targets from the supplied calculator`() {
        val cfg = CfgStatisticsImpl(graph)
        val goal = TestInstruction(method = "method", offset = 1)
        val calculator = CallStackDistanceCalculator(
            targets = listOf(goal),
            cfgStatistics = cfg,
            applicationGraph = graph,
        )
        val state = mockState(id = 1u, startMethod = "method", startInstruction = 1)
        every { state.pathNode } returns PathNode.root<TestInstruction>() + goal
        val coverage = CoverageStatistics<String, TestInstruction, TestState>(
            methods = setOf("method"),
            applicationGraph = graph,
        )
        val selector = createPathSelector(
            initialState = state,
            options = UMachineOptions(
                pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
            ),
            applicationGraph = graph,
            coverageStatisticsFactory = { coverage },
            cfgStatisticsFactory = { cfg },
            coverageDistanceCalculatorFactory = { calculator },
        )

        coverage.onStateTerminated(state = state, stateReachable = true)
        selector.update(state = state)

        assertEquals(expected = 1, actual = coverage.getTotalCoveredStatements())
        assertEquals(
            expected = UInt.MAX_VALUE,
            actual = calculator.calculateDistance(state.currentStatement, state.callStack),
        )
        assertSame(expected = state, actual = selector.peek())
        assertEquals(expected = listOf(state), actual = selector.drain().toList())
    }

    @Test
    fun `covered prefixes can still lead to uncovered successors`() {
        val goal = TestInstruction(method = "method", offset = 6)
        val prefix = mockState(id = 1u, startMethod = "method", startInstruction = 3)
        val calculator = CallStackDistanceCalculator(
            targets = listOf(prefix.currentStatement, goal),
            cfgStatistics = CfgStatisticsImpl(graph),
            applicationGraph = graph,
        )

        calculator.removeTarget(method = "method", statement = prefix.currentStatement)

        assertEquals(
            expected = 3u,
            actual = calculator.calculateDistance(prefix.currentStatement, prefix.callStack),
        )
    }
}
