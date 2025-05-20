package org.usvm.dataflow.ts.ifds

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AnalyzerEvent
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.util.EtsTraits

internal class EtsIfdsMethodRunner<Fact, Event : AnalyzerEvent>(
    val graph: EtsApplicationGraph,
    val method: EtsMethod,
    val analyzer: Analyzer<Fact, Event, EtsMethod, EtsStmt>,
    val traits: EtsTraits,
    val manager: TypeInferenceManager,
    val commonRunner: EtsIfdsRunner<Fact, Event>,
) {
    internal val flowSpace = analyzer.flowFunctions
    private var enqueued: Boolean = false
    private val sourceRunnersQueue: ArrayDeque<EtsIfdsSourceRunner<Fact>> = ArrayDeque()

    internal fun enqueue(runner: EtsIfdsSourceRunner<Fact>) {
        sourceRunnersQueue.addLast(runner)
        if (!enqueued) {
            commonRunner.methodRunnersQueue.addLast(this)
            enqueued = true
        }
    }

    fun processFacts() {
        while (!sourceRunnersQueue.isEmpty()) {
            val currentSourceRunner = sourceRunnersQueue.removeFirst()
            currentSourceRunner.processFacts()
        }
        enqueued = false
    }

    internal val sourceRunners = hashMapOf<Fact, HashMap<EtsStmt, EtsIfdsSourceRunner<Fact>>>()
    internal fun getSourceRunner(vertex: Vertex<Fact, EtsStmt>): EtsIfdsSourceRunner<Fact> {
        return sourceRunners
            .getOrPut(vertex.fact) { hashMapOf() }
            .getOrPut(vertex.statement) {
                EtsIfdsSourceRunner(traits, this, vertex.statement, vertex.fact)
            }
    }

    internal fun getSourceRunners(fact: Fact): Collection<EtsIfdsSourceRunner<Fact>> {
        return sourceRunners.getOrPut(fact) {
            entrypoints.associateWithTo(hashMapOf()) {
                EtsIfdsSourceRunner(traits, this, it, fact)
            }
        }.values
    }

    internal val mockStmt = EtsNopStmt(EtsStmtLocation(method, -1))
    internal val stmts = listOf(mockStmt) + method.cfg.stmts

    internal val isExit = BooleanArray(stmts.size).apply {
        for (exit in graph.exitPoints(method)) {
            set(exit.index, true)
        }
    }

    internal val EtsStmt.index: Int
        get() = location.index + 1

    internal val successors = stmts.map { graph.successors(it).map { s -> s.index } }
    internal val entrypoints = graph.entryPoints(method).toList()

    fun addStart() {
        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            propagateStartingFact(startFact)
        }
    }

    internal data class PathEdge<Fact>(
        val endStmtIndex: Int,
        val fact: Fact,
    )

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
