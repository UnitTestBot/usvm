package org.usvm.dataflow.ts.ifds

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsMethod
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

class EtsForwardIfdsRunner<Fact, Event : AnalyzerEvent>(
    override val graph: EtsApplicationGraph,
    val analyzer: Analyzer<Fact, Event, EtsMethod, EtsStmt>,
    val traits: EtsTraits,
    val manager: TypeInferenceManager,
) : Runner<Fact, EtsMethod, EtsStmt> {
    val queue: ArrayDeque<EtsForwardMethodRunner<Fact, Event>> = ArrayDeque()

    private val queueIsEmpty = QueueEmptinessChanged(runner = this, isEmpty = true)

    private val methods = graph.cp.projectAndSdkClasses.flatMap { it.methods + it.ctor }
    private val runners = methods.associateWith {
        EtsForwardMethodRunner(
            graph = graph,
            method = it,
            analyzer = analyzer,
            traits = traits,
            manager = manager,
            commonRunner = this@EtsForwardIfdsRunner
        )
    }

    fun getMethodRunner(method: EtsMethod): EtsForwardMethodRunner<Fact, Event> {
        return runners.getValue(method)
    }

    override suspend fun run(startMethods: List<EtsMethod>) {
        for (method in startMethods) {
            val runner = runners[method] ?: continue
            runner.addStart()
            /*for ((stmt, fact) in backwardFacts[method].orEmpty()) {
                runner.submitFact(stmt, fact)
            }*/
        }

        tabulationAlgorithm()
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val current = queue.removeFirstOrNull() ?: run {
                manager.handleControlEvent(queueIsEmpty)
                return@coroutineScope
            }
            current.processFacts()
        }
    }

    override val unit: UnitType
        get() = SingletonUnit

    override fun getIfdsResult(): IfdsResult<Fact, EtsStmt> {
        TODO("Not yet implemented")
    }

    override fun submitNewEdge(edge: Edge<Fact, EtsStmt>, reason: Reason<Fact, EtsStmt>) {
        TODO("Not yet implemented")
    }
}
