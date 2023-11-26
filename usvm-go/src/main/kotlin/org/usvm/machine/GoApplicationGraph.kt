package org.usvm.machine

import org.usvm.bridge.GoBridge
import org.usvm.statistics.ApplicationGraph

class GoApplicationGraph(
    private val bridge: GoBridge,
) : ApplicationGraph<GoMethod, GoInst> {
    override fun predecessors(node: GoInst): Sequence<GoInst> {
        val (predecessors, count) = bridge.predecessors(node)
        return predecessors.asSequence().take(count)
    }

    override fun successors(node: GoInst): Sequence<GoInst> {
        val (successors, count) = bridge.successors(node)
        return successors.asSequence().take(count)
    }

    override fun callees(node: GoInst): Sequence<GoMethod> {
        val (callees, count) = bridge.callees(node)
        return callees.asSequence().take(count)
    }

    override fun callers(method: GoMethod): Sequence<GoInst> {
        val (callers, count) = bridge.callers(method)
        return callers.asSequence().take(count)
    }

    override fun entryPoints(method: GoMethod): Sequence<GoInst> {
        val (entryPoints, count) = bridge.entryPoints(method)
        return entryPoints.asSequence().take(count)
    }

    override fun exitPoints(method: GoMethod): Sequence<GoInst> {
        val (exitPoints, count) = bridge.exitPoints(method)
        return exitPoints.asSequence().take(count)
    }

    override fun methodOf(node: GoInst): GoMethod {
        return bridge.methodOf(node)
    }

    override fun statementsOf(method: GoMethod): Sequence<GoInst> {
        val (statements, count) = bridge.statementsOf(method)
        return statements.asSequence().take(count)
    }
}