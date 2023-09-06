package org.usvm.test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.util.limitedBfsTraversal
import kotlin.test.assertEquals

internal class GraphUtilsLimitedBfsTests {

    @ParameterizedTest
    @MethodSource("limitedBfsTestCases")
    fun limitedBfsTraversalTest(limit: Int, graph: SimpleGraph, startVertex: Int, expectedVisited: Set<Int>) {
        val visited = limitedBfsTraversal(limit.toUInt(), listOf(startVertex), adjacentVertices = { graph.getAdjacentVertices(it).toSet() })
        assertEquals(expectedVisited, visited)
    }

    companion object {
        @JvmStatic
        fun limitedBfsTestCases(): Collection<Arguments> {
            return listOf(
                Arguments.of(0, TestGraphs.graph1, 0, setOf(0)),
                Arguments.of(1, TestGraphs.graph1, 0, setOf(0, 1, 7)),
                Arguments.of(2, TestGraphs.graph1, 0, setOf(0, 1, 7, 2, 8, 6)),
                Arguments.of(3, TestGraphs.graph1, 0, setOf(0, 1, 7, 2, 8, 6, 5, 3)),
                Arguments.of(0, TestGraphs.graph1, 8, setOf(8)),
                Arguments.of(1, TestGraphs.graph1, 8, setOf(8, 2, 6, 7)),
                Arguments.of(2, TestGraphs.graph1, 8, setOf(8, 2, 6, 7, 0, 1, 5, 3)),
                Arguments.of(3, TestGraphs.graph1, 8, setOf(8, 2, 6, 7, 0, 1, 5, 3, 4)),
                Arguments.of(42, TestGraphs.graph1, 8, setOf(8, 2, 6, 7, 0, 1, 5, 3, 4))
            )
        }
    }
}
