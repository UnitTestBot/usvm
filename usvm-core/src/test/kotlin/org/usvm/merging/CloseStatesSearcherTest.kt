package org.usvm.merging

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.usvm.PathNode
import org.usvm.TestInstruction
import org.usvm.TestState
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.ps.ExecutionTreeTracker
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.distances.CfgStatisticsImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloseStatesSearcherTest {
    @Test
    fun `no merging`() {
        val graph = mapOf(
            1 to listOf(2, 3),
            2 to listOf(4),
            3 to listOf(4),
            4 to listOf(5)
        )
        val (searcher, states) = buildSearcherAndStates(
            0,
            graph,
            listOf(1, 2),
            listOf(1, 3)
        )
        val closeStates = searcher.findCloseStates(states[0]).toList()
        assertTrue(closeStates.isEmpty())
    }

    @Test
    fun `bamboo merging`() {
        val graph = mapOf(
            1 to listOf(2),
            2 to listOf(3),
            3 to listOf(4),
            4 to listOf(5),
            5 to listOf(6)
        )
        val (searcher, states) = buildSearcherAndStates(
            1,
            graph,
            listOf(1, 2, 3),
            listOf(1, 2, 3, 4, 5)
        )
        val (closeToFirst, closeToSecond) = states.map { searcher.findCloseStates(it).toList() }
        assertTrue(closeToFirst.isEmpty())

        assertEquals(listOf(states[0]), closeToSecond)
    }

    @Test
    fun `if merging`() {
        val graph = mapOf(
            1 to listOf(2, 3),
            2 to listOf(4),
            3 to listOf(4),
            4 to listOf(5)
        )
        val (searcher, states) = buildSearcherAndStates(
            1,
            graph,
            listOf(1, 2),
            listOf(1, 5)
        )
        val (closeToFirst, closeToSecond) = states.map { searcher.findCloseStates(it).toList() }

        assertTrue(closeToFirst.isEmpty())
        assertEquals(listOf(states[0]), closeToSecond)
    }

    @Test
    fun `loop merging`() {
        val graph = mapOf(
            1 to listOf(2, 4),
            2 to listOf(3),
            3 to listOf(1),
            4 to listOf(5)
        )
        val (searcher, states) = buildSearcherAndStates(
            1,
            graph,
            listOf(1, 2, 3, 1, 2, 3, 2),
            listOf(1, 2, 3, 1, 4)
        )
        val (closeToFirst, closeToSecond) = states.map { searcher.findCloseStates(it).toList() }

        assertTrue(closeToFirst.isEmpty())
        assertEquals(listOf(states[0]), closeToSecond)
    }

    @Test
    fun `nested loops merging`() {
        val graph = mapOf(
            1 to listOf(2, 8),
            2 to listOf(3),
            3 to listOf(4, 7),
            4 to listOf(5),
            5 to listOf(6),
            6 to listOf(3),
            7 to listOf(1),
            8 to listOf(9)
        )
        val (searcher, states) = buildSearcherAndStates(
            1,
            graph,
            listOf(1, 2, 3, 5, 6),
            listOf(1, 2, 3, 7),
            listOf(1, 8)
        )
        val (closeToFirst, closeToSecond, closeToThird) = states.map { searcher.findCloseStates(it).toList() }

        assertEquals(listOf(states[1]), closeToFirst)
        assertEquals(listOf(states[0]), closeToSecond)
        assertEquals(setOf(states[0], states[1]), closeToThird.toSet())

    }

    @Test
    fun `switch merging`() {
        val graph = mapOf(
            1 to listOf(2, 3, 4),
            2 to listOf(5),
            3 to listOf(6),
            4 to listOf(7),
            5 to listOf(8),
            6 to listOf(8),
            7 to listOf(8),
            8 to listOf(9)
        )
        val (searcher, states) = buildSearcherAndStates(
            1,
            graph,
            listOf(1, 2, 5, 8, 9),
            listOf(1, 2),
            listOf(1, 3, 6),
            listOf(1, 4, 7),
        )
        val (closeToFirst, closeToSecond, closeToThird, closeToFourth) = states.map {
            searcher.findCloseStates(it).toList()
        }

        assertEquals(setOf(states[1], states[2], states[3]), closeToFirst.toSet())
        assertTrue(closeToSecond.isEmpty())
        assertTrue(closeToThird.isEmpty())
        assertTrue(closeToFourth.isEmpty())
    }

    private fun buildSearcherAndStates(
        startStmt: Int,
        adjacentStmts: Map<Int, List<Int>>,
        vararg statePaths: List<Int>,
    ): Pair<CloseStatesSearcher<TestState>, List<TestState>> {
        val graph = TestApplicationGraph(startStmt, adjacentStmts)
        val rootNode = PathNode.root<TestInstruction>()
        val states = statePaths.map { statePath ->
            val pathNode = statePath.fold(rootNode) { path, stmt -> path + TestInstruction("", stmt) }
            val ctxMock = mockk<UContext<*>>()
            every { ctxMock.getNextStateId() } returns 0u
            val callStack = UCallStack<String, TestInstruction>("")
            val spyk = spyk(
                TestState(ctxMock, MutabilityOwnership(), callStack, mockk(), mockk(), emptyList(), pathNode, mockk())
            )
            spyk
        }
        val executionTreeTracker = ExecutionTreeTracker<TestState, TestInstruction>(rootNode).apply { add(states) }
        val cfgStatistics = CfgStatisticsImpl(graph)
        val searcher = CloseStatesSearcherImpl(
            executionTreeTracker,
            cfgStatistics,
        )
        return searcher to states
    }

    class TestApplicationGraph(
        entryPoint: Int,
        private val adjacentStmts: Map<Int, List<Int>>,
    ) : ApplicationGraph<String, TestInstruction> {
        private val reversed = adjacentStmts
            .flatMap { (key, value) -> value.map { key to it } }
            .groupBy({ it.second }) { it.first }

        private val entryPoints = sequenceOf(entryPoint)
        private val exitPoints = reversed.keys - adjacentStmts.keys
        private val statements = reversed.keys + adjacentStmts.keys


        override fun predecessors(node: TestInstruction): Sequence<TestInstruction> =
            reversed.getOrElse(node.offset, ::emptyList).asSequence().map { TestInstruction("", it) }

        override fun successors(node: TestInstruction): Sequence<TestInstruction> =
            adjacentStmts.getOrElse(node.offset, ::emptyList).asSequence().map { TestInstruction("", it) }

        override fun callees(node: TestInstruction): Sequence<String> = emptySequence()

        override fun callers(method: String): Sequence<TestInstruction> = emptySequence()

        override fun entryPoints(method: String): Sequence<TestInstruction> =
            entryPoints.map { TestInstruction("", it) }

        override fun exitPoints(method: String): Sequence<TestInstruction> =
            exitPoints.asSequence().map { TestInstruction("", it) }

        override fun methodOf(node: TestInstruction): String = ""

        override fun statementsOf(method: String): Sequence<TestInstruction> =
            statements.asSequence().map { TestInstruction("", it) }
    }
}
