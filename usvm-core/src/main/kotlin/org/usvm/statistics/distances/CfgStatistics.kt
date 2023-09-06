package org.usvm.statistics.distances

/**
 * Calculates CFG metrics.
 */
interface CfgStatistics<Method, Statement> {

    /**
     * Returns shortest CFG distance from [stmtFrom] to [stmtTo] located in [method].
     */
    fun getShortestDistance(method: Method, stmtFrom: Statement, stmtTo: Statement): UInt

    /**
     * Returns CFG distance from [stmtFrom] to the closest exit point of [method].
     */
    fun getShortestDistanceToExit(method: Method, stmtFrom: Statement): UInt
}
