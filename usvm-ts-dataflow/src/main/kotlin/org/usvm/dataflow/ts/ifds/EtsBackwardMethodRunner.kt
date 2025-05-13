package org.usvm.dataflow.ts.ifds

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AnalyzerEvent
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.util.EtsTraits

class EtsBackwardMethodRunner<Fact, Event : AnalyzerEvent>(
    val graph: EtsApplicationGraph,
    val method: EtsMethod,
    val analyzer: Analyzer<Fact, Event, EtsMethod, EtsStmt>,
    val traits: EtsTraits,
    val manager: TypeInferenceManager,
    val commonRunner: EtsBackwardIfdsRunner<Fact, Event>,
) {
    private val flowSpace = analyzer.flowFunctions

    /**
     * Remember only the sink since the source is specified by runner
     *
     * `ip` - index of the end statement
     *
     * `fact` - fact at the end statement
     */
    internal data class PathEdge<Fact>(
        val ip: Int,
        val fact: Fact,
    )

    private var enqueued: Boolean = false
    private val queue: ArrayDeque<EtsBackwardSourceRunner<Fact>> = ArrayDeque()

    internal fun enqueue(runner: EtsBackwardSourceRunner<Fact>) {
        queue.addLast(runner)
        if (!enqueued) {
            commonRunner.queue.addLast(this)
            enqueued = true
        }
    }

    fun processFacts() {
        while (!queue.isEmpty()) {
            val currentSourceRunner = queue.removeFirst()
            currentSourceRunner.processFacts()
        }
        enqueued = false
    }

    private val sourceRunners = hashMapOf<Fact, HashMap<EtsStmt, EtsBackwardSourceRunner<Fact>>>()
    internal fun getSourceRunner(vertex: Vertex<Fact, EtsStmt>): EtsBackwardSourceRunner<Fact> {
        return sourceRunners
            .getOrPut(vertex.fact) { hashMapOf() }
            .getOrPut(vertex.statement) {
                EtsBackwardSourceRunner(traits, this, vertex.statement, vertex.fact)
            }
    }

    internal fun getSourceRunners(fact: Fact): Collection<EtsBackwardSourceRunner<Fact>> {
        return sourceRunners.getOrPut(fact) {
            entrypoints.associateWithTo(hashMapOf()) {
                EtsBackwardSourceRunner(traits, this, it, fact)
            }
        }.values
    }

    internal val mockStmt = EtsNopStmt(EtsStmtLocation(method, -1))
    internal val stmts = listOf(mockStmt) + method.cfg.stmts

    internal val isExit = BooleanArray(stmts.size) { false }.apply {
        for (exit in graph.exitPoints(method)) {
            set(exit.index, true)
        }
    }

    internal val EtsStmt.index: Int
        get() = location.index + 1

    internal val successors = stmts.map { graph.successors(it).map { s -> s.index } }

    internal val sequentFlowFunction = stmts.map {
        flowSpace.obtainSequentFlowFunction(it, mockStmt)
    }
    internal val callToReturnSiteFlowFunction = stmts.map { stmt ->
        flowSpace.takeIf { stmt.callExpr != null }
            ?.obtainCallToReturnSiteFlowFunction(stmt, mockStmt)
    }

    internal data class CalleeStart<Fact>(
        val start: EtsStmt,
        val flowFunction: FlowFunction<Fact>,
    )

    internal val callToStartFlowFunctions = stmts.map { stmt ->
        val flowFunctions = mutableListOf<CalleeStart<Fact>>()
        for (callee in graph.callees(stmt)) {
            for (calleeStart in graph.entryPoints(callee)) {
                val flowFunction = flowSpace.obtainCallToStartFlowFunction(stmt, calleeStart)
                flowFunctions.add(CalleeStart(calleeStart, flowFunction))
            }
        }
        flowFunctions
    }

    internal val entrypoints = graph.entryPoints(method).toList()
    internal val exitpoints = graph.exitPoints(method).toList()

    fun addStart() {
        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            propagateStartingFact(startFact)
        }
    }

    internal fun propagateStartingFact(fact: Fact): Set<Vertex<Fact, EtsStmt>> {
        val summaryEdges = hashSetOf<Vertex<Fact, EtsStmt>>()
        for (entrypoint in entrypoints) {
            val runner = getSourceRunner(Vertex(entrypoint, fact))
            runner.propagate(PathEdge(entrypoint.index, fact))
            summaryEdges += runner.summaryEdges
        }
        return summaryEdges
    }

    internal fun handleSummaryEdgeInCaller(
        currentEdge: Edge<Fact, EtsStmt>,
        summaryEdge: Edge<Fact, EtsStmt>,
    ) {
        val (startVertex, currentVertex) = currentEdge
        val sourceRunner = getSourceRunner(startVertex)
        val caller = currentVertex.statement
        for (returnSite in graph.successors(caller)) {
            val (exit, exitFact) = summaryEdge.to
            val finalFacts = flowSpace
                .obtainExitToReturnSiteFlowFunction(caller, returnSite, exit)
                .compute(exitFact)
            for (returnSiteFact in finalFacts) {
                val newEdge = PathEdge(caller.index, returnSiteFact)
                sourceRunner.propagate(newEdge)
            }
        }
    }

    override fun toString(): String {
        return "Runner for ${method.signature}"
    }
}
