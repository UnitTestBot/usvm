package org.usvm

import org.jacodb.go.api.GoCallInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.usvm.statistics.ApplicationGraph

class GoApplicationGraph : ApplicationGraph<GoMethod, GoInst> {
    override fun predecessors(node: GoInst): Sequence<GoInst> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        val throwers = graph.throwers(node)
        return predecessors.asSequence() + throwers.asSequence()
    }

    override fun successors(node: GoInst): Sequence<GoInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    override fun callees(node: GoInst): Sequence<GoMethod> {
        if (node !is GoCallInst) {
            return emptySequence()
        }
        val callExpr = node.callExpr
        return sequenceOf(callExpr.callee!!)
    }

    override fun callers(method: GoMethod): Sequence<GoInst> {
        var res = listOf<GoInst>()
        for (block in method.blocks) {
            res = listOf(res, block.instructions).flatten()
        }
        return res.asSequence()
    }

    override fun entryPoints(method: GoMethod): Sequence<GoInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: GoMethod): Sequence<GoInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: GoInst): GoMethod {
        return node.location.method
    }

    override fun statementsOf(method: GoMethod): Sequence<GoInst> {
        return method.flowGraph().instructions.asSequence()
    }
}