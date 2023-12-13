package org.usvm.machine

import org.usvm.bridge.GoBridge
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.statistics.ApplicationGraph

class GoApplicationGraph(
    private val bridge: GoBridge,
) : ApplicationGraph<GoMethod, GoInst> {
    override fun predecessors(node: GoInst): Sequence<GoInst> {
        return bridge.predecessors(node).asSequence()
    }

    override fun successors(node: GoInst): Sequence<GoInst> {
        return bridge.successors(node).asSequence()
    }

    override fun callees(node: GoInst): Sequence<GoMethod> {
        return bridge.callees(node).asSequence()
    }

    override fun callers(method: GoMethod): Sequence<GoInst> {
        return bridge.callers(method).asSequence()
    }

    override fun entryPoints(method: GoMethod): Sequence<GoInst> {
        return bridge.entryPoints(method).asSequence()
    }

    override fun exitPoints(method: GoMethod): Sequence<GoInst> {
        return bridge.exitPoints(method).asSequence()
    }

    override fun methodOf(node: GoInst): GoMethod {
        return bridge.methodOf(node)
    }

    override fun statementsOf(method: GoMethod): Sequence<GoInst> {
        return bridge.statementsOf(method).asSequence()
    }
}