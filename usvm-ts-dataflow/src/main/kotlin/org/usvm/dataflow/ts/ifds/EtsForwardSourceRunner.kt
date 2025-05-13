package org.usvm.dataflow.ts.ifds

import org.jacodb.ets.model.EtsStmt
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.ifds.EtsForwardMethodRunner.PathEdge
import org.usvm.dataflow.ts.util.EtsTraits

class EtsForwardSourceRunner<Fact>(
    val traits: EtsTraits,
    val methodRunner: EtsForwardMethodRunner<Fact, *>,
    val startingFact: Fact,
) {
    private var enqueued: Boolean = false
    private val internalQueue: ArrayDeque<PathEdge<Fact>> = ArrayDeque()

    fun processFacts() {
        while (!internalQueue.isEmpty()) {
            val currentEdge = internalQueue.removeFirst()
            tabulationAlgorithmStep(currentEdge)
        }
        enqueued = false
    }

    private val successors = methodRunner.successors
    private val stmts = methodRunner.stmts
    private val mockStmt = methodRunner.mockStmt
    private val entrypoint = methodRunner.entrypoint

    private val callToReturnSiteFlowFunction = methodRunner.callToReturnSiteFlowFunction
    private val callToStartFlowFunctions = methodRunner.callToStartFlowFunctions
    private val sequentFlowFunction = methodRunner.sequentFlowFunction

    private val factsAtStmt = Array(stmts.size) { hashSetOf<Fact>() }

    internal fun propagate(edge: PathEdge<Fact>) {
        val (ip, fact) = edge
        if (factsAtStmt[ip].add(fact)) {
            val startVertex = Vertex(mockStmt, startingFact)
            val endVertex = Vertex(stmts[ip], fact)
            for (event in methodRunner.analyzer.handleNewEdge(Edge(startVertex, endVertex))) {
                methodRunner.manager.handleEvent(event)
            }

            internalQueue.add(edge)
            if (!enqueued) {
                enqueued = true
                methodRunner.enqueue(this)
            }
        }
    }

    private val callerPathEdges = hashSetOf<Edge<Fact, EtsStmt>>()
    internal val summaryEdges = hashSetOf<Vertex<Fact, EtsStmt>>()

    private fun tabulationAlgorithmStep(
        currentEdge: PathEdge<Fact>,
    ) = with(traits) {
        val (currentIp, currentFact) = currentEdge
        val current = stmts[currentIp]

        val currentIsCall = getCallExpr(current) != null
        val currentIsExit = methodRunner.isExit[currentIp]

        if (currentIsCall) {
            val callToReturnFlowFunction = callToReturnSiteFlowFunction[currentIp]
            if (callToReturnFlowFunction != null) {
                // Propagate through the call-to-return-site edge:
                val factsAtReturnSite = callToReturnFlowFunction.compute(currentFact)
                for (returnSite in successors[currentIp]) {
                    for (returnSiteFact in factsAtReturnSite) {
                        val edge = PathEdge(returnSite, returnSiteFact)
                        propagate(edge)
                    }
                }
            }

            for ((calleeStart, callToStartFlowFunction) in callToStartFlowFunctions[currentIp]) {
                val callee = calleeStart.method
                val factsAtCalleeStart = callToStartFlowFunction.compute(currentFact)

                for (calleeStartFact in factsAtCalleeStart) {
                    val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                    // Save info about the call for summary edges that will be found later:
                    val calleeRunner = methodRunner.commonRunner.getMethodRunner(callee)
                    val startingVertex = Vertex(entrypoint, startingFact)
                    val currentVertex = Vertex(current, currentFact)
                    val callerEdge = Edge(startingVertex, currentVertex)
                    calleeRunner.getSourceRunner(calleeStartFact).callerPathEdges.add(callerEdge)

                    // Initialize analysis of callee:
                    val summaryEdges = calleeRunner.propagateStartingFact(calleeStartFact)

                    // Handle already-found summary edges:
                    for (exitVertex in summaryEdges) {
                        val summaryEdge = Edge(calleeStartVertex, exitVertex)
                        methodRunner.handleSummaryEdgeInCaller(callerEdge, summaryEdge)
                    }
                }
            }
        } else {
            if (currentIsExit) {
                val startVertex = Vertex(entrypoint, startingFact)
                val currentVertex = Vertex(current, currentFact)

                // Propagate through the summary edge:
                for (callerPathEdge in callerPathEdges) {
                    val summaryEdge = Edge(startVertex, currentVertex)
                    val caller = callerPathEdge.from.statement.method
                    val callerRunner = methodRunner.commonRunner.getMethodRunner(caller)
                    callerRunner.handleSummaryEdgeInCaller(currentEdge = callerPathEdge, summaryEdge = summaryEdge)
                }

                // Add new summary edge:
                summaryEdges.add(currentVertex)
            }

            // Simple (sequential) propagation to the next instruction:
            val factsAtNext = sequentFlowFunction[currentIp].compute(currentFact)
            for (next in successors[currentIp]) {
                for (nextFact in factsAtNext) {
                    val edge = PathEdge(next, nextFact)
                    propagate(edge)
                }
            }
        }
    }

    internal fun getPathEdges(): List<Edge<Fact, EtsStmt>> {
        val startVertex = Vertex(entrypoint, startingFact)
        return factsAtStmt.flatMapIndexed { index, facts ->
            val stmt = stmts[index]
            facts.map {
                val endVertex = Vertex(stmt, it)
                Edge(startVertex, endVertex)
            }
        }
    }
}
