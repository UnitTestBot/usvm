package org.usvm

import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.SyncUsagesExtension
import org.usvm.statistics.ApplicationGraph

class JcApplicationGraph(
    cp: JcClasspath,
) : ApplicationGraph<JcTypedMethod, JcInst> {
    private val jcApplicationGraph = JcApplicationGraphImpl(cp, SyncUsagesExtension(HierarchyExtensionImpl(cp), cp))

    override fun predecessors(node: JcInst): Sequence<JcInst> =
        jcApplicationGraph.predecessors(node)

    override fun successors(node: JcInst): Sequence<JcInst> =
        jcApplicationGraph.successors(node)

    override fun callees(node: JcInst): Sequence<JcTypedMethod> =
        jcApplicationGraph.callees(node).map { it.toTyped }

    override fun callers(method: JcTypedMethod): Sequence<JcInst> =
        jcApplicationGraph.callers(method.method)

    override fun entryPoints(method: JcTypedMethod): Sequence<JcInst> =
        jcApplicationGraph.entryPoint(method.method)

    override fun exitPoints(method: JcTypedMethod): Sequence<JcInst> =
        jcApplicationGraph.exitPoints(method.method)

    override fun methodOf(node: JcInst): JcTypedMethod =
        jcApplicationGraph.methodOf(node).toTyped


    private val typedMethodsCache = mutableMapOf<JcMethod, JcTypedMethod>()

    private val JcMethod.toTyped
        get() = typedMethodsCache.getOrPut(this) {
            enclosingClass.toType().declaredMethods.first { it.method == this }
        }
}
