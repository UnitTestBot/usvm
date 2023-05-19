package org.usvm

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst

class JcApplicationGraph(
    val jcApplicationGraph: org.jacodb.api.analysis.ApplicationGraph<JcMethod, JcInst>
) : ApplicationGraph<JcMethod, JcInst> {
    override fun predecessors(node: JcInst): Sequence<JcInst> =
        jcApplicationGraph.predecessors(node)

    override fun successors(node: JcInst): Sequence<JcInst> =
        jcApplicationGraph.successors(node)

    override fun callees(node: JcInst): Sequence<JcMethod> =
        jcApplicationGraph.callees(node)

    override fun callers(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.callers(method)

    override fun entryPoint(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.entryPoint(method)

    override fun exitPoints(method: JcMethod): Sequence<JcInst> =
        jcApplicationGraph.exitPoints(method)

    override fun methodOf(node: JcInst): JcMethod =
        jcApplicationGraph.methodOf(node)
}