package org.usvm.dataflow.ts.infer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.jacodb.ets.base.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.graph.findDominators
import org.jacodb.ets.model.EtsMethod
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.usvm.dataflow.graph.reversed
import org.usvm.dataflow.ifds.ControlEvent
import org.usvm.dataflow.ifds.Edge
import org.usvm.dataflow.ifds.Manager
import org.usvm.dataflow.ifds.QueueEmptinessChanged
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UniRunner
import org.usvm.dataflow.ts.graph.EtsApplicationGraph
import org.usvm.dataflow.ts.infer.EtsTypeFact.Companion.allStringProperties
import org.usvm.dataflow.ts.util.EtsTraits
import org.usvm.dataflow.ts.util.getRealLocals
import org.usvm.dataflow.ts.util.sortedBy
import org.usvm.dataflow.ts.util.sortedByBase
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalCoroutinesApi::class)
class TypeInferenceManager(
    val traits: EtsTraits,
    val graph: EtsApplicationGraph,
) : Manager<Nothing, AnalyzerEvent, EtsMethod, EtsStmt>, AutoCloseable {
    private lateinit var runnerFinished: CompletableDeferred<Unit>

    private val backwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<BackwardSummaryAnalyzerEvent>>()
    private val forwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<ForwardSummaryAnalyzerEvent>>()

    private val methodDominatorsCache = ConcurrentHashMap<EtsMethod, GraphDominators<EtsStmt>>()

    private fun methodDominators(method: EtsMethod): GraphDominators<EtsStmt> =
        methodDominatorsCache.computeIfAbsent(method) {
            method.flowGraph().findDominators()
        }

    private val savedTypes: ConcurrentHashMap<EtsType, MutableList<EtsTypeFact>> = ConcurrentHashMap()

    @OptIn(DelicateCoroutinesApi::class)
    private val analyzerDispatcher = newSingleThreadContext(
        name = "${this::class.java.name}-worker"
    )

    private val analyzerScope = CoroutineScope(analyzerDispatcher)

    override fun close() {
        analyzerDispatcher.close()
    }

    fun analyze(
        entrypoints: List<EtsMethod>,
        allMethods: List<EtsMethod> = entrypoints,
        doAddKnownTypes: Boolean = true,
        doInferAllLocals: Boolean = true,
    ): TypeInferenceResult = runBlocking {
        val methodTypeScheme = collectSummaries(
            startMethods = entrypoints,
            doAddKnownTypes = doAddKnownTypes,
        )
        val remainingMethodsForAnalysis = allMethods.filter { it !in methodTypeScheme.keys }

        val updatedTypeScheme = if (remainingMethodsForAnalysis.isEmpty()) {
            methodTypeScheme
        } else {
            collectSummaries(
                startMethods = remainingMethodsForAnalysis,
                doAddKnownTypes = doAddKnownTypes,
            )
        }

        createResultsFromSummaries(updatedTypeScheme, doInferAllLocals)
    }

    lateinit var backwardRunner: UniRunner<BackwardTypeDomainFact, *, EtsMethod, EtsStmt>
    lateinit var forwardRunner: UniRunner<ForwardTypeDomainFact, *, EtsMethod, EtsStmt>

    private suspend fun collectSummaries(
        startMethods: List<EtsMethod>,
        doAddKnownTypes: Boolean = true,
    ): Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>> {
        logger.info { "Preparing backward analysis" }
        val backwardGraph = graph.reversed
        val backwardAnalyzer = BackwardAnalyzer(backwardGraph, savedTypes, ::methodDominators)
        val backwardRunner = UniRunner(
            traits = traits,
            manager = this@TypeInferenceManager,
            graph = backwardGraph,
            analyzer = backwardAnalyzer,
            unitResolver = { SingletonUnit },
            unit = SingletonUnit,
            zeroFact = BackwardTypeDomainFact.Zero,
        )
        this@TypeInferenceManager.backwardRunner = backwardRunner

        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            logger.error { "Got exception from runner, stopping analysis" }
            runnerFinished.completeExceptionally(exception)
        }

        val backwardJob = analyzerScope.launch(exceptionHandler, start = CoroutineStart.LAZY) {
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

        val methodTypeSchemeBasic = methodTypeScheme()
        val guesser = TypeGuesser(graph.cp)
        val methodTypeScheme = methodTypeSchemeBasic.mapValues { (_, facts) ->
            facts.mapValues { (_, fact) ->
                guesser.guess(fact)
            }
        }

        // logger.info {
        //     buildString {
        //         appendLine("Backward types:")
        //         for ((method, typeFacts) in methodTypeScheme) {
        //             appendLine("Backward types for ${method.enclosingClass.name}::${method.name} in ${method.enclosingClass.file}:")
        //             for ((base, fact) in typeFacts.entries.sortedBy {
        //                 when (val key = it.key) {
        //                     is AccessPathBase.This -> 0
        //                     is AccessPathBase.Arg -> key.index + 1
        //                     else -> 1_000_000
        //                 }
        //             }) {
        //                 appendLine("$base: ${fact.toPrettyString()}")
        //             }
        //         }
        //     }
        // }

        val typeInfo: Map<EtsType, EtsTypeFact> = savedTypes.mapValues { (type, facts) ->
            val typeFact = EtsTypeFact.ObjectEtsTypeFact(type, properties = emptyMap())
            facts.fold(typeFact as EtsTypeFact) { acc, it ->
                acc.intersect(it) ?: run {
                    logger.warn { "Empty intersection type: $acc & $it" }
                    acc
                }
            }
        }

        logger.info { "Preparing forward analysis" }
        val forwardGraph = graph
        val forwardAnalyzer = ForwardAnalyzer(forwardGraph, methodTypeScheme, typeInfo, doAddKnownTypes)
        val forwardRunner = UniRunner(
            traits = traits,
            manager = this@TypeInferenceManager,
            graph = forwardGraph,
            analyzer = forwardAnalyzer,
            unitResolver = { SingletonUnit },
            unit = SingletonUnit,
            zeroFact = ForwardTypeDomainFact.Zero,
        )
        this@TypeInferenceManager.forwardRunner = forwardRunner

        val forwardJob = analyzerScope.launch(exceptionHandler, start = CoroutineStart.LAZY) {
            forwardRunner.run(startMethods)
        }

        logger.info { "Running forward analysis" }
        runnerFinished = CompletableDeferred()
        forwardJob.start()
        withTimeout(3600.seconds) {
            runnerFinished.await()
        }
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

        return methodTypeScheme
    }

    private fun createResultsFromSummaries(
        methodTypeScheme: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>,
        doInferAllLocals: Boolean,
    ): TypeInferenceResult {
        val refinedTypes = refineMethodTypes(methodTypeScheme).toMutableMap()
        // logger.info {
        //     buildString {
        //         appendLine("Forward types:")
        //         for ((method, typeFacts) in refinedTypes) {
        //             appendLine("Forward types for ${method.signature.enclosingClass.name}::${method.name} in ${method.signature.enclosingClass.file}:")
        //             for ((base, fact) in typeFacts.entries.sortedBy {
        //                 when (val key = it.key) {
        //                     is AccessPathBase.This -> 0
        //                     is AccessPathBase.Arg -> key.index + 1
        //                     else -> 1_000_000
        //                 }
        //             }) {
        //                 appendLine("$base: ${fact.toPrettyString()}")
        //             }
        //         }
        //     }
        // }

        // Infer types for 'this' in each class
        val inferredCombinedThisTypes = run {
            val allClasses = methodTypeScheme.keys
                .map { it.enclosingClass }
                .distinct()
                .map { sig -> graph.cp.projectAndSdkClasses.first { cls -> cls.signature == sig } }
                .filterNot { it.name.startsWith(ANONYMOUS_CLASS_PREFIX) }
            allClasses.mapNotNull { cls ->
                val combinedBackwardType = methodTypeScheme
                    .asSequence()
                    .filter { (method, _) -> method in (cls.methods + cls.ctor) }
                    .mapNotNull { (_, facts) -> facts[AccessPathBase.This] }.reduceOrNull { acc, type ->
                        acc.intersect(type) ?: run {
                            logger.warn { "Empty intersection type: $acc & $type" }
                            acc
                        }
                    }
                // logger.info {
                //     buildString {
                //         appendLine("Combined backward type for This in class '${cls.signature}': ${combinedBackwardType?.toPrettyString()}")
                //     }
                // }

                if (combinedBackwardType == null) {
                    return@mapNotNull null
                }

                val typeFactsOnThisMethods = forwardSummaries.asSequence()
                    .filter { (method, _) -> method.enclosingClass == cls.signature }
                    .filter { (method, _) -> method.name != INSTANCE_INIT_METHOD_NAME }
                    .flatMap { (_, summaries) -> summaries.asSequence() }
                    .mapNotNull { it.initialFact as? ForwardTypeDomainFact.TypedVariable }
                    .filter { it.variable.base is AccessPathBase.This }
                    .distinct()
                    .toList()

                val typeFactsOnThisCtor = forwardSummaries.asSequence()
                    .filter { (method, _) -> method.enclosingClass == cls.signature }
                    .filter { (method, _) -> method.name == CONSTRUCTOR_NAME || method.name == INSTANCE_INIT_METHOD_NAME }
                    .flatMap { (_, summaries) -> summaries.asSequence() }
                    .mapNotNull { it.exitFact as? ForwardTypeDomainFact.TypedVariable }
                    .filter { it.variable.base is AccessPathBase.This }
                    .distinct()
                    .toList()

                val typeFactsOnThis = (typeFactsOnThisMethods + typeFactsOnThisCtor).distinct()

                val propertyRefinements = typeFactsOnThis
                    .groupBy({ it.variable.accesses }, { it.type })
                    .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

                // logger.info {
                //     buildString {
                //         appendLine("Property refinements for This in class '${cls.signature}':")
                //         for ((property, propertyType) in propertyRefinements.toList()
                //             .sortedBy { it.first.joinToString(".") }) {
                //             appendLine("  this.${property.joinToString(".")}: $propertyType")
                //         }
                //     }
                // }

                var refined: EtsTypeFact = combinedBackwardType
                for ((property, propertyType) in propertyRefinements) {
                    refined = refined.refineProperty(property, propertyType) ?: this@TypeInferenceManager.run {
                        logger.warn { "Empty intersection type: $refined & $propertyType" }
                        refined
                    }
                }

                combinedBackwardType.let {}
                typeFactsOnThisMethods.let {}
                typeFactsOnThisCtor.let {}
                typeFactsOnThis.let {}
                propertyRefinements.let {}
                cls.let {}

                cls.signature to refined
            }.toMap()
        }
        // logger.info {
        //     buildString {
        //         appendLine("Combined and refined types for This:")
        //         for ((cls, type) in inferredCombinedThisTypes) {
        //             appendLine("Combined This in class '${cls}': ${type.toPrettyString()}")
        //         }
        //     }
        // }

        // Infer return types for each method
        val inferredReturnTypes = run {
            forwardSummaries.asSequence().mapNotNull { (method, summaries) ->
                val typeFacts = summaries.asSequence().map { it.exitVertex }.mapNotNull {
                    val stmt = it.statement as? EtsReturnStmt ?: return@mapNotNull null
                    val fact = it.fact as? ForwardTypeDomainFact.TypedVariable ?: return@mapNotNull null
                    val r = stmt.returnValue?.toPath() ?: return@mapNotNull null
                    check(r.accesses.isEmpty())
                    if (fact.variable.base != r.base) return@mapNotNull null
                    r.base to fact
                }.groupBy({ it.first }, { it.second })

                val returnFact = typeFacts.mapValues { (_, typeRefinements) ->
                    val propertyRefinements = typeRefinements.groupBy({ it.variable.accesses }, { it.type })
                        .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

                    val rootType = propertyRefinements[emptyList()] ?: run {
                        if (propertyRefinements.keys.any { it.isNotEmpty() }) {
                            EtsTypeFact.ObjectEtsTypeFact(null, emptyMap())
                        } else {
                            EtsTypeFact.AnyEtsTypeFact
                        }
                    }

                    val refined = rootType.refineProperties(emptyList(), propertyRefinements)

                    refined
                }.values.reduceOrNull { acc, type -> acc.union(type) }
                if (returnFact != null) {
                    method to returnFact
                } else {
                    null
                }
            }.toMap()
        }
        logger.info {
            buildString {
                appendLine("Return types:")
                for ((method, type) in inferredReturnTypes) {
                    appendLine("Return type for ${method.signature.enclosingClass.file}::${method.signature.enclosingClass.name}::${method.name}: ${type.toPrettyString()}")
                }
            }
        }

        val inferredLocalTypes: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>? = if (doInferAllLocals) {
            forwardSummaries.asSequence().map { (method, summaries) ->
                val typeFacts = summaries.asSequence()
                    .mapNotNull { it.exitVertex.fact as? ForwardTypeDomainFact.TypedVariable }
                    .filter { it.variable.base is AccessPathBase.Local }
                    .groupBy { it.variable.base }

                val localTypes = typeFacts.mapValues { (_, typeFacts) ->
                    val propertyRefinements = typeFacts
                        .groupBy({ it.variable.accesses }, { it.type })
                        .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

                    val rootType = propertyRefinements[emptyList()] ?: run {
                        if (propertyRefinements.keys.any { it.isNotEmpty() }) {
                            EtsTypeFact.ObjectEtsTypeFact(null, emptyMap())
                        } else {
                            EtsTypeFact.AnyEtsTypeFact
                        }
                    }

                    val refined = rootType.refineProperties(emptyList(), propertyRefinements)

                    refined
                }

                method to localTypes
            }.toMap()
        } else {
            null
        }

        if (inferredLocalTypes != null) {
            logger.info {
                buildString {
                    appendLine("Local types:")
                    for ((method, localTypes) in inferredLocalTypes) {
                        appendLine("Local types for ${method.signature.enclosingClass.name}::${method.name} in ${method.signature.enclosingClass.file}:")
                        for ((base, fact) in localTypes.entries.sortedBy { (it.key as AccessPathBase.Local).name }) {
                            appendLine("$base: ${fact.toPrettyString()}")
                        }
                    }
                }
            }

            for ((method, localFacts) in inferredLocalTypes) {
                val facts = refinedTypes[method]
                if (facts == null) {
                    logger.warn { "No refined types for $method" }
                    continue
                }
                refinedTypes[method] = facts + localFacts
            }
        }

        val inferredTypes = refinedTypes
            // Sort by 'base':
            .mapValues { (_, facts) -> facts.sortedByBase() }
            // Simplify types:
            .mapValues { (_, facts) -> facts.mapValues { (_, fact) -> fact.simplify() } }

        return TypeInferenceResult(
            inferredTypes = inferredTypes,
            inferredReturnType = inferredReturnTypes,
            inferredCombinedThisType = inferredCombinedThisTypes,
        )
    }

    private fun methodTypeScheme(): Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>> =
        backwardSummaries.mapValues { (method, summaries) ->
            buildMethodTypeScheme(method, summaries).sortedByBase()
        }.sortedBy { it.key.signature.toString() }

    private fun refineMethodTypes(schema: Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>>): Map<EtsMethod, Map<AccessPathBase, EtsTypeFact>> =
        schema.mapValues { (method, facts) ->
            val summaries = forwardSummaries[method].orEmpty()
            refineMethodTypes(method, facts, summaries)
        }

    private val realLocalsBaseCache: MutableMap<EtsMethod, Set<AccessPathBase>> = hashMapOf()

    private fun getRealLocalsBase(method: EtsMethod): Set<AccessPathBase> {
        return realLocalsBaseCache.computeIfAbsent(method) {
            method.getRealLocals().mapTo(hashSetOf()) { it.toBase() }
        }
    }

    private fun buildMethodTypeScheme(
        method: EtsMethod,
        summaries: Iterable<BackwardSummaryAnalyzerEvent>,
    ): Map<AccessPathBase, EtsTypeFact> {
        val types = summaries
            .mapNotNull { it.exitFact as? BackwardTypeDomainFact.TypedVariable }
            .groupBy({ it.variable }, { it.type })
            .filter { (base, _) ->
                base is AccessPathBase.This
                    || base is AccessPathBase.Arg
                    || base is AccessPathBase.Static
                    || (base is AccessPathBase.Local && base !in getRealLocalsBase(method))
            }
            .mapValues { (_, typeFacts) ->
                typeFacts.reduce { acc, typeFact ->
                    acc.intersect(typeFact) ?: run {
                        logger.warn { "Empty intersection type: $acc & $typeFact" }
                        acc
                    }
                }
            }

        return types
    }

    private fun refineMethodTypes(
        method: EtsMethod,
        facts: Map<AccessPathBase, EtsTypeFact>, // backward types
        summaries: Iterable<ForwardSummaryAnalyzerEvent>,
    ): Map<AccessPathBase, EtsTypeFact> {
        // Contexts
        val typeFacts = summaries.asSequence()
            .mapNotNull { it.initialFact as? ForwardTypeDomainFact.TypedVariable }
            .filter {
                val base = it.variable.base
                base is AccessPathBase.This
                    || base is AccessPathBase.Arg
                    || base is AccessPathBase.Static
                    || (base is AccessPathBase.Local && base !in getRealLocalsBase(method))
            }
            .groupBy { it.variable.base }

        val refinedTypes = facts.mapValues { (base, type) ->
            // TODO: throw an error
            val typeRefinements = typeFacts[base] ?: return@mapValues type

            val propertyRefinements = typeRefinements
                .groupBy({ it.variable.accesses }, { it.type })
                .mapValues { (_, types) -> types.reduce { acc, t -> acc.union(t) } }

            val rootType = propertyRefinements[emptyList()] ?: run {
                if (propertyRefinements.keys.any { it.isNotEmpty() }) {
                    EtsTypeFact.ObjectEtsTypeFact(null, emptyMap())
                } else {
                    EtsTypeFact.AnyEtsTypeFact
                }
            }

            val refined = rootType.refineProperties(emptyList(), propertyRefinements)

            refined
        }

        typeFacts.let {}

        return refinedTypes
    }

    private fun EtsTypeFact.refineProperties(
        pathFromRootObject: List<Accessor>,
        typeRefinements: Map<List<Accessor>, EtsTypeFact>,
    ): EtsTypeFact = when (this) {
        is EtsTypeFact.NumberEtsTypeFact -> this
        is EtsTypeFact.StringEtsTypeFact -> this
        is EtsTypeFact.FunctionEtsTypeFact -> this
        is EtsTypeFact.AnyEtsTypeFact -> this
        is EtsTypeFact.BooleanEtsTypeFact -> this
        is EtsTypeFact.NullEtsTypeFact -> this
        is EtsTypeFact.UndefinedEtsTypeFact -> this

        is EtsTypeFact.UnknownEtsTypeFact -> {
            // logger.warn { "Unknown type after forward analysis" }
            EtsTypeFact.AnyEtsTypeFact
        }

        is EtsTypeFact.ArrayEtsTypeFact -> {
            // TODO: array types
            logger.warn { "TODO: Array type $this" }

            val elementPath = pathFromRootObject + ElementAccessor
            val refinedElemType =
                typeRefinements[elementPath]?.intersect(elementType) ?: elementType // todo: mb exception???
            val elemType = refinedElemType.refineProperties(elementPath, typeRefinements)

            EtsTypeFact.ArrayEtsTypeFact(elemType)
        }

        is EtsTypeFact.ObjectEtsTypeFact -> refineProperties(pathFromRootObject, typeRefinements)

        is EtsTypeFact.UnionEtsTypeFact -> EtsTypeFact.mkUnionType(
            types.mapTo(hashSetOf()) {
                it.refineProperties(
                    pathFromRootObject,
                    typeRefinements,
                )
            }
        )

        is EtsTypeFact.IntersectionEtsTypeFact -> EtsTypeFact.mkIntersectionType(
            types.mapTo(hashSetOf()) {
                it.refineProperties(
                    pathFromRootObject,
                    typeRefinements,
                )
            }
        )

        is EtsTypeFact.GuardedTypeFact -> type
            .refineProperties(pathFromRootObject, typeRefinements)
            .withGuard(guard, guardNegated)
    }

    private fun EtsTypeFact.ObjectEtsTypeFact.refineProperties(
        pathFromRootObject: List<Accessor>,
        typeRefinements: Map<List<Accessor>, EtsTypeFact>,
    ): EtsTypeFact {
        val refinedProperties = properties.mapValues { (propertyName, type) ->
            val propertyAccessor = FieldAccessor(propertyName)
            val propertyPath = pathFromRootObject + propertyAccessor
            val refinedType = typeRefinements[propertyPath]?.intersect(type) ?: type // todo: mb exception???
            refinedType.refineProperties(propertyPath, typeRefinements)
        }
        return EtsTypeFact.ObjectEtsTypeFact(cls, refinedProperties)
    }

    private fun EtsTypeFact.refineProperty(
        property: List<Accessor>,
        type: EtsTypeFact,
    ): EtsTypeFact? = when (this) {
        is EtsTypeFact.BasicType -> refineProperty(property, type)

        is EtsTypeFact.GuardedTypeFact -> this.type.refineProperty(property, type)?.withGuard(guard, guardNegated)

        is EtsTypeFact.IntersectionEtsTypeFact -> EtsTypeFact.mkIntersectionType(
            types = types.mapTo(hashSetOf()) {
                // Note: intersection is empty if any of the types is empty
                it.refineProperty(property, type) ?: return null
            }
        )

        is EtsTypeFact.UnionEtsTypeFact -> EtsTypeFact.mkUnionType(
            types = types.mapNotNullTo(hashSetOf()) {
                // Note: ignore empty types in the union
                it.refineProperty(property, type)
            }
        )
    }

    private fun EtsTypeFact.BasicType.refineProperty(
        property: List<Accessor>,
        type: EtsTypeFact,
    ): EtsTypeFact? {
        when (this) {
            EtsTypeFact.AnyEtsTypeFact,
            EtsTypeFact.FunctionEtsTypeFact,
            EtsTypeFact.NumberEtsTypeFact,
            EtsTypeFact.BooleanEtsTypeFact,
            EtsTypeFact.StringEtsTypeFact,
            EtsTypeFact.NullEtsTypeFact,
            EtsTypeFact.UndefinedEtsTypeFact,
                -> return if (property.isNotEmpty()) this else intersect(type)

            is EtsTypeFact.ArrayEtsTypeFact -> {
                // TODO: the following check(property.size == 1) fails on multiple projects
                // check(property.size == 1)
                if (property.size == 1) {
                    // val p = property.single()
                    // check(p is ElementAccessor)
                    val t = elementType.intersect(type) ?: run {
                        logger.warn { "Empty intersection of array element and refinement types: $elementType & $type" }
                        elementType
                    }
                    return EtsTypeFact.ArrayEtsTypeFact(elementType = t)
                } else {
                    return EtsTypeFact.AnyEtsTypeFact
                }
            }

            is EtsTypeFact.UnknownEtsTypeFact -> {
                // .f.g:T -> {f: {g: T}}
                // .f[i].g:T -> {f: Array<{g: T}>}
                var t = type
                for (p in property.reversed()) {
                    if (p is FieldAccessor) {
                        t = EtsTypeFact.ObjectEtsTypeFact(
                            cls = null,
                            properties = mapOf(p.name to t),
                        )
                    } else {
                        t = EtsTypeFact.ArrayEtsTypeFact(elementType = t)
                    }
                }
                return t
            }

            is EtsTypeFact.ObjectEtsTypeFact -> {
                val propertyAccessor = property.firstOrNull()
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
                        if (cls is EtsUnclearRefType && cls.name == "String") return type
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

                        // TODO("Unexpected: $this & $type")
                        return this
                    }

                    return EtsTypeFact.ObjectEtsTypeFact(type.cls, properties)
                }

                when (propertyAccessor) {
                    is FieldAccessor -> {
                        val propertyType = properties[propertyAccessor.name] ?: return this
                        val refinedProperty = propertyType.refineProperty(property.drop(1), type) ?: return null
                        val properties = this.properties + (propertyAccessor.name to refinedProperty)
                        return EtsTypeFact.ObjectEtsTypeFact(cls, properties)
                    }

                    is ElementAccessor -> {
                        // TODO: think about a proper way of handling the refinement with * accessor
                        return this
                    }
                }
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
}
