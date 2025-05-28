package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph

class BackwardAnalyzer(
    val graph: EtsApplicationGraph,
    savedTypes: MutableMap<EtsType, MutableList<EtsTypeFact>>,
    dominators: (EtsMethod) -> GraphDominators<EtsStmt>,
    doAddKnownTypes: Boolean = true,
) : Analyzer<BackwardPathTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = BackwardPathFlowFunctions(doAddKnownTypes)

    override fun handleCrossUnitCall(
        caller: Vertex<BackwardPathTypeDomainFact, EtsStmt>,
        callee: Vertex<BackwardPathTypeDomainFact, EtsStmt>,
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    override fun handleNewEdge(edge: Edge<BackwardPathTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, currentFact) = currentVertex

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
