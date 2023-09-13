package org.usvm.statistics.distances

import org.usvm.algorithms.findMinDistancesInUnweightedGraph
import org.usvm.statistics.ApplicationGraph
import java.util.concurrent.ConcurrentHashMap

/**
 * Common [CfgStatistics] implementation with thread-safe results caching.
 *
 * @param applicationGraph [ApplicationGraph] instance to get CFG from.
 */
class CfgStatisticsImpl<Method, Statement>(
    private val applicationGraph: ApplicationGraph<Method, Statement>,
) : CfgStatistics<Method, Statement> {

    private val allToAllShortestDistanceCache = ConcurrentHashMap<Method, ConcurrentHashMap<Statement, Map<Statement, UInt>>>()
    private val shortestDistanceToExitCache = ConcurrentHashMap<Method, ConcurrentHashMap<Statement, UInt>>()

    private fun getAllShortestCfgDistances(method: Method, stmtFrom: Statement): Map<Statement, UInt> {
        val methodCache = allToAllShortestDistanceCache.computeIfAbsent(method) { ConcurrentHashMap() }
        return methodCache.computeIfAbsent(stmtFrom) {
            findMinDistancesInUnweightedGraph(stmtFrom, applicationGraph::successors, methodCache)
        }
    }

    override fun getShortestDistance(method: Method, stmtFrom: Statement, stmtTo: Statement): UInt {
        return getAllShortestCfgDistances(method, stmtFrom)[stmtTo] ?: UInt.MAX_VALUE
    }

    override fun getShortestDistanceToExit(method: Method, stmtFrom: Statement): UInt {
        return shortestDistanceToExitCache
            .computeIfAbsent(method) { ConcurrentHashMap() }
            .computeIfAbsent(stmtFrom) {
                val exitPoints = applicationGraph.exitPoints(method).toHashSet()
                getAllShortestCfgDistances(method, stmtFrom)
                    .filterKeys(exitPoints::contains)
                    .minByOrNull { it.value }
                    ?.value ?: UInt.MAX_VALUE
            }
    }
}
