package org.usvm.machine

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.SyncUsagesExtension
import org.jacodb.impl.features.hierarchyExt
import org.usvm.dataflow.jvm.graph.JcApplicationGraphImpl
import org.usvm.statistics.ApplicationGraph
import org.usvm.util.originalInst
import java.util.concurrent.ConcurrentHashMap

/**
 * A [JcApplicationGraphImpl] wrapper.
 */
class JcApplicationGraph(
    cp: JcClasspath,
) : ApplicationGraph<JcMethod, JcInst> {
    private val jcApplicationGraph =
        JcApplicationGraphImpl(cp, SyncUsagesExtension(runBlocking { cp.hierarchyExt() }, cp))

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
        jcApplicationGraph.entryPoints(method)

    override fun exitPoints(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.exitPoints(method)

    override fun methodOf(node: JcInst): JcMethod {
        return jcApplicationGraph.methodOf(node.originalInst())
    }

    private val typedMethodsCache = ConcurrentHashMap<JcMethod, JcTypedMethod>()

    val JcMethod.typed: JcTypedMethod
        get() = typedMethodsCache.getOrPut(this) {
            enclosingClass.toType().declaredMethods.first { it.method == this }
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
