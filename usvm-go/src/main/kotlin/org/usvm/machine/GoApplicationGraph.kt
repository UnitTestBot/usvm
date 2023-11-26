package org.usvm.machine

import org.usvm.bridge.Bridge
import org.usvm.statistics.ApplicationGraph

class GoApplicationGraph(
    private val bridge: Bridge,
) : ApplicationGraph<GoMethod, GoInst> {
    override fun predecessors(node: GoInst): Sequence<GoInst> {
        TODO("Not yet implemented")
    }

    override fun successors(node: GoInst): Sequence<GoInst> {
        TODO("Not yet implemented")
    }

    override fun callees(node: GoInst): Sequence<GoMethod> {
        TODO("Not yet implemented")
    }

    override fun callers(method: GoMethod): Sequence<GoInst> {
        TODO("Not yet implemented")
    }

    override fun entryPoints(method: GoMethod): Sequence<GoInst> {
        TODO("Not yet implemented")
    }

    override fun exitPoints(method: GoMethod): Sequence<GoInst> {
        TODO("Not yet implemented")
    }

    override fun methodOf(node: GoInst): GoMethod {
        TODO("Not yet implemented")
    }

    override fun statementsOf(method: GoMethod): Sequence<GoInst> {
        TODO("Not yet implemented")
    }
}