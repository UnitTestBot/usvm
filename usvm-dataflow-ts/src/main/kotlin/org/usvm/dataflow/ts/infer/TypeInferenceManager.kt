package org.usvm.dataflow.ts.infer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.graph.EtsApplicationGraph
import org.jacodb.ets.graph.findDominators
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.usvm.dataflow.graph.reversed
import org.usvm.dataflow.ifds.Accessor
import org.usvm.dataflow.ifds.ControlEvent
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.ifds.Manager
import org.usvm.dataflow.ifds.QueueEmptinessChanged
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UniRunner
import org.usvm.dataflow.ifds.Vertex
import org.usvm.dataflow.ts.infer.EtsTypeFact.Companion.allStringProperties
import org.usvm.dataflow.ts.util.EtsTraits
import java.util.concurrent.ConcurrentHashMap

context(EtsTraits)
class TypeInferenceManager(
    val graph: EtsApplicationGraph,
) : Manager<Nothing, AnalyzerEvent, EtsMethod, EtsStmt> {
    private lateinit var runnerFinished: CompletableDeferred<Unit>

    private val backwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<BackwardSummaryAnalyzerEvent>>()
    private val forwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<ForwardSummaryAnalyzerEvent>>()

    private val methodDominatorsCache = ConcurrentHashMap<EtsMethod, GraphDominators<EtsStmt>>()

    private fun methodDominators(method: EtsMethod): GraphDominators<EtsStmt> =
        methodDominatorsCache.computeIfAbsent(method) {
            method.flowGraph().findDominators()
        }

    fun analyze(
        startMethods: List<EtsMethod>,
        guessUniqueTypes: Boolean = false
    ): Map<EtsMethod, EtsMethodTypeFacts> = runBlocking(Dispatchers.Default) {
        logger.info { "Preparing forward analysis" }
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

        logger.info { "Running backward analysis" }
        runnerFinished = CompletableDeferred()
        backwardJob.start()
        runnerFinished.await()
        backwardJob.cancelAndJoin()
        logger.info { "Backward analysis finished" }

        // logger.info {
        //     buildString {
        //         appendLine("Backward summaries: (${backwardSummaries.size})")
        //         for ((method, summaries) in backwardSummaries) {
        //             appendLine("=== Backward summaries for ${method.signature.enclosingClass.name}::${method.name}: (${summaries.size})")
        //             for (summary in summaries) {
        //                 appendLine("    ${summary.initialFact} -> ${summary.exitFact}")
        //             }
        //         }
        //     }
        // }

        val methodTypeScheme = methodTypeScheme()

        logger.info {
            buildString {
                appendLine("Backward types:")
                for ((method, typeFacts) in methodTypeScheme) {
                    appendLine("Backward types for ${method.enclosingClass.name}::${method.name} in ${method.enclosingClass.enclosingFile}:")
                    for ((base, fact) in typeFacts.types.entries.sortedBy {
                        when (val key = it.key) {
                            is AccessPathBase.This -> 0
                            is AccessPathBase.Arg -> key.index + 1
                            else -> 1_000_000
                        }
                    }) {
                        appendLine("$base: ${fact.toPrettyString()}")
                    }
                }
            }
        }

        logger.info { "Preparing forward analysis" }
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

        logger.info { "Running forward analysis" }
        runnerFinished = CompletableDeferred()
        forwardJob.start()
        runnerFinished.await()
        forwardJob.cancelAndJoin()
        logger.info { "Forward analysis finished" }

        // logger.info {
        //     buildString {
        //         appendLine("Forward summaries: (${forwardSummaries.size})")
        //         for ((method, summaries) in forwardSummaries) {
        //             appendLine("=== Forward summaries for ${method.signature.enclosingClass.name}::${method.name}: (${summaries.size})")
        //             for (summary in summaries) {
        //                 appendLine("    ${summary.initialFact} -> ${summary.exitFact}")
        //             }
        //         }
        //     }
        // }

        val refinedTypes = refineMethodTypes(methodTypeScheme).toMutableMap()
        logger.info {
            buildString {
                appendLine("Forward types:")
                for ((method, typeFacts) in refinedTypes) {
                    appendLine("Forward types for ${method.signature.enclosingClass.name}::${method.name} in ${method.signature.enclosingClass.enclosingFile}:")
                    for ((base, fact) in typeFacts.types.entries.sortedBy {
                        when (val key = it.key) {
                            is AccessPathBase.This -> 0
                            is AccessPathBase.Arg -> key.index + 1
                            else -> 1_000_000
                        }
                    }) {
                        appendLine("$base: ${fact.toPrettyString()}")
                    }
                }
            }
        }

        // Infer types for 'this' in each class
        run {
            val allClasses = methodTypeScheme.keys
                .map { it.enclosingClass }
                .distinct()
                .map { sig -> graph.cp.classes.firstOrNull { cls -> cls.signature == sig }!! }
                .filter { !it.name.startsWith("AnonymousClass-") }
            val combinedThis = allClasses.associateWith { cls ->
                val combinedBackwardType = methodTypeScheme
                    .asSequence()
                    .filter { (method, _) -> method in (cls.methods + cls.ctor) }
                    .mapNotNull { (_, facts) -> facts.types[AccessPathBase.This] }
                    .reduceOrNull { acc, type ->
                        val intersection = acc.intersect(type)

                        if (intersection == null) {
                            System.err.println("Empty intersection type: $acc & $type")
                        }

                        intersection ?: acc
                    }
                logger.info {
                    buildString {
                        appendLine("Combined backward type for This in class '${cls.signature}': ${combinedBackwardType?.toPrettyString()}")
                    }
                }

                if (combinedBackwardType == null) {
                    return@associateWith null
                }

                val typeFactsOnThisMethods = forwardSummaries
                    .asSequence()
                    .filter { (method, _) -> method.enclosingClass == cls.signature }
                    .filter { (method, _) -> method.name != "@instance_init" }
                    .flatMap { (_, summaries) -> summaries.asSequence() }
                    .mapNotNull { it.initialFact as? ForwardTypeDomainFact.TypedVariable }
                    .filter { it.variable.base is AccessPathBase.This }
                    .toList()
                    .distinct()

                val typeFactsOnThisCtor = forwardSummaries
                    .asSequence()
                    .filter { (method, _) -> method.enclosingClass == cls.signature }
                    .filter { (method, _) -> method.name == "constructor" || method.name == "@instance_init" }
                    .flatMap { (_, summaries) -> summaries.asSequence() }
                    .mapNotNull { it.exitFact as? ForwardTypeDomainFact.TypedVariable }
                    .filter { it.variable.base is AccessPathBase.This }
                    .toList()
                    .distinct()

                val typeFactsOnThis = (typeFactsOnThisMethods + typeFactsOnThisCtor).distinct()

                val propertyRefinements = typeFactsOnThis
                    .groupBy({ it.variable.accesses }, { it.type })
                    .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

                logger.info {
                    buildString {
                        appendLine("Property refinements for This in class '${cls.signature}':")
                        for ((property, propertyType) in propertyRefinements.toList()
                            .sortedBy { it.first.joinToString(".") }) {
                            appendLine("  this.${property.joinToString(".")}: $propertyType")
                        }
                    }
                }

                var refined: EtsTypeFact = combinedBackwardType
                for ((property, propertyType) in propertyRefinements) {
                    refined = refined.refineProperty(property, propertyType) ?: this@TypeInferenceManager.run {
                        System.err.println("Empty intersection type: $combinedBackwardType[$property] & $propertyType")
                        refined
                    }
                }

                combinedBackwardType.let {}
                typeFactsOnThisMethods.let {}
                typeFactsOnThisCtor.let {}
                typeFactsOnThis.let {}
                propertyRefinements.let {}
                cls.let {}

                refined
            }
            logger.info {
                buildString {
                    appendLine("Combined and refined types for This:")
                    for ((cls, type) in combinedThis) {
                        appendLine("Combined This in class '${cls.signature}': ${type?.toPrettyString()}")
                    }
                }
            }
        }

        // Infer return types for each method
        run {
            val returnTypes = forwardSummaries
                .asSequence()
                .mapNotNull { (method, summaries) ->
                    val returnFact = summaries
                        .asSequence()
                        .map { it.exitVertex }
                        .mapNotNull {
                            val stmt = it.statement as? EtsReturnStmt
                            val fact = it.fact as? ForwardTypeDomainFact.TypedVariable
                            if (stmt != null && fact != null) {
                                Vertex(stmt, fact)
                            } else {
                                null
                            }
                        }
                        .filter { it.statement.returnValue?.toPath() == it.fact.variable }
                        .map { it.fact.type }
                        .reduceOrNull { acc, type -> acc.union(type) }
                    if (returnFact != null) {
                        method to returnFact
                    } else {
                        null
                    }
                }
                .toMap()

            // Augment 'refinedTypes' with inferred return types
            for ((method, returnType) in returnTypes) {
                val facts = refinedTypes[method]!!
                refinedTypes[method] = facts.copy(
                    types = facts.types + (AccessPathBase.Return to returnType)
                )
            }

            logger.info {
                buildString {
                    appendLine("Return types:")
                    for ((method, type) in returnTypes) {
                        appendLine("Return type for ${method.signature.enclosingClass.file}::${method.signature.enclosingClass.name}::${method.name}: ${type.toPrettyString()}")
                    }
                }
            }
        }

        backwardRunner.let {}
        forwardRunner.let {}

        if (!guessUniqueTypes) return@runBlocking refinedTypes

        val possibleMatchedTypes = refinedTypes.mapValues { (method, facts) ->
            val types = facts.types

            if (types.isNotEmpty() && types.entries.singleOrNull()?.value != EtsTypeFact.UnknownEtsTypeFact) {
                val updatedTypes = types.mapValues { (_, fact) ->
                    fact.guessType()
                }

                return@mapValues facts.copy(types = updatedTypes)
            }

            facts
        }

        possibleMatchedTypes
    }

    private fun EtsTypeFact.guessType(): EtsTypeFact = when (this) {
        is EtsTypeFact.ArrayEtsTypeFact -> {
            val elementType = this.elementType
            if (elementType is EtsTypeFact.UnknownEtsTypeFact) {
                this
            } else {
                this.copy(elementType = elementType.guessType())
            }
        }

        is EtsTypeFact.ObjectEtsTypeFact -> {
            val touchedPropertiesNames = this.properties.keys
            val classesInSystem = graph.cp
                .classes
                .filter { cls ->
                    val methodNames = cls.methods.map { it.name }
                    val fieldNames = cls.fields.map { it.name }
                    val propertiesNames = (methodNames + fieldNames).distinct()
                    touchedPropertiesNames.all { name -> name in propertiesNames }
                }

            classesInSystem.singleOrNull()
                ?.takeUnless { it.name.startsWith("AnonymousClass-") } // TODO make it an impossible unique prefix
                ?.let {
                    println("UPDATED TYPE FOR ${it.name}")
                    // TODO how to do it properly?
                    EtsTypeFact.ObjectEtsTypeFact(
                        cls = EtsClassType(EtsClassSignature(it.name)),
                        properties = this.properties,
                    )
                } ?: this
        }

        is EtsTypeFact.FunctionEtsTypeFact -> TODO()
        is EtsTypeFact.GuardedTypeFact -> TODO()
        is EtsTypeFact.IntersectionEtsTypeFact -> TODO()
        is EtsTypeFact.UnionEtsTypeFact -> TODO()
        else -> this
    }

    private fun methodTypeScheme(): Map<EtsMethod, EtsMethodTypeFacts> =
        backwardSummaries.mapValues { (method, summaries) ->
            buildMethodTypeScheme(method, summaries)
        }

    private fun refineMethodTypes(schema: Map<EtsMethod, EtsMethodTypeFacts>): Map<EtsMethod, EtsMethodTypeFacts> =
        schema.mapValues { (method, facts) ->
            val summaries = forwardSummaries[method].orEmpty()
            refineMethodTypes(facts, summaries)
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

    private fun refineMethodTypes(
        facts: EtsMethodTypeFacts,
        summaries: Iterable<ForwardSummaryAnalyzerEvent>,
    ): EtsMethodTypeFacts {
        // Contexts
        val typeFacts = summaries
            .asSequence()
            .mapNotNull { it.initialFact as? ForwardTypeDomainFact.TypedVariable }
            .filter { it.variable.base is AccessPathBase.This || it.variable.base is AccessPathBase.Arg }
            .groupBy { it.variable.base }

        val refinedTypes = facts.types.mapValues { (base, type) ->
            val typeRefinements = typeFacts[base] ?: return@mapValues type

            val propertyRefinements = typeRefinements
                .groupBy({ it.variable.accesses }, { it.type })
                .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

            var refined = type
            for ((property, propertyType) in propertyRefinements) {
                refined = refined.refineProperty(property, propertyType) ?: run {
                    System.err.println("Empty intersection type: $type[$property] & $facts")
                    refined
                }
            }

            refined
        }

        typeFacts.let {}

        return EtsMethodTypeFacts(facts.method, refinedTypes)
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
        type: EtsTypeFact,
    ): EtsTypeFact? {
        when (this) {
            EtsTypeFact.AnyEtsTypeFact,
            EtsTypeFact.UnknownEtsTypeFact,
            EtsTypeFact.FunctionEtsTypeFact,
            is EtsTypeFact.ArrayEtsTypeFact, // TODO: refine array elements?
            EtsTypeFact.NumberEtsTypeFact,
            EtsTypeFact.BooleanEtsTypeFact,
            EtsTypeFact.StringEtsTypeFact,
            EtsTypeFact.NullEtsTypeFact,
            EtsTypeFact.UndefinedEtsTypeFact,
            -> return if (property.isNotEmpty()) this else intersect(type)

            is EtsTypeFact.ObjectEtsTypeFact -> {
                val propertyAccessor = property.firstOrNull() as? FieldAccessor
                if (propertyAccessor == null) {
                    // TODO: handle 'type=union' by exploding it into multiple ObjectFacts (later combined with union) with class names from union.
                    if (type is EtsTypeFact.UnionEtsTypeFact) {
                        return type.types.map {
                            refineProperty(property, it) ?: return null
                        }.reduce { acc: EtsTypeFact, t: EtsTypeFact -> acc.union(t) }
                    }

                    if (type is EtsTypeFact.StringEtsTypeFact) {
                        // intersect(this:object, type:string)

                        if (cls == EtsStringType) return type
                        if (cls != null) return null

                        val intersectionProperties = properties
                            .filter { it.key in allStringProperties }
                            .mapValues { (_, type) ->
                                // TODO: intersect with the corresponding type of String's property
                                type
                            }

                        return EtsTypeFact.ObjectEtsTypeFact(cls, intersectionProperties)
                    }

                    if (type is EtsTypeFact.NullEtsTypeFact) {
                        // intersect(this:object, type:null)
                        // return EtsTypeFact.NullEtsTypeFact
                        return EtsTypeFact.mkUnionType(this, EtsTypeFact.NullEtsTypeFact)
                    }

                    if (type is EtsTypeFact.UndefinedEtsTypeFact) {
                        // intersect(this:object, type:undefined)
                        // return EtsTypeFact.UndefinedEtsTypeFact
                        return EtsTypeFact.mkUnionType(this, EtsTypeFact.UndefinedEtsTypeFact)
                    }

                    if (type !is EtsTypeFact.ObjectEtsTypeFact || cls != null) {
                        // todo: hack
                        if (type is EtsTypeFact.AnyEtsTypeFact) return this

                        TODO("Unexpected: $this & $type")
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
        handler: (Edge<Nothing, EtsStmt>) -> Unit,
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
