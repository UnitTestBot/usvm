package org.usvm.test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.util.findMinDistancesInUnweightedGraph
import kotlin.test.assertEquals

internal class SimpleGraph(val vertexCount: Int) {
    private val adjacencyLists = Array(vertexCount) { mutableSetOf(it) }

    fun addEdge(fromVertex: Int, toVertex: Int) {
        adjacencyLists[fromVertex].add(toVertex)
        adjacencyLists[toVertex].add(fromVertex)
    }

    fun getAdjacentVertices(vertexIndex: Int): Sequence<Int> = adjacencyLists[vertexIndex].asSequence()
}

internal class GraphUtilsTests {

    @ParameterizedTest
    @MethodSource("testCases")
    fun findShortestDistancesInUnweightedGraphTest(graph: SimpleGraph, startVertex: Int, expected: Map<Int, UInt>) {
        val foundDistances = findMinDistancesInUnweightedGraph(startVertex, graph::getAdjacentVertices)
        assertEquals(expected.size, foundDistances.size)
        expected.forEach { (i, d) -> assertEquals(d, foundDistances[i]) }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun findShortestDistancesInUnweightedGraphWithCacheTest(graph: SimpleGraph, startVertex: Int, expected: Map<Int, UInt>) {
        val cache = mutableMapOf<Int, Map<Int, UInt>>()
        for (i in 0 until graph.vertexCount) {
            if (i == startVertex) { continue }
            val foundDistances = findMinDistancesInUnweightedGraph(i, graph::getAdjacentVertices, cache)
            val foundWithoutCacheDistances = findMinDistancesInUnweightedGraph(i, graph::getAdjacentVertices)
            foundWithoutCacheDistances.forEach { (i, d) -> assertEquals(d, foundDistances[i]) }
            cache[i] = foundDistances
        }

        val foundDistances = findMinDistancesInUnweightedGraph(startVertex, graph::getAdjacentVertices)
        val foundWithoutCacheDistances = findMinDistancesInUnweightedGraph(startVertex, graph::getAdjacentVertices)

        assertEquals(expected.size, foundDistances.size)
        foundWithoutCacheDistances.forEach { (i, d) -> assertEquals(d, foundDistances[i]) }
        expected.forEach { (i, d) -> assertEquals(d, foundDistances[i]) }
    }

    companion object {
        @JvmStatic
        fun testCases(): Collection<Arguments> {
            val graph1 = SimpleGraph(9).apply {
                addEdge(0, 1)
                addEdge(0, 7)
                addEdge(1, 7)
                addEdge(1, 2)
                addEdge(7, 6)
                addEdge(7, 8)
                addEdge(2, 8)
                addEdge(8, 6)
                addEdge(2, 5)
                addEdge(2, 3)
                addEdge(6, 5)
                addEdge(3, 5)
                addEdge(3, 4)
                addEdge(5, 4)
            }
            val graph1WithStandaloneVertices = SimpleGraph(30).apply {
                addEdge(0, 1)
                addEdge(0, 7)
                addEdge(1, 7)
                addEdge(1, 2)
                addEdge(7, 6)
                addEdge(7, 8)
                addEdge(2, 8)
                addEdge(8, 6)
                addEdge(2, 5)
                addEdge(2, 3)
                addEdge(6, 5)
                addEdge(3, 5)
                addEdge(3, 4)
                addEdge(5, 4)
            }
            val graph1Expected = mapOf(
                0 to 0u,
                1 to 1u,
                2 to 2u,
                3 to 3u,
                4 to 4u,
                5 to 3u,
                6 to 2u,
                7 to 1u,
                8 to 2u
            )
            val graph2 = SimpleGraph(6).apply {
                addEdge(0, 1)
                addEdge(0, 2)
                addEdge(0, 3)
                addEdge(2, 4)
                addEdge(3, 5)
                addEdge(4, 5)
            }
            val graph2Expected = mapOf(
                0 to 0u,
                1 to 1u,
                2 to 1u,
                3 to 1u,
                4 to 2u,
                5 to 2u
            )

            return listOf(
                Arguments.of(graph1, 0, graph1Expected),
                Arguments.of(graph1WithStandaloneVertices, 0, graph1Expected),
                Arguments.of(graph1WithStandaloneVertices, 15, mapOf(15 to 0u)),
                Arguments.of(graph2, 0, graph2Expected),
            )
        }
    }
}
