package org.usvm.statistics.distances

/**
 * Calculates call graph metrics.
 */
interface CallGraphStatistics<Method> {

    /**
     * Checks if [methodTo] is reachable from [methodFrom] in call graph.
     */
    fun checkReachability(methodFrom: Method, methodTo: Method): Boolean
}
