package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex

class ForwardAnalyzer(
    val graph: EtsApplicationGraph,
    methodInitialTypes: Map<EtsMethod, EtsMethodTypeFacts>,
) : Analyzer<ForwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = ForwardFlowFunction(graph, methodInitialTypes)

    override fun handleCrossUnitCall(
        caller: Vertex<ForwardTypeDomainFact, EtsStmt>,
        callee: Vertex<ForwardTypeDomainFact, EtsStmt>,
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    override fun handleNewEdge(edge: Edge<ForwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, currentFact) = currentVertex

        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method)

        if (!currentIsExit) return emptyList()

        return listOf(
            ForwardSummaryAnalyzerEvent(
                method = method,
                initialVertex = startVertex,
                exitVertex = currentVertex,
            )
        )
    }
}
