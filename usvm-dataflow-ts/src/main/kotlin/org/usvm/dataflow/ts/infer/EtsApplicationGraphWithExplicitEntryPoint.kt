package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.graph.EtsApplicationGraphImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ts.util.CONSTRUCTOR

private val logger = KotlinLogging.logger {}

class EtsApplicationGraphWithExplicitEntryPoint(
    private val graph: EtsApplicationGraph,
) : EtsApplicationGraph {

    override val cp: EtsFile
        get() = graph.cp

    override fun methodOf(node: EtsStmt): EtsMethod = node.location.method

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> = graph.exitPoints(method)

    private fun methodEntryPoint(method: EtsMethod) =
        EtsNopStmt(EtsInstLocation(method, index = -1))

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> = sequenceOf(methodEntryPoint(method))

    override fun callers(method: EtsMethod): Sequence<EtsStmt> = graph.callers(method)

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        val callees = graph.callees(node).toList()

        val callExpr = node.callExpr
        if (callees.isEmpty() && callExpr != null) {
            if (callExpr.method.name == CONSTRUCTOR) {
                val enclosingClass = graph.cp.classes.firstOrNull {
                    it.name == callExpr.method.enclosingClass.name
                }
                if (enclosingClass != null) {
                    val ctor = enclosingClass.ctor
                    logger.info { "Constructor call at $node: $ctor" }
                    return sequenceOf(ctor)
                }
            }
            logger.info { "No methods found for: $node" }
        }

        return callees.asSequence()
    }

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        val method = methodOf(node)
        val methodEntry = methodEntryPoint(method)

        if (node == methodEntry) {
            return graph.entryPoints(method)
        }

        return graph.successors(node)
    }

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        val method = methodOf(node)
        val methodEntry = methodEntryPoint(method)

        if (node == methodEntry) {
            return emptySequence()
        }

        if (node in graph.entryPoints(method)) {
            return sequenceOf(methodEntry)
        }

        return graph.predecessors(node)
    }
}
