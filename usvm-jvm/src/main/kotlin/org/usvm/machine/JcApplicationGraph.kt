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
import org.usvm.util.originalInst
import java.util.concurrent.ConcurrentHashMap

/**
 * A [JcApplicationGraphImpl] wrapper.
 */
class JcApplicationGraph(
    cp: JcClasspath,
) : ApplicationGraph<JcMethod, JcInst> {
    private val jcApplicationGraph = JcApplicationGraphImpl(cp, SyncUsagesExtension(HierarchyExtensionImpl(cp), cp))

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        return jcApplicationGraph.predecessors(node.originalInst())
    }

    override fun successors(node: JcInst): Sequence<JcInst> {
        return jcApplicationGraph.successors(node.originalInst())
    }

    override fun callees(node: JcInst): Sequence<JcMethod> {
        return jcApplicationGraph.callees(node.originalInst())
    }

    override fun callers(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.callers(method)

    override fun entryPoints(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.entryPoint(method)

    override fun exitPoints(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.exitPoints(method)

    override fun methodOf(node: JcInst): JcMethod {
        return jcApplicationGraph.methodOf(node.originalInst())
    }

    private val typedMethodsCache = ConcurrentHashMap<JcMethod, JcTypedMethod>()

    val JcMethod.typed: JcTypedMethod?
        get() = typedMethodsCache.getOrPut(this) {
            enclosingClass.toType().declaredMethods.firstOrNull { it.method == this }
        }

    private val statementsOfMethodCache = ConcurrentHashMap<JcMethod, Collection<JcInst>>()

    override fun statementsOf(method: JcMethod): Sequence<JcInst> =
        statementsOfMethodCache
            .getOrPut(method) { computeAllStatementsOfMethod(method) }
            .asSequence()

    private fun computeAllStatementsOfMethod(method: JcMethod): Collection<JcInst> {
        val entryStatements = entryPoints(method)
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
