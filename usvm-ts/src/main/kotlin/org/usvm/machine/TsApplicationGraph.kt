package org.usvm.machine

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.graph.EtsApplicationGraphImpl
import org.usvm.statistics.ApplicationGraph
import org.usvm.util.originalStmt

class TsApplicationGraph(scene: EtsScene) : ApplicationGraph<EtsMethod, EtsStmt> {
    private val applicationGraph: EtsApplicationGraph = EtsApplicationGraphImpl(scene)

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> =
        applicationGraph.predecessors(node.originalStmt())

    override fun successors(node: EtsStmt): Sequence<EtsStmt> =
        applicationGraph.successors(node.originalStmt())

    override fun callees(node: EtsStmt): Sequence<EtsMethod> =
        applicationGraph.callees(node.originalStmt())

    override fun callers(method: EtsMethod): Sequence<EtsStmt> =
        applicationGraph.callers(method)

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> =
        applicationGraph.entryPoints(method)

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> =
        applicationGraph.exitPoints(method)

    override fun methodOf(node: EtsStmt): EtsMethod =
        applicationGraph.methodOf(node.originalStmt())

    override fun statementsOf(method: EtsMethod): Sequence<EtsStmt> =
        method.cfg.stmts.asSequence()
}
