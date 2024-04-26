package org.usvm.machine

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.toType
import org.usvm.algorithms.limitedBfsTraversal
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.types.TypesResult
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
        val callees = mutableSetOf<JcMethod>()
        applicationGraph.statementsOf(method).flatMapTo(callees, applicationGraph::callees)

        if (subclassesToTake <= 0 || callees.isEmpty()) {
            return callees
        }

        val overrides = mutableSetOf<JcMethod>()
        for (callee in callees) {
            if (!callee.canBeOverridden()) {
                continue
            }

            typeStream
                .filterBySupertype(callee.enclosingClass.toType())
                .take(subclassesToTake)
                .let {
                    when (it) {
                        TypesResult.EmptyTypesResult -> emptyList()
                        is TypesResult.SuccessfulTypesResult -> it
                        is TypesResult.TypesResultWithExpiredTimeout -> it.collectedTypes
                    }
                }
                .mapTo(overrides) {
                    val calleeMethod = it.findMethod(callee)?.method
                    checkNotNull(calleeMethod) {
                        "Cannot find overridden method $callee in type $it"
                    }
                }
        }

        return callees + overrides
    }

    override fun checkReachability(methodFrom: JcMethod, methodTo: JcMethod): Boolean =
        cache.computeIfAbsent(methodFrom) {
            // TODO: stop traversal on reaching methodTo and cache remaining elements
            limitedBfsTraversal(listOf(methodFrom), depthLimit, adjacentVertices = { getCallees(it).asSequence() }).toSet()
        }.contains(methodTo)
}
