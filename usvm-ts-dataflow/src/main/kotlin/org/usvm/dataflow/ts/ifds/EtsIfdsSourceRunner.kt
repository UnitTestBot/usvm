package org.usvm.dataflow.ts.ifds

import org.jacodb.ets.model.EtsStmt
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.ifds.EtsIfdsMethodRunner.PathEdge
import org.usvm.dataflow.ts.util.EtsTraits
import org.usvm.dataflow.ts.util.etsMethod

internal class EtsIfdsSourceRunner<Fact>(
    val traits: EtsTraits,
    val methodRunner: EtsIfdsMethodRunner<Fact, *>,
    val entrypoint: EtsStmt,
    val startingFact: Fact,
) {
    private var enqueued: Boolean = false
    private val pathEdgeQueue: ArrayDeque<PathEdge<Fact>> = ArrayDeque()

    fun processFacts() {
        while (!pathEdgeQueue.isEmpty()) {
            val currentEdge = pathEdgeQueue.removeFirst()
            tabulationAlgorithmStep(currentEdge)
        }
        enqueued = false
    }

    private val successors = methodRunner.successors
    private val stmts = methodRunner.stmts
    private val mockStmt = methodRunner.mockStmt
    private val flowSpace = methodRunner.flowSpace
    val graph = methodRunner.graph

    private val factsAtStmt = Array(stmts.size) { hashSetOf<Fact>() }

    internal fun propagate(localEdge: PathEdge<Fact>) {
        val (endStmtIndex, endFact) = localEdge
        if (factsAtStmt[endStmtIndex].add(endFact)) {
            val startVertex = Vertex(mockStmt, startingFact)
            val endVertex = Vertex(stmts[endStmtIndex], endFact)
            val edge = Edge(startVertex, endVertex)
            val events = methodRunner.analyzer.handleNewEdge(edge)

            for (event in events) {
                methodRunner.manager.handleEvent(event)
            }

            pathEdgeQueue.add(localEdge)
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
        val (currentStmtIndex, currentFact) = currentEdge
        val currentStmt = stmts[currentStmtIndex]

        val currentIsCall = getCallExpr(currentStmt) != null
        val currentIsExit = methodRunner.isExit[currentStmtIndex]

        if (currentIsCall) {
            val callToReturnFlowFunction = flowSpace
                .obtainCallToReturnSiteFlowFunction(currentStmt, mockStmt)

            // Propagate through the call-to-return-site edge:
            val factsAtReturnSite = callToReturnFlowFunction.compute(currentFact)
            for (returnSite in successors[currentStmtIndex]) {
                for (returnSiteFact in factsAtReturnSite) {
                    val edge = PathEdge(returnSite, returnSiteFact)
                    propagate(edge)
                }
            }

            for (callee in graph.callees(currentStmt)) {
                for (calleeStart in graph.entryPoints(callee)) {
                    val callToStartFlowFunction = flowSpace.obtainCallToStartFlowFunction(currentStmt, calleeStart)
                    val factsAtCalleeStart = callToStartFlowFunction.compute(currentFact)

                    for (calleeStartFact in factsAtCalleeStart) {
                        val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                        // Save info about the call for summary edges that will be found later:
                        val calleeRunner = methodRunner.commonRunner.getMethodRunner(callee)
                        val currentVertex = Vertex(currentStmt, currentFact)
                        val startingVertex = Vertex(entrypoint, startingFact)
                        val callerEdge = Edge(startingVertex, currentVertex)
                        for (calleeSourceRunner in calleeRunner.getSourceRunners(startingFact)) {
                            calleeSourceRunner.callerPathEdges.add(callerEdge)
                        }

                        // Initialize analysis of callee:
                        val summaryEdges = calleeRunner.propagateStartingFact(calleeStartFact)

                        // Handle already-found summary edges:
                        for (exitVertex in summaryEdges) {
                            val summaryEdge = Edge(calleeStartVertex, exitVertex)
                            methodRunner.handleSummaryEdgeInCaller(callerEdge, summaryEdge)
                        }
                    }
                }
            }
        } else {
            if (currentIsExit) {
                val startVertex = Vertex(entrypoint, startingFact)
                val currentVertex = Vertex(currentStmt, currentFact)

                // Propagate through the summary edge:
                for (callerPathEdge in callerPathEdges) {
                    val summaryEdge = Edge(startVertex, currentVertex)
                    val caller = callerPathEdge.from.etsMethod
                    val callerRunner = methodRunner.commonRunner.getMethodRunner(caller)
                    callerRunner.handleSummaryEdgeInCaller(currentEdge = callerPathEdge, summaryEdge = summaryEdge)
                }

                // Add new summary edge:
                summaryEdges.add(currentVertex)
            }

            // Simple (sequential) propagation to the next instruction:
            val factsAtNext = flowSpace
                .obtainSequentFlowFunction(currentStmt, mockStmt)
                .compute(currentFact)
            for (next in successors[currentStmtIndex]) {
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
