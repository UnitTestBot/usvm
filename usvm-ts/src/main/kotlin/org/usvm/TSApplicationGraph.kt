package org.usvm

import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.EtsApplicationGraph
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.statistics.ApplicationGraph

class TSApplicationGraph(project: EtsFile) : ApplicationGraph<EtsMethod, EtsStmt> {
    private val applicationGraph = EtsApplicationGraph(project)

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        TODO("Not yet implemented")
    }

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        TODO("Not yet implemented")
    }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        TODO("Not yet implemented")
    }

    override fun callers(method: EtsMethod): Sequence<EtsStmt> {
        TODO("Not yet implemented")
    }

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> {
        TODO("Not yet implemented")
    }

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> {
        TODO("Not yet implemented")
    }

    override fun methodOf(node: EtsStmt): EtsMethod {
        TODO("Not yet implemented")
    }

    override fun statementsOf(method: EtsMethod): Sequence<EtsStmt> {
        TODO("Not yet implemented")
    }
}
