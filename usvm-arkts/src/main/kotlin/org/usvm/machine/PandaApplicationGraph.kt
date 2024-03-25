package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.usvm.statistics.ApplicationGraph
import java.util.concurrent.ConcurrentHashMap

class PandaApplicationGraph(project: PandaProject) : ApplicationGraph<PandaMethod, PandaInst> {
    private val applicationGraph = PandaApplicationGraphImpl(project)

    override fun predecessors(node: PandaInst): Sequence<PandaInst> =
        applicationGraph.predecessors(node)

    override fun successors(node: PandaInst): Sequence<PandaInst> =
        applicationGraph.successors(node)

    override fun callees(node: PandaInst): Sequence<PandaMethod> =
        applicationGraph.callees(node)

    override fun callers(method: PandaMethod): Sequence<PandaInst> =
        applicationGraph.callers(method)

    override fun entryPoints(method: PandaMethod): Sequence<PandaInst> =
        applicationGraph.entryPoints(method)

    override fun exitPoints(method: PandaMethod): Sequence<PandaInst> =
        applicationGraph.exitPoints(method)

    override fun methodOf(node: PandaInst): PandaMethod =
        applicationGraph.methodOf(node)


    private val statementsOfMethodCache = ConcurrentHashMap<PandaMethod, Collection<PandaInst>>()

    override fun statementsOf(method: PandaMethod): Sequence<PandaInst> =
        statementsOfMethodCache
            .getOrPut(method) { computeAllStatementsOfMethod(method) }
            .asSequence()

    private fun computeAllStatementsOfMethod(method: PandaMethod): Collection<PandaInst> {
        val entryStatements = entryPoints(method)
        val statements = entryStatements.toMutableSet()

        val queue = ArrayDeque(entryStatements.toList())

        while (queue.isNotEmpty()) {
            val statement = queue.removeLast()
            val successors = successors(statement)
            for (successor in successors) {
                if (successor !in statements) {
                    statements += successor
                    queue += successor
                }
            }
        }

        return statements
    }
}