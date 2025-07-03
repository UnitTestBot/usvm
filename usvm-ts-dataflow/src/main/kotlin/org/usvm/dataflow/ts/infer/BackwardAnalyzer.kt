package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph

class BackwardAnalyzer(
    val graph: EtsApplicationGraph,
    doAddKnownTypes: Boolean = true,
) : Analyzer<BackwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = BackwardFlowFunctions(doAddKnownTypes)

    override fun handleCrossUnitCall(
        caller: Vertex<BackwardTypeDomainFact, EtsStmt>,
        callee: Vertex<BackwardTypeDomainFact, EtsStmt>,
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    override fun handleNewEdge(edge: Edge<BackwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, _) = currentVertex

        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method)

        if (!currentIsExit) return emptyList()

        return listOf(
            BackwardSummaryAnalyzerEvent(
                method = method,
                initialVertex = startVertex,
                exitVertex = currentVertex,
            )
        )
    }
}
