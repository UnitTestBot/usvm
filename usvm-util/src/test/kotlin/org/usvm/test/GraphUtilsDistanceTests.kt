package org.usvm.test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.algorithms.findMinDistancesInUnweightedGraph
import kotlin.test.assertEquals

internal class GraphUtilsDistanceTests {

    @ParameterizedTest
    @MethodSource("distanceTestCases")
    fun findShortestDistancesInUnweightedGraphTest(graph: SimpleGraph, startVertex: Int, expected: Map<Int, UInt>) {
        val foundDistances = findMinDistancesInUnweightedGraph(startVertex, graph::getAdjacentVertices)
        assertEquals(expected.size, foundDistances.size)
        expected.forEach { (i, d) -> assertEquals(d, foundDistances[i]) }
    }

    @ParameterizedTest
    @MethodSource("distanceTestCases")
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
        fun distanceTestCases(): Collection<Arguments> {
            return listOf(
                Arguments.of(TestGraphs.graph1, 0, TestGraphs.graph1Expected),
                Arguments.of(TestGraphs.graph1WithStandaloneVertices, 0, TestGraphs.graph1Expected),
                Arguments.of(TestGraphs.graph1WithStandaloneVertices, 15, mapOf(15 to 0u)),
                Arguments.of(TestGraphs.graph2, 0, TestGraphs.graph2Expected),
            )
        }
    }
}
