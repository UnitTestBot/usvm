package org.usvm.util

import java.util.*

inline fun <V> findShortestDistancesInUnweightedGraph(
    startVertex: V,
    adjacentVertices: (V) -> Sequence<V>,
    distanceCache: Map<V, Map<V, UInt>> = emptyMap()
): Map<V, UInt> {
    val currentDistances = hashMapOf(startVertex to 0u)
    val queue: Queue<V> = LinkedList()
    queue.add(startVertex)
    while (queue.isNotEmpty()) {
        val currentVertex = queue.remove()
        val distanceToCurrentVertex = currentDistances.getValue(currentVertex)
        val cachedDistances = distanceCache[currentVertex]

        if (cachedDistances != null) {
            for ((theVertex, distanceFromCurrentToTheVertex) in cachedDistances) {
                val currentDistanceToTheVertex = currentDistances[theVertex]
                val newDistanceToTheVertex = distanceToCurrentVertex + distanceFromCurrentToTheVertex
                if (currentDistanceToTheVertex == null || newDistanceToTheVertex < currentDistanceToTheVertex) {
                    /*
                        1.
                        Why won't we break anything with such update?
                        Because even if newDistanceToTheVertex < currentDistanceToTheVertex holds, we haven't visited theVertex yet:

                        Suppose we have, then

                        currentDistanceToTheVertex <= distanceToCurrentVertex (because we're doing BFS)

                        newDistanceToTheVertex < currentDistanceToTheVertex ~
                        distanceToCurrentVertex + distanceFromCurrentToTheVertex < currentDistanceToTheVertex

                        distanceToCurrentVertex + distanceFromCurrentToTheVertex < distanceToCurrentVertex ?!
                    */
                    currentDistances[theVertex] = newDistanceToTheVertex
                }
            }
            continue
        }

        for (adjacentVertex in adjacentVertices(currentVertex)) {
            val currentDistanceToAdjacentVertex = currentDistances[adjacentVertex]
            val newDistanceToAdjacentVertex = distanceToCurrentVertex + 1u
            if (currentDistanceToAdjacentVertex == null || newDistanceToAdjacentVertex < currentDistanceToAdjacentVertex) {
                currentDistances[adjacentVertex] = newDistanceToAdjacentVertex
                /*
                    2.
                    If the vertex was added to queue, then it will never be added again:

                    If we write newDistanceToAdjacentVertex to dictionary here, newDistanceToAdjacentVertex >= currentDistanceToAdjacentVertex
                    will always hold because of BFS. It won't be broken by cache because currentDistanceToAdjacentVertex won't be rewritten from cache (see 1.)
                 */
                queue.add(adjacentVertex)
            }
        }
    }
    return currentDistances
}

inline fun <V> bfsTraversal(startVertices: Collection<V>, crossinline adjacentVertices: (V) -> Sequence<V>): Sequence<V> {
    val queue: Queue<V> = LinkedList(startVertices)
    val visited = HashSet<V>()

    return sequence {
        while (queue.isNotEmpty()) {
            val currentVertex = queue.remove()
            visited.add(currentVertex)
            yield(currentVertex)
            adjacentVertices(currentVertex).filterNot(visited::contains).forEach(queue::add)
        }
    }
}
