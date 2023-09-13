package org.usvm.test

class SimpleGraph(val vertexCount: Int) {
    private val adjacencyLists = Array(vertexCount) { mutableSetOf(it) }

    fun addEdge(fromVertex: Int, toVertex: Int) {
        adjacencyLists[fromVertex].add(toVertex)
        adjacencyLists[toVertex].add(fromVertex)
    }

    fun getAdjacentVertices(vertexIndex: Int): Sequence<Int> = adjacencyLists[vertexIndex].asSequence()
}

object TestGraphs {
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
}
