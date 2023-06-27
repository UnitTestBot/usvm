package org.usvm.machine

import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.SyncUsagesExtension
import org.usvm.statistics.ApplicationGraph
import java.util.concurrent.ConcurrentHashMap

// TODO: add trap handlers
/**
 * A [JcApplicationGraphImpl] wrapper.
 */
class JcApplicationGraph(
    cp: JcClasspath,
) : ApplicationGraph<JcMethod, JcInst> {
    private val jcApplicationGraph = JcApplicationGraphImpl(cp, SyncUsagesExtension(HierarchyExtensionImpl(cp), cp))

    override fun predecessors(node: JcInst): Sequence<JcInst> =
        jcApplicationGraph.predecessors(node)

    override fun successors(node: JcInst): Sequence<JcInst> =
        jcApplicationGraph.successors(node)

    override fun callees(node: JcInst): Sequence<JcMethod> =
        jcApplicationGraph.callees(node)

    override fun callers(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.callers(method)

    override fun entryPoints(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.entryPoint(method)

    override fun exitPoints(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.exitPoints(method)

    override fun methodOf(node: JcInst): JcMethod =
        jcApplicationGraph.methodOf(node)

    private val typedMethodsCache = ConcurrentHashMap<JcMethod, JcTypedMethod>()

    val JcMethod.typed
        get() = typedMethodsCache.getOrPut(this) {
            enclosingClass.toType().declaredMethods.first { it.method == this }
        }

    private val statementsOfMethodCache = ConcurrentHashMap<JcMethod, Collection<JcInst>>()

    override fun statementsOf(method: JcMethod): Sequence<JcInst> =
        statementsOfMethodCache
            .getOrPut(method) { computeAllStatementsOfMethod(method) }
            .asSequence()

    private fun computeAllStatementsOfMethod(method: JcMethod): Collection<JcInst> {
        val entryStatements = entryPoint(method)
        val statements = entryStatements.toMutableSet()

        val queue = ArrayDeque(entryStatements.toList())

        while (queue.isNotEmpty()) {
            val statement = queue.removeLast()
            val successors = successors(statement)
            for (successor in successors) {
                if (successor !in statements) {
                    statements += successor
                    queue += successor
                }
            }
        }

        return statements
    }
}
