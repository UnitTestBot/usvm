package org.usvm.machine

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsTrap
import org.jacodb.ets.utils.TrapUtils
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.graph.EtsApplicationGraphImpl
import org.usvm.statistics.ApplicationGraph
import org.usvm.util.EtsHierarchy

private val logger = KotlinLogging.logger {}

class TsGraph(scene: EtsScene) : ApplicationGraph<EtsMethod, EtsStmt> {
    private val etsGraph: EtsApplicationGraph = EtsApplicationGraphImpl(scene)
    val hierarchy: EtsHierarchy = EtsHierarchy(scene)

    val cp: EtsScene
        get() = etsGraph.cp

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> =
        etsGraph.predecessors(node)

    override fun successors(node: EtsStmt): Sequence<EtsStmt> =
        if (node is TsMethodCall) {
            etsGraph.successors(node.returnSite)
        } else {
            etsGraph.successors(node)
        }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> =
        etsGraph.callees(node)

    override fun callers(method: EtsMethod): Sequence<EtsStmt> =
        etsGraph.callers(method)

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> =
        etsGraph.entryPoints(method)

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> =
        etsGraph.exitPoints(method)

    override fun methodOf(node: EtsStmt): EtsMethod =
        etsGraph.methodOf(node)

    override fun statementsOf(method: EtsMethod): Sequence<EtsStmt> =
        method.cfg.stmts.asSequence()

    fun findTrap(stmt: EtsStmt): EtsTrap? {
        val method = stmt.location.method
        val cfg = method.cfg
        val block = cfg.blocks.find { b -> b.statements.any { s -> s === stmt } } ?: run {
            logger.warn { "Cannot find block for stmt $stmt in method: $method" }
            return null
        }
        val trap = TrapUtils.findInnermostTrap(method.traps, block)
        return trap
    }
}
