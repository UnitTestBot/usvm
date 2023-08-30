package org.usvm.statistics.distances

import org.usvm.statistics.ApplicationGraph
import org.usvm.util.limitedBfsTraversal
import java.util.concurrent.ConcurrentHashMap

/**
 * Calculates and caches information about reachability of one method from another
 * in call graph.
 *
 * @param depthLimit methods which are reachable via paths longer than this value are
 * not considered (i.e. 1 means that the target method should be directly called from source method).
 */
class CallGraphReachabilityStatistics<Method, Statement>(
    private val depthLimit: UInt,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) {
    private val cache = ConcurrentHashMap<Method, Set<Method>>()

    private fun getAdjacentVertices(vertex: Method): Sequence<Method> =
        applicationGraph.statementsOf(vertex).flatMap(applicationGraph::callees).distinct()

    /**
     * Checks if [methodTo] is reachable from [methodFrom] in call graph.
     */
    fun checkReachability(methodFrom: Method, methodTo: Method): Boolean =
        cache.computeIfAbsent(methodFrom) {
            limitedBfsTraversal(depthLimit, listOf(methodFrom), ::getAdjacentVertices).toSet()
        }.contains(methodTo)
}
