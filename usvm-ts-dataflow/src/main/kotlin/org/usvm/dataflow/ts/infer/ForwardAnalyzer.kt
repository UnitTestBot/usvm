package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsStmt
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph

class ForwardAnalyzer(
    val graph: EtsApplicationGraph,
    methodInitialTypes: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    doAddKnownTypes: Boolean = true,
    doAliasAnalysis: Boolean = true,
    val doLiveVariablesAnalysis: Boolean = true,
) : Analyzer<ForwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = ForwardFlowFunctions(
        graph = graph,
        methodInitialTypes = methodInitialTypes,
        doAddKnownTypes = doAddKnownTypes,
        doAliasAnalysis = doAliasAnalysis,
        doLiveVariablesAnalysis = doLiveVariablesAnalysis,
    )

    override fun handleCrossUnitCall(
        caller: Vertex<ForwardTypeDomainFact, EtsStmt>,
        callee: Vertex<ForwardTypeDomainFact, EtsStmt>,
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    private fun variableIsDying(fact: ForwardTypeDomainFact, stmt: EtsStmt): Boolean {
        if (fact !is ForwardTypeDomainFact.TypedVariable) return false
        return when (val base = fact.variable.base) {
            is AccessPathBase.Local -> !flowFunctions.liveVariables(stmt.location.method).isAliveAt(base.name, stmt)
            is AccessPathBase.Arg -> !flowFunctions.liveVariables(stmt.location.method).isAliveAt("arg(${base.index})", stmt)
            else -> false
        }
    }

    override fun handleNewEdge(edge: Edge<ForwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, currentFact) = currentVertex
        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method) ||
            (current is EtsNopStmt && graph.successors(current).none())

        if (currentIsExit || variableIsDying(currentFact, current)) {
            return listOf(
                ForwardSummaryAnalyzerEvent(
                    method = method,
                    initialVertex = startVertex,
                    exitVertex = currentVertex,
                )
            )
        }
        return emptyList()
    }
}
