package org.usvm.dataflow.ts.ifds

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt
import org.usvm.dataflow.ifds.Analyzer
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.IfdsResult
import org.usvm.dataflow.ifds.QueueEmptinessChanged
import org.usvm.dataflow.ifds.Reason
import org.usvm.dataflow.ifds.Runner
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UnitType
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.AnalyzerEvent
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.util.EtsTraits
import org.usvm.dataflow.ts.util.etsMethod

class EtsIfdsRunner<Fact, Event : AnalyzerEvent>(
    override val graph: EtsApplicationGraph,
    val analyzer: Analyzer<Fact, Event, EtsMethod, EtsStmt>,
    val traits: EtsTraits,
    val manager: TypeInferenceManager,
    private val zeroFact: Fact,
) : Runner<Fact, EtsMethod, EtsStmt> {
    internal val methodRunnersQueue: ArrayDeque<EtsIfdsMethodRunner<Fact, Event>> = ArrayDeque()
    private val queueIsEmpty = QueueEmptinessChanged(runner = this, isEmpty = true)

    private val methodRunners = hashMapOf<EtsMethod, EtsIfdsMethodRunner<Fact, Event>>()

    internal fun getMethodRunner(method: EtsMethod): EtsIfdsMethodRunner<Fact, Event> {
        return methodRunners.getOrPut(method) {
            EtsIfdsMethodRunner(
                graph = graph,
                method = method,
                analyzer = analyzer,
                traits = traits,
                manager = manager,
                commonRunner = this@EtsIfdsRunner,
            )
        }
    }

    override suspend fun run(startMethods: List<EtsMethod>) {
        for (method in startMethods) {
            getMethodRunner(method).addStart()
        }

        tabulationAlgorithm()
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val current = methodRunnersQueue.removeFirstOrNull() ?: run {
                manager.handleControlEvent(queueIsEmpty)
                return@coroutineScope
            }
            current.processFacts()
        }
    }

    override val unit: UnitType
        get() = SingletonUnit

    override fun getIfdsResult(): IfdsResult<Fact, EtsStmt> {
        val sourceRunners = methodRunners.values.flatMap { methodRunner ->
            methodRunner.sourceRunners.values.flatMap { it.values }
        }
        val pathEdges = sourceRunners.flatMap { it.getPathEdges() }

        val resultFacts: MutableMap<EtsStmt, MutableSet<Fact>> = hashMapOf()
        for (edge in pathEdges) {
            resultFacts.getOrPut(edge.to.statement) { hashSetOf() }.add(edge.to.fact)
        }

        return IfdsResult(pathEdges, resultFacts, reasons = emptyMap(), zeroFact)
    }

    override fun submitNewEdge(edge: Edge<Fact, EtsStmt>, reason: Reason<Fact, EtsStmt>) {
        val (startVertex, endVertex) = edge
        val (endStmt, endFact) = endVertex

        val localPathEdge = EtsIfdsMethodRunner.PathEdge(endStmt.location.index, endFact)
        getMethodRunner(startVertex.etsMethod).getSourceRunner(startVertex).propagate(localPathEdge)
    }
}
