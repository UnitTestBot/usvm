package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsNopStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsMethod
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph

class ForwardAnalyzer(
    val graph: EtsApplicationGraph,
    methodInitialTypes: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
    typeInfo: Map<EtsType, EtsTypeFact>,
    doAddKnownTypes: Boolean = true,
) : Analyzer<ForwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = ForwardFlowFunctions(graph, methodInitialTypes, typeInfo, doAddKnownTypes)

    override fun handleCrossUnitCall(
        caller: Vertex<ForwardTypeDomainFact, EtsStmt>,
        callee: Vertex<ForwardTypeDomainFact, EtsStmt>,
    ): List<AnalyzerEvent> {
        error("No cross unit calls")
    }

    override fun handleNewEdge(edge: Edge<ForwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, _) = currentVertex
        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method) ||
            (current is EtsNopStmt && graph.successors(current).none())
        if (currentIsExit) {
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
