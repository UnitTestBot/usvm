package org.usvm.statistics

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import org.usvm.util.findMinDistancesInUnweightedGraph
import java.util.concurrent.ConcurrentHashMap

/**
 * Calculates distances in CFG and caches them.
 *
 * Operations are thread-safe.
 *
 * @param applicationGraph [ApplicationGraph] instance to get CFG from.
 */
class DistanceStatistics<Method, Statement>(private val applicationGraph: ApplicationGraph<Method, Statement>) {

    private val allToAllShortestCfgDistanceCache = ConcurrentHashMap<Method, ConcurrentHashMap<Statement, ImmutableMap<Statement, UInt>>>()
    private val shortestCfgDistanceToExitPointCache = ConcurrentHashMap<Method, ConcurrentHashMap<Statement, UInt>>()

    private fun getAllShortestCfgDistances(method: Method, stmtFrom: Statement): ImmutableMap<Statement, UInt> {
        val methodCache = allToAllShortestCfgDistanceCache.computeIfAbsent(method) { ConcurrentHashMap() }
        return methodCache.computeIfAbsent(stmtFrom) { findMinDistancesInUnweightedGraph(stmtFrom, applicationGraph::successors, methodCache).toImmutableMap() }
    }

    fun getShortestCfgDistance(method: Method, stmtFrom: Statement, stmtTo: Statement): UInt {
        return getAllShortestCfgDistances(method, stmtFrom)[stmtTo] ?: UInt.MAX_VALUE
    }

    fun getShortestCfgDistanceToExitPoint(method: Method, stmtFrom: Statement): UInt {
        return shortestCfgDistanceToExitPointCache.computeIfAbsent(method) { ConcurrentHashMap() }
            .computeIfAbsent(stmtFrom) {
                val exitPoints = applicationGraph.exitPoints(method).toHashSet()
                getAllShortestCfgDistances(method, stmtFrom).filterKeys(exitPoints::contains).minByOrNull { it.value }?.value ?: UInt.MAX_VALUE
            }
    }
}
