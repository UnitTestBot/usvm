package org.usvm.dataflow.ts.infer

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex

class BackwardAnalyzer(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>,
    dominators: (EtsMethod) -> GraphDominators<EtsStmt>
) : Analyzer<BackwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = BackwardFlowFunction(graph, dominators)

    override fun handleCrossUnitCall(
        caller: Vertex<BackwardTypeDomainFact, EtsStmt>,
        callee: Vertex<BackwardTypeDomainFact, EtsStmt>
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    override fun handleNewEdge(edge: Edge<BackwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, currentFact) = currentVertex

        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method)

        if (!currentIsExit) return emptyList()

        return listOf(
            BackwardSummaryAnalyzerEvent(
                method = method,
                initialFact = startVertex.fact,
                exitFact = currentFact,
            )
        )
    }
}
