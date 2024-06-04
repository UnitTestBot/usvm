package org.usvm.jacodb

import org.jacodb.go.api.GoApplicationGraph
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.usvm.statistics.ApplicationGraph

class GoApplicationGraphAdapter(
    private val graph: GoApplicationGraph,
) : ApplicationGraph<GoMethod, GoInst> {
    override fun predecessors(node: GoInst): Sequence<GoInst> {
        return graph.predecessors(node)
    }

    override fun successors(node: GoInst): Sequence<GoInst> {
        return graph.successors(node)
    }

    override fun callees(node: GoInst): Sequence<GoMethod> {
        return graph.callees(node)
    }

    override fun callers(method: GoMethod): Sequence<GoInst> {
        return graph.callers(method)
    }

    override fun entryPoints(method: GoMethod): Sequence<GoInst> {
        return graph.entryPoints(method)
    }

    override fun exitPoints(method: GoMethod): Sequence<GoInst> {
        return graph.exitPoints(method)
    }

    override fun methodOf(node: GoInst): GoMethod {
        return graph.methodOf(node)
    }

    override fun statementsOf(method: GoMethod): Sequence<GoInst> {
        return method.flowGraph().instructions.asSequence()
    }
}