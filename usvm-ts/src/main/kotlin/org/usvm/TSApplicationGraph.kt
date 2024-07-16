package org.usvm

import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.EtsApplicationGraph
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.statistics.ApplicationGraph

class TSApplicationGraph(project: EtsFile) : ApplicationGraph<EtsMethod, EtsStmt> {
    private val applicationGraph = EtsApplicationGraph(project)

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> =
        applicationGraph.predecessors(node)

    override fun successors(node: EtsStmt): Sequence<EtsStmt> =
        applicationGraph.successors(node)

    override fun callees(node: EtsStmt): Sequence<EtsMethod> =
        applicationGraph.callees(node)

    override fun callers(method: EtsMethod): Sequence<EtsStmt> =
        applicationGraph.callers(method)

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> =
        applicationGraph.entryPoints(method)

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> =
        applicationGraph.exitPoints(method)

    override fun methodOf(node: EtsStmt): EtsMethod =
        applicationGraph.methodOf(node)

    override fun statementsOf(method: EtsMethod): Sequence<EtsStmt> = sequence {
        method.cfg.stmts.forEach { yield(it) }
    }
}
