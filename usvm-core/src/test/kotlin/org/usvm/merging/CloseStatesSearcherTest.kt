package org.usvm.merging

import org.usvm.PathNode
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
        val (closeToFirst, closeToSecond, closeToThird, closeToFourth) = states.map { searcher.findCloseStates(it).toList() }

        assertEquals(setOf(states[1], states[2], states[3]), closeToFirst.toSet())
        assertTrue(closeToSecond.isEmpty())
        assertTrue(closeToThird.isEmpty())
        assertTrue(closeToFourth.isEmpty())
    }

    private fun buildSearcherAndStates(
        startStmt: Int,
        adjacentStmts: Map<Int, List<Int>>,
        vararg statePaths: List<Int>,
    ): Pair<CloseStatesSearcher<State>, List<State>> {
        val graph = TestApplicationGraph(startStmt, adjacentStmts)
        val rootNode = PathNode.root<Int>()
        val states = statePaths.map { statePath ->
            val pathNode = statePath.fold(rootNode) { path, stmt -> path + stmt }
            State(pathNode)
        }
        val executionTreeTracker = ExecutionTreeTracker(rootNode, State::pathNode).apply { add(states) }
        val cfgStatistics = CfgStatisticsImpl(graph)
        val searcher = CloseStatesSearcherImpl(
            executionTreeTracker,
            State::pathNode,
            { },
            cfgStatistics,
        )
        return searcher to states
    }

    class State(
        val pathNode: PathNode<Int>,
    )

    class TestApplicationGraph(
        entryPoint: Int,
        private val adjacentStmts: Map<Int, List<Int>>,
    ) : ApplicationGraph<Unit, Int> {
        private val reversed = adjacentStmts
            .flatMap { (key, value) -> value.map { key to it } }
            .groupBy({ it.second }) { it.first }

        private val entryPoints = sequenceOf(entryPoint)
        private val exitPoints = reversed.keys - adjacentStmts.keys
        private val statements = reversed.keys + adjacentStmts.keys


        override fun predecessors(node: Int): Sequence<Int> =
            reversed.getOrElse(node, ::emptyList).asSequence()

        override fun successors(node: Int): Sequence<Int> =
            adjacentStmts.getOrElse(node, ::emptyList).asSequence()

        override fun callees(node: Int): Sequence<Unit> = emptySequence()

        override fun callers(method: Unit): Sequence<Int> = emptySequence()

        override fun entryPoints(method: Unit): Sequence<Int> = entryPoints

        override fun exitPoints(method: Unit): Sequence<Int> = exitPoints.asSequence()

        override fun methodOf(node: Int) = Unit

        override fun statementsOf(method: Unit): Sequence<Int> = statements.asSequence()
    }
}