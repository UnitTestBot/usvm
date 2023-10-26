package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.ext.toType
import org.usvm.algorithms.limitedBfsTraversal
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.types.UTypeStream
import org.usvm.util.canBeOverridden
import org.usvm.util.findMethod
import java.util.concurrent.ConcurrentHashMap

/**
 * [CallGraphStatistics] Java caching implementation with thread-safe results caching. Overridden methods are considered
 * according to [typeStream] and [subclassesToTake].
 *
 * @param depthLimit depthLimit methods which are reachable via paths longer than this value are
 * not considered (i.e. 1 means that the target method should be directly called from source method).
 * @param applicationGraph [JcApplicationGraph] used to get callees info.
 * @param typeStream [UTypeStream] used to resolve method overrides.
 * @param subclassesToTake only method overrides from [subclassesToTake] first subtypes returned by [typeStream] are
 * considered during traversal. If equal to zero, method overrides are not considered during traversal at all.
 */
class JcCallGraphStatistics(
    private val depthLimit: UInt,
    private val applicationGraph: JcApplicationGraph,
    private val typeStream: UTypeStream<JcType>,
    private val subclassesToTake: Int = 0
) : CallGraphStatistics<JcMethod> {

    private val cache = ConcurrentHashMap<JcMethod, Set<JcMethod>>()

    private fun getCallees(method: JcMethod): Set<JcMethod> {
        val result = hashSetOf<JcMethod>()
        applicationGraph.statementsOf(method).flatMapTo(result, applicationGraph::callees)

        if (method.canBeOverridden() && subclassesToTake > 0) {
            typeStream
                .filterBySupertype(method.enclosingClass.toType())
                .take(subclassesToTake)
                .mapTo(result) {
                    val calleeMethod = it.findMethod(method)?.method
                    checkNotNull(calleeMethod) {
                        "Cannot find overridden method $method in type $it"
                    }
                }
        }

        return result
    }

    override fun checkReachability(methodFrom: JcMethod, methodTo: JcMethod): Boolean =
        cache.computeIfAbsent(methodFrom) {
            // TODO: stop traversal on reaching methodTo and cache remaining elements
            limitedBfsTraversal(listOf(methodFrom), depthLimit, adjacentVertices = { getCallees(it).asSequence() }).toSet()
        }.contains(methodTo)
}
