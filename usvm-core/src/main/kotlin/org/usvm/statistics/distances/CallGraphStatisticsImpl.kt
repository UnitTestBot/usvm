package org.usvm.statistics.distances

import java.util.concurrent.ConcurrentHashMap
import org.usvm.statistics.ApplicationGraph
import org.usvm.util.limitedBfsTraversal

/**
 * [CallGraphStatistics] common implementation with thread-safe results caching. As it is language-agnostic,
 * it uses only [applicationGraph] info and **doesn't** consider potential virtual calls.
 *
 * @param depthLimit depthLimit methods which are reachable via paths longer than this value are
 * not considered (i.e. 1 means that the target method should be directly called from source method).
 * @param applicationGraph [ApplicationGraph] used to get callees info.
 */
class CallGraphStatisticsImpl<Method, Statement>(
    private val depthLimit: UInt,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) : CallGraphStatistics<Method> {

    private val cache = ConcurrentHashMap<Method, Set<Method>>()

    private fun getCallees(method: Method): Sequence<Method> =
        applicationGraph.statementsOf(method).flatMap(applicationGraph::callees).distinct()

    override fun checkReachability(methodFrom: Method, methodTo: Method): Boolean =
        cache.computeIfAbsent(methodFrom) {
            limitedBfsTraversal(depthLimit, listOf(methodFrom), ::getCallees).toSet()
        }.contains(methodTo)
}
