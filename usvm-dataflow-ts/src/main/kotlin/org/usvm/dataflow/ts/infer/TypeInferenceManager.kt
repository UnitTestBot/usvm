package org.usvm.dataflow.ts.infer

import analysis.type.EtsTypeFact
import analysis.type.withGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.usvm.dataflow.graph.reversed
import org.usvm.dataflow.ifds.Accessor
import org.usvm.dataflow.ifds.ControlEvent
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.ifds.Manager
import org.usvm.dataflow.ifds.QueueEmptinessChanged
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UniRunner
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.findDominators
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.dataflow.ts.util.EtsTraits
import java.util.concurrent.ConcurrentHashMap

context(EtsTraits)
class TypeInferenceManager(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>,
) : Manager<Nothing, AnalyzerEvent, EtsMethod, EtsStmt> {
    private lateinit var runnerFinished: CompletableDeferred<Unit>

    private val backwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<BackwardSummaryAnalyzerEvent>>()
    private val forwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<ForwardSummaryAnalyzerEvent>>()

    private val methodDominatorsCache = ConcurrentHashMap<EtsMethod, GraphDominators<EtsStmt>>()

    private fun methodDominators(method: EtsMethod): GraphDominators<EtsStmt> =
        methodDominatorsCache.computeIfAbsent(method) {
            method.flowGraph().findDominators()
        }

    fun analyze(startMethods: List<EtsMethod>): Unit = runBlocking(Dispatchers.Default) {
        val backwardGraph = graph.reversed
        val backwardAnalyzer = BackwardAnalyzer(backwardGraph, ::methodDominators)
        val backwardRunner = UniRunner(
            manager = this@TypeInferenceManager,
            graph = backwardGraph,
            analyzer = backwardAnalyzer,
            unitResolver = { SingletonUnit },
            unit = SingletonUnit,
            zeroFact = BackwardTypeDomainFact.Zero,
        )

        val backwardJob = launch(start = CoroutineStart.LAZY) {
            backwardRunner.run(startMethods)
        }

        runnerFinished = CompletableDeferred()
        backwardJob.start()
        runnerFinished.await()
        backwardJob.cancelAndJoin()

        val methodTypeScheme = methodTypeScheme()

        logger.info {
            buildString {
                appendLine("Backward types:")
                methodTypeScheme.values.forEach { appendLine(it) }
            }
        }

        val forwardGraph = graph
        val forwardAnalyzer = ForwardAnalyzer(forwardGraph, methodTypeScheme)
        val forwardRunner = UniRunner(
            manager = this@TypeInferenceManager,
            graph = forwardGraph,
            analyzer = forwardAnalyzer,
            unitResolver = { SingletonUnit },
            unit = SingletonUnit,
            zeroFact = ForwardTypeDomainFact.Zero,
        )

        val forwardJob = launch(start = CoroutineStart.LAZY) {
            forwardRunner.run(startMethods)
        }

        runnerFinished = CompletableDeferred()
        forwardJob.start()
        runnerFinished.await()
        forwardJob.cancelAndJoin()

        val refinedTypes = refineMethodTypes(methodTypeScheme)
        logger.info {
            buildString {
                appendLine("Forward types:")
                refinedTypes.values.forEach { appendLine(it) }
            }
        }

        backwardRunner.let {  }
        forwardRunner.let {  }
    }

    private fun methodTypeScheme(): Map<EtsMethod, EtsMethodTypeFacts> =
        backwardSummaries.mapValues { (method, summaries) ->
            buildMethodTypeScheme(method, summaries)
        }

    private fun refineMethodTypes(types: Map<EtsMethod, EtsMethodTypeFacts>): Map<EtsMethod, EtsMethodTypeFacts> =
        types.mapValues { (method, type) ->
            val summaries = forwardSummaries[method].orEmpty()
            refineMethodType(type, summaries)
        }

    private fun buildMethodTypeScheme(
        method: EtsMethod,
        summaries: Iterable<BackwardSummaryAnalyzerEvent>,
    ): EtsMethodTypeFacts {
        val types = summaries
            .mapNotNull { it.exitFact as? BackwardTypeDomainFact.TypedVariable }
            .groupBy({ it.variable }, { it.type })
            .filter { (base, _) -> base is AccessPathBase.This || base is AccessPathBase.Arg }
            .mapValues { (_, typeFacts) ->
                typeFacts.reduce { acc, typeFact ->
                    val intersection = acc.intersect(typeFact)

                    if (intersection == null) {
                        System.err.println("Empty intersection type: $acc & $typeFact")
                    }

                    intersection ?: acc
                }
            }

        return EtsMethodTypeFacts(method, types)
    }

    private fun refineMethodType(
        type: EtsMethodTypeFacts,
        summaries: Iterable<ForwardSummaryAnalyzerEvent>,
    ): EtsMethodTypeFacts {
        val typeFacts = summaries
            .mapNotNull { it.initialFact as? ForwardTypeDomainFact.TypedVariable }
            .groupBy({ it.variable.base }, { it })
            .filter { (base, _) -> base is AccessPathBase.This || base is AccessPathBase.Arg }

        val types = type.types.mapValues { (base, typeScheme) ->
            val typeRefinements = typeFacts[base] ?: return@mapValues typeScheme

            val propertyRefinements = typeRefinements
                .groupBy ({ it.variable.accesses }, { it.type })
                .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

            var refinedScheme = typeScheme
            for ((property, propertyType) in propertyRefinements) {
                refinedScheme = refinedScheme.refineProperty(property, propertyType) ?: run {
                    System.err.println("Empty intersection type: $typeScheme[$property] & $type")
                    refinedScheme
                }
            }

            refinedScheme
        }

        return EtsMethodTypeFacts(type.method, types)
    }

    private fun EtsTypeFact.refineProperty(property: List<Accessor>, type: EtsTypeFact): EtsTypeFact? =
        when (this) {
            is EtsTypeFact.BasicType -> refineProperty(property, type)
            is EtsTypeFact.GuardedTypeFact -> this.type.refineProperty(property, type)?.withGuard(guard, guardNegated)
            is EtsTypeFact.IntersectionEtsTypeFact -> EtsTypeFact.mkIntersectionType(
                types.mapTo(hashSetOf()) { it.refineProperty(property, type) ?: return null }
            )
            is EtsTypeFact.UnionEtsTypeFact -> EtsTypeFact.mkUnionType(
                types.mapNotNullTo(hashSetOf()) { it.refineProperty(property, type) }
            )
        }

    private fun EtsTypeFact.BasicType.refineProperty(
        property: List<Accessor>,
        type: EtsTypeFact
    ): EtsTypeFact? {
        when (this) {
            EtsTypeFact.AnyEtsTypeFact,
            EtsTypeFact.FunctionEtsTypeFact,
            EtsTypeFact.NumberEtsTypeFact,
            EtsTypeFact.StringEtsTypeFact,
            EtsTypeFact.UnknownEtsTypeFact -> return if (property.isNotEmpty()) this else intersect(type)

            is EtsTypeFact.ObjectEtsTypeFact -> {
                val propertyAccessor = property.firstOrNull() as? FieldAccessor
                if (propertyAccessor == null) {
                    if (type !is EtsTypeFact.ObjectEtsTypeFact || cls != null) {
                        TODO("$this & $type")
                    }

                    return EtsTypeFact.ObjectEtsTypeFact(type.cls, properties)
                }

                val propertyType = properties[propertyAccessor.name] ?: return this
                val refinedProperty = propertyType.refineProperty(property.drop(1), type) ?: return null
                val properties = this.properties + (propertyAccessor.name to refinedProperty)
                return EtsTypeFact.ObjectEtsTypeFact(cls, properties)
            }
        }
    }

    override fun handleEvent(event: AnalyzerEvent) {
        when (event) {
            is BackwardSummaryAnalyzerEvent -> {
                backwardSummaries.computeIfAbsent(event.method) {
                    ConcurrentHashMap.newKeySet()
                }.add(event)
            }

            is ForwardSummaryAnalyzerEvent -> {
                forwardSummaries.computeIfAbsent(event.method) {
                    ConcurrentHashMap.newKeySet()
                }.add(event)
            }
        }
    }

    override fun subscribeOnSummaryEdges(
        method: EtsMethod,
        scope: CoroutineScope,
        handler: (Edge<Nothing, EtsStmt>) -> Unit
    ) {
        error("No cross unit subscriptions")
    }

    override fun handleControlEvent(event: ControlEvent) {
        if (event is QueueEmptinessChanged) {
            if (event.isEmpty) {
                runnerFinished.complete(Unit)
            }
        }
    }

    companion object {
        val logger = mu.KotlinLogging.logger {}
    }
}
