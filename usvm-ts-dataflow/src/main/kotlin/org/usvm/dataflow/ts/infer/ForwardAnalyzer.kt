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
    doAliasAnalysis: Boolean = true,
    val doLiveVariablesAnalysis: Boolean = true,
) : Analyzer<ForwardTypeDomainFact, AnalyzerEvent, EtsMethod, EtsStmt> {

    override val flowFunctions = ForwardFlowFunctions(
        graph = graph,
        methodInitialTypes = methodInitialTypes,
        typeInfo = typeInfo,
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

    private val liveVariablesCache = hashMapOf<EtsMethod, LiveVariables>()
    private fun liveVariables(method: EtsMethod) =
        liveVariablesCache.computeIfAbsent(method) {
            if (doLiveVariablesAnalysis) LiveVariables.from(method) else AlwaysAlive
        }

    override fun handleNewEdge(edge: Edge<ForwardTypeDomainFact, EtsStmt>): List<AnalyzerEvent> {
        val (startVertex, currentVertex) = edge
        val (current, currentFact) = currentVertex
        val method = graph.methodOf(current)
        val currentIsExit = current in graph.exitPoints(method) ||
            (current is EtsNopStmt && graph.successors(current).none())

        val variableIsDying = (currentFact as? ForwardTypeDomainFact.TypedVariable)?.let {
            val base = it.variable.base
            base is AccessPathBase.Local && (!liveVariables(method).isAliveAt(base.name, current))
        } ?: false

        if (currentIsExit || variableIsDying) {
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
