package org.usvm.machine

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.SyncUsagesExtension
import org.usvm.statistics.ApplicationGraph
import org.usvm.util.originalInst
import java.util.concurrent.ConcurrentHashMap

class JcApplicationGraph(
    cp: JcClasspath,
) : ApplicationGraph<JcMethod, JcInst> {
    private val usages = SyncUsagesExtension(HierarchyExtensionImpl(cp), cp)

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        @Suppress("NAME_SHADOWING")
        val node = node.originalInst()
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        val throwers = graph.throwers(node)
        return predecessors.asSequence() + throwers.asSequence()
    }

    override fun successors(node: JcInst): Sequence<JcInst> {
        @Suppress("NAME_SHADOWING")
        val node = node.originalInst()
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        val catchers = graph.catchers(node)
        return successors.asSequence() + catchers.asSequence()
    }

    override fun callees(node: JcInst): Sequence<JcMethod> {
        @Suppress("NAME_SHADOWING")
        val node = node.originalInst()
        val callExpr = node.callExpr ?: return emptySequence()
        return sequenceOf(callExpr.method.method)
    }

    override fun callers(method: JcMethod): Sequence<JcInst> {
        return usages.findUsages(method).flatMap {
            it.flowGraph().instructions.asSequence().filter { inst ->
                val callExpr = inst.callExpr ?: return@filter false
                callExpr.method.method == method
            }
        }
    }

    override fun entryPoints(method: JcMethod): Sequence<JcInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: JcMethod): Sequence<JcInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: JcInst): JcMethod {
        return node.location.method
    }

    private val typedMethodsCache = ConcurrentHashMap<JcMethod, JcTypedMethod>()

    val JcMethod.typed: JcTypedMethod
        get() = typedMethodsCache.computeIfAbsent(this) {
            enclosingClass.toType().declaredMethods.first { it.method == this }
        }

    private val statementsOfMethodCache = ConcurrentHashMap<JcMethod, Collection<JcInst>>()

    override fun statementsOf(method: JcMethod): Sequence<JcInst> =
        statementsOfMethodCache
            .computeIfAbsent(method) { computeAllStatementsOfMethod(method) }
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
