package org.usvm.jvm

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcBasicBlock
import org.jacodb.api.cfg.JcBlockGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.usvm.ApplicationBlockGraph
import org.usvm.machine.JcApplicationGraph
import java.util.concurrent.ConcurrentHashMap

class JcApplicationBlockGraph(cp: JcClasspath) :
    ApplicationBlockGraph<JcMethod, JcBasicBlock, JcInst> {
    val jcApplicationGraph: JcApplicationGraph = JcApplicationGraph(cp)
    var initialStatement: JcInst? = null

    private fun initialStatement(): JcInst {
        if (initialStatement == null) {
            throw RuntimeException("initial statement not set")
        }
        return initialStatement!!
    }

    private fun getBlockGraph() = initialStatement().location.method.flowGraph().blockGraph()

    override fun predecessors(node: JcBasicBlock): Sequence<JcBasicBlock> {
        val jcBlockGraphImpl: JcBlockGraph = getBlockGraph()
        return jcBlockGraphImpl.predecessors(node).asSequence()
    }

    override fun successors(node: JcBasicBlock): Sequence<JcBasicBlock> {
        val jcBlockGraphImpl: JcBlockGraph = getBlockGraph()
        return jcBlockGraphImpl.successors(node).asSequence() + jcBlockGraphImpl.throwers(node).asSequence()
    }

    override fun callees(node: JcBasicBlock): Sequence<JcMethod> {
        val jcBlockGraphImpl: JcBlockGraph = getBlockGraph()

        return jcBlockGraphImpl.instructions(node)
            .map { jcApplicationGraph.callees(it) }
            .reduce { acc, sequence -> acc + sequence }
            .toSet()
            .asSequence()
    }

    override fun callers(method: JcMethod): Sequence<JcBasicBlock> {
        return jcApplicationGraph
            .callers(method)
            .map { stmt -> blockOf(stmt) }
            .toSet()
            .asSequence()
    }

    override fun entryPoints(method: JcMethod): Sequence<JcBasicBlock> =
        method.flowGraph().blockGraph().entries.asSequence()

    override fun exitPoints(method: JcMethod): Sequence<JcBasicBlock> =
        method.flowGraph().blockGraph().exits.asSequence()

    override fun methodOf(node: JcBasicBlock): JcMethod {
        val firstInstruction = getBlockGraph().instructions(node).first()
        return jcApplicationGraph.methodOf(firstInstruction)
    }

    override fun instructions(block: JcBasicBlock): Sequence<JcInst> {
        return getBlockGraph().instructions(block).asSequence()
    }

    override fun statementsOf(method: JcMethod): Sequence<JcBasicBlock> {
        return jcApplicationGraph
            .statementsOf(method)
            .map { stmt -> blockOf(stmt) }
            .toSet()
            .asSequence()
    }

    override fun blockOf(stmt: JcInst): JcBasicBlock {
        val jcBlockGraphImpl: JcBlockGraph = stmt.location.method.flowGraph().blockGraph()
        val blocks = blocks()
        for (block in blocks) {
            if (stmt in jcBlockGraphImpl.instructions(block)) {
                return block
            }
        }
        throw IllegalStateException("block not found for $stmt in ${jcBlockGraphImpl.jcGraph.method}")
    }

    override fun blocks(): Sequence<JcBasicBlock> {
        return initialStatement().location.method.flowGraph().blockGraph().asSequence()
    }

    private val typedMethodsCache = ConcurrentHashMap<JcMethod, JcTypedMethod>()

    val JcMethod.typed
        get() = typedMethodsCache.getOrPut(this) {
            enclosingClass.toType().declaredMethods.first { it.method == this }
        }
}
