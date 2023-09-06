package org.usvm.machine

import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.ext.findMethodOrNull
import org.jacodb.api.ext.toType
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.types.UTypeStream
import org.usvm.util.isDefinition
import org.usvm.util.limitedBfsTraversal
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
        val callees = mutableSetOf<JcMethod>()
        applicationGraph.statementsOf(method).flatMapTo(callees, applicationGraph::callees)

        if (subclassesToTake <= 0 || callees.isEmpty()) {
            return callees
        }

        return typeStream
            .filterBySupertype(method.enclosingClass.toType())
            .take(subclassesToTake)
            .mapNotNullTo(callees) {
                val override = checkNotNull((it as? JcClassType)?.findMethodOrNull(method.name, method.description)?.method) {
                    "Cannot find overridden method $method in type $it"
                }
                // Check that the method was actually overridden
                if (override.isDefinition()) method else null
            }
    }

    override fun checkReachability(methodFrom: JcMethod, methodTo: JcMethod): Boolean =
        cache.computeIfAbsent(methodFrom) {
            // TODO: stop traversal on reaching methodTo and cache remaining elements
            limitedBfsTraversal(depthLimit, listOf(methodFrom), ::getCallees)
        }.contains(methodTo)
}
