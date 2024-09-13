package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

private val logger = KotlinLogging.logger {}

class EtsApplicationGraphWithExplicitEntryPoint(
    private val graph: EtsApplicationGraph,
) : EtsApplicationGraph {

    override val cp: EtsScene
        get() = graph.cp

    override fun methodOf(node: EtsStmt): EtsMethod = node.location.method

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> = graph.exitPoints(method)

    private fun methodEntryPoint(method: EtsMethod) =
        EtsNopStmt(EtsInstLocation(method, index = -1))

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> = sequenceOf(methodEntryPoint(method))

    override fun callers(method: EtsMethod): Sequence<EtsStmt> = graph.callers(method)

    override fun callees(node: EtsStmt): Sequence<EtsMethod> = graph.callees(node)

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