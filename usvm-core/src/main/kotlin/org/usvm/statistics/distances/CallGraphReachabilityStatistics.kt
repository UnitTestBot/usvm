package org.usvm.statistics.distances

import org.usvm.statistics.ApplicationGraph
import org.usvm.util.limitedBfsTraversal
import java.util.concurrent.ConcurrentHashMap

class CallGraphReachabilityStatistics<Method, Statement>(
    private val depthLimit: UInt,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) {
    private val cache = ConcurrentHashMap<Method, Set<Method>>()

    private fun getAdjacentVertices(vertex: Method): Sequence<Method> =
        applicationGraph.statementsOf(vertex).flatMap(applicationGraph::callees).distinct()

    fun checkReachability(methodFrom: Method, methodTo: Method): Boolean =
        cache.computeIfAbsent(methodFrom) {
            limitedBfsTraversal(depthLimit, listOf(methodFrom), ::getAdjacentVertices).toSet()
        }.contains(methodTo)
}
