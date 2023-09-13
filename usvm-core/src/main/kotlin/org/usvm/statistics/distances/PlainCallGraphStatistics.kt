package org.usvm.statistics.distances

/**
 * Limit case [CallGraphStatistics] implementation which considers two methods reachable
 * only if they are the same.
 */
class PlainCallGraphStatistics<Method> : CallGraphStatistics<Method> {

    override fun checkReachability(methodFrom: Method, methodTo: Method): Boolean = methodFrom == methodTo
}
