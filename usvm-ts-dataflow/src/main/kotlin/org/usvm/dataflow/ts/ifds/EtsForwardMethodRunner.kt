package org.usvm.dataflow.ts.ifds

import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AnalyzerEvent
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.util.EtsTraits

class EtsForwardMethodRunner<Fact, Event : AnalyzerEvent>(
    val graph: EtsApplicationGraph,
    val method: EtsMethod,
    val analyzer: Analyzer<Fact, Event, EtsMethod, EtsStmt>,
    val traits: EtsTraits,
    val manager: TypeInferenceManager,
    val commonRunner: EtsForwardIfdsRunner<Fact, Event>,
) {
    private val flowSpace = analyzer.flowFunctions

    private var enqueued: Boolean = false
    private val queue: ArrayDeque<EtsForwardSourceRunner<Fact>> = ArrayDeque()

    internal val cfg = method.cfg
    internal val stmts = method.cfg.stmts
    internal val isExit = BooleanArray(stmts.size) { false }.apply {
        for (exit in cfg.exits) {
            set(exit.index, true)
        }
    }
    internal val successors = stmts.map { cfg.successors(it).map { s -> s.index } }

    internal val EtsStmt.index: Int
        get() = location.index
    internal val mockStmt = EtsNopStmt(EtsStmtLocation(method, -1))
    internal val entrypoint = stmts.firstOrNull() ?: mockStmt

    internal data class CalleeStart<Fact>(
        val start: EtsStmt,
        val flowFunction: FlowFunction<Fact>,
    )

    internal val sequentFlowFunction = stmts.map {
        flowSpace.obtainSequentFlowFunction(it, mockStmt)
    }
    internal val callToReturnSiteFlowFunction = stmts.map { stmt ->
        flowSpace.takeIf { stmt.callExpr != null }
            ?.obtainCallToReturnSiteFlowFunction(stmt, mockStmt)
    }
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

    fun addStart() {
        if (entrypoint == mockStmt) {
            return
        }

        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            propagateStartingFact(startFact)
        }
    }

    internal fun enqueue(runner: EtsForwardSourceRunner<Fact>) {
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

    internal val sourceRunners = hashMapOf<Fact, EtsForwardSourceRunner<Fact>>()
    internal fun getSourceRunner(fact: Fact): EtsForwardSourceRunner<Fact> {
        return sourceRunners.getOrPut(fact) {
            EtsForwardSourceRunner(traits, this, fact)
        }
    }

    internal fun propagateStartingFact(fact: Fact): Set<Vertex<Fact, EtsStmt>> {
        if (entrypoint == mockStmt) {
            return emptySet()
        }
        val sourceRunner = getSourceRunner(fact)
        sourceRunner.propagate(PathEdge(0, fact))
        return sourceRunner.summaryEdges
    }

    internal fun handleSummaryEdgeInCaller(
        currentEdge: Edge<Fact, EtsStmt>,
        summaryEdge: Edge<Fact, EtsStmt>,
    ) {
        val (startVertex, currentVertex) = currentEdge
        val sourceRunner = getSourceRunner(startVertex.fact)
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
