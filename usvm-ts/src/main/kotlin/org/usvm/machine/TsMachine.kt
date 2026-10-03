package org.usvm.machine

import mu.KotlinLogging
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsClassValueType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.api.targets.TsTarget
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsModelUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCallDispatcher
import org.usvm.machine.call.TsUnknownCallModelCatalog
import org.usvm.machine.call.deduplicateEtsFilesBySignature
import org.usvm.machine.interpreter.TsInterpreter
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsTypeSystem
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StatisticsByMethodPrinter
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.TsStateVisualizer
import org.usvm.util.humanReadableSignature
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/** Whether symbolic analysis exhausted its paths or a configured strategy stopped it. */
enum class TsAnalysisStopReason {
    EXHAUSTED,
    STOPPED,
}

/** Collected states together with the machine completion kind. */
data class TsAnalysisResult(
    val states: List<TsState>,
    val stopReason: TsAnalysisStopReason,
    /** Reasons for satisfiable paths excluded by an explicit engine model bound. */
    val unsupportedPaths: List<String>,
)

class TsMachine(
    scene: EtsScene,
    override val options: UMachineOptions,
    private val tsOptions: TsOptions,
    private val machineObserver: UMachineObserver<TsState>? = null,
    observer: TsInterpreterObserver? = null,
    unknownCallDispatcher: TsUnknownCallDispatcher? = null,
    unknownCallModels: TsUnknownCallModelCatalog? = null,
) : UMachine<TsState>() {
    private val resolvedUnknownCallModels = when {
        unknownCallDispatcher != null -> null
        unknownCallModels != null -> unknownCallModels
        else -> TsBuiltInUnknownCallModels.catalog(tsOptions.unknownCallModelSelection)
    }?.materializeForMachine()

    private val analysisScene = resolvedUnknownCallModels
        ?.additionalSceneFiles
        ?.takeIf { modelFiles -> modelFiles.isNotEmpty() }
        ?.let { modelFiles ->
            val files = (scene.projectFiles + scene.sdkFiles + modelFiles).deduplicateEtsFilesBySignature()
            EtsScene(
                projectFiles = files.filter { it !in scene.sdkFiles },
                sdkFiles = scene.sdkFiles,
                projectName = scene.projectName,
            )
        }
        ?: scene
    private val graph = TsGraph(analysisScene)
    private val typeSystem = TsTypeSystem(analysisScene, typeOperationsTimeout = 1.seconds, graph.hierarchy)
    private val components = TsComponents(typeSystem, options)
    private val ctx = TsContext(
        scene = analysisScene,
        components = components,
        applicationAndSdkClasses = scene.projectAndSdkClasses,
    )
    private val resolvedUnknownCallDispatcher = unknownCallDispatcher ?: TsModelUnknownCallDispatcher(
        models = requireNotNull(resolvedUnknownCallModels),
        fallback = tsOptions.unknownCallFallback,
        observer = observer,
    )
    private val interpreter = TsInterpreter(
        ctx = ctx,
        graph = graph,
        options = tsOptions,
        observer = observer,
        unknownCallDispatcher = resolvedUnknownCallDispatcher,
        throwExceptionOnStepFailure = options.throwExceptionOnStepFailure,
    )
    private val cfgStatistics = CfgStatisticsImpl(graph)

    /** Returns supported states only. Call [analyzeWithOutcome] to inspect excluded unsupported paths. */
    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): List<TsState> = analyzeWithOutcome(methods = methods, targets = targets).states

    fun analyzeWithOutcome(
        methods: List<EtsMethod>,
        targets: List<TsTarget> = emptyList(),
    ): TsAnalysisResult {
        val (initialStates, unsupportedPaths) = createInitialStates(methods, targets)

        if (initialStates.isEmpty()) {
            return TsAnalysisResult(emptyList(), TsAnalysisStopReason.EXHAUSTED, unsupportedPaths.toList())
        }

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD, CoverageZone.TRANSITIVE -> initialStates.keys.toHashSet()
                CoverageZone.CLASS -> TODO("Unsupported yet")
            }

        val coverageStatistics = CoverageStatistics<EtsMethod, EtsStmt, TsState>(
            methods = methodsToTrackCoverage,
            applicationGraph = graph,
        )

        val callGraphStatistics: PlainCallGraphStatistics<EtsMethod> =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> TODO("Unsupported yet")
            }

        val timeStatistics = TimeStatistics<EtsMethod, TsState>()

        val pathSelector = createPathSelector(
            initialStates = initialStates,
            options = options,
            applicationGraph = graph,
            timeStatistics = timeStatistics,
            coverageStatisticsFactory = { coverageStatistics },
            cfgStatisticsFactory = { cfgStatistics },
            callGraphStatisticsFactory = { callGraphStatistics },
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<TsState>(coverageStatistics) {
                    it.methodResult is TsMethodResult.TsException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val observers = mutableListOf<UMachineObserver<TsState>>(coverageStatistics)
        observers.add(statesCollector)
        if (tsOptions.enableVisualization) {
            observers += TsStateVisualizer()
        }

        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }

        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()
        val stopStrategy = object : StopStrategy {
            val strategy = createStopStrategy(
                options,
                targets,
                timeStatisticsFactory = { timeStatistics },
                stepsStatisticsFactory = { stepsStatistics },
                coverageStatisticsFactory = { coverageStatistics },
                getCollectedStatesCount = { statesCollector.collectedStates.size },
            )

            override fun shouldStop(): Boolean {
                val result = strategy.shouldStop()

                if (result) {
                    logger.warn { "Stop strategy finished execution: ${strategy.stopReason()}" }
                }

                return result
            }
        }

        observers.add(timeStatistics)
        observers.add(stepsStatistics)
        machineObserver?.let { observers.add(it) }

        if (logger.isInfoEnabled) {
            observers.add(
                StatisticsByMethodPrinter(
                    getMethods = { initialStates.keys.toList() },
                    print = logger::info,
                    getMethodSignature = { it.humanReadableSignature },
                    coverageStatistics = coverageStatistics,
                    timeStatistics = timeStatistics,
                    stepsStatistics = stepsStatistics
                )
            )
        }

        val supportedObserver = CompositeUMachineObserver(observers)
        val outcomeObserver = object : UMachineObserver<TsState> by supportedObserver {
            override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
                val unsupportedReason = state.unsupportedReason
                if (unsupportedReason != null) {
                    if (stateReachable) {
                        unsupportedPaths += unsupportedReason
                    }
                    return
                }

                supportedObserver.onStateTerminated(state, stateReachable)
            }
        }

        run(
            interpreter,
            pathSelector,
            observer = outcomeObserver,
            isStateTerminated = { state -> state.callStack.isEmpty() },
            stopStrategy = stopStrategy
        )

        val stopReason = if (pathSelector.isEmpty()) {
            TsAnalysisStopReason.EXHAUSTED
        } else {
            TsAnalysisStopReason.STOPPED
        }

        return TsAnalysisResult(
            states = statesCollector.collectedStates,
            stopReason = stopReason,
            unsupportedPaths = unsupportedPaths.toList(),
        )
    }

    private fun createInitialStates(
        methods: List<EtsMethod>,
        targets: List<TsTarget>,
    ): Pair<Map<EtsMethod, TsState>, MutableSet<String>> {
        val initialStates = mutableMapOf<EtsMethod, TsState>()
        val unsupportedPaths = mutableSetOf<String>()
        val classesBySignature = analysisScene.projectAndSdkClasses.associateBy { it.signature }

        methods.forEach { method ->
            val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
            val unsupportedParameter = method.parameters.firstNotNullOfOrNull { parameter ->
                val gap = parameter.type.constructorInputGap(genericParameters, classesBySignature)
                gap?.let { parameter to it }
            }
            if (unsupportedParameter != null) {
                val (parameter, gap) = unsupportedParameter
                unsupportedPaths += "${gap.description} '${parameter.name}' " +
                    "in ${method.humanReadableSignature} is not modeled"
            } else {
                initialStates[method] = interpreter.getInitialState(method, targets)
            }
        }

        return initialStates to unsupportedPaths
    }

    override fun close() {
        components.close()
    }
}

private enum class ConstructorInputGap(val description: String) {
    CONSTRUCTOR_VALUE("Constructor-typed parameter"),
    UNRESOLVED_INHERITED_GENERIC("Unresolved inherited generic field in parameter"),
}

private fun EtsType.constructorInputGap(
    genericParameters: Map<String, EtsGenericType>,
    classesBySignature: Map<EtsClassSignature, EtsClass>,
    visitedGenerics: Set<String> = emptySet(),
    visitedClasses: Set<EtsClassSignature> = emptySet(),
): ConstructorInputGap? = when (this) {
    is EtsClassValueType -> ConstructorInputGap.CONSTRUCTOR_VALUE
    is EtsUnionType -> types.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsIntersectionType -> types.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsTupleType -> types.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsArrayType -> elementType.constructorInputGap(
        genericParameters,
        classesBySignature,
        visitedGenerics,
        visitedClasses,
    )
    is EtsClassType -> constructorInputGapInClass(
        genericParameters,
        classesBySignature,
        visitedGenerics,
        visitedClasses,
    )
    is EtsUnclearRefType -> typeParameters.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    is EtsAliasType -> originalType.constructorInputGap(
        genericParameters,
        classesBySignature,
        visitedGenerics,
        visitedClasses,
    )
    is EtsGenericType -> {
        if (typeName in visitedGenerics) {
            null
        } else {
            val declaration = genericParameters[typeName]
            listOfNotNull(constraint, defaultType, declaration?.constraint, declaration?.defaultType)
                .firstNotNullOfOrNull {
                    it.constructorInputGap(
                        genericParameters,
                        classesBySignature,
                        visitedGenerics + typeName,
                        visitedClasses,
                    )
                }
        }
    }
    else -> null
}

private fun EtsClassType.constructorInputGapInClass(
    genericParameters: Map<String, EtsGenericType>,
    classesBySignature: Map<EtsClassSignature, EtsClass>,
    visitedGenerics: Set<String>,
    visitedClasses: Set<EtsClassSignature>,
): ConstructorInputGap? {
    val typeArgumentGap = typeParameters.firstNotNullOfOrNull {
        it.constructorInputGap(genericParameters, classesBySignature, visitedGenerics, visitedClasses)
    }
    if (typeArgumentGap != null) {
        return typeArgumentGap
    }

    if (signature in visitedClasses) {
        return null
    }

    val clazz = classesBySignature[signature] ?: return null
    val nextVisitedClasses = visitedClasses + signature
    val fieldGap = clazz.fields.firstNotNullOfOrNull { field ->
        if (field.modifiers.isStatic) return@firstNotNullOfOrNull null

        field.type.constructorInputGap(
            genericParameters,
            classesBySignature,
            visitedGenerics,
            nextVisitedClasses,
        )
    }
    if (fieldGap != null) {
        return fieldGap
    }

    val superClass = clazz.superClass ?: return null
    val superClasses = if (superClass.file == EtsFileSignature.UNKNOWN) {
        classesBySignature.values.filter { it.name == superClass.name }
    } else {
        listOfNotNull(classesBySignature[superClass])
    }

    return superClasses.firstNotNullOfOrNull { parent ->
        val parentGap = EtsClassType(parent.signature).constructorInputGap(
            genericParameters,
            classesBySignature,
            visitedGenerics,
            nextVisitedClasses,
        )
        if (parentGap != null) return@firstNotNullOfOrNull parentGap

        // The frontend omits extends type arguments, so an inherited T may contain a constructor.
        val parentGenerics = parent.typeParameters.filterIsInstance<EtsGenericType>()
            .mapTo(mutableSetOf()) { it.typeName }
        val unresolvedGenericField = parent.fields.any { field ->
            !field.modifiers.isStatic && field.type.containsGeneric(parentGenerics)
        }
        if (unresolvedGenericField) ConstructorInputGap.UNRESOLVED_INHERITED_GENERIC else null
    }
}

private fun EtsType.containsGeneric(names: Set<String>): Boolean = when (this) {
    is EtsGenericType -> typeName in names
    is EtsUnionType -> types.any { it.containsGeneric(names) }
    is EtsIntersectionType -> types.any { it.containsGeneric(names) }
    is EtsTupleType -> types.any { it.containsGeneric(names) }
    is EtsArrayType -> elementType.containsGeneric(names)
    is EtsClassType -> typeParameters.any { it.containsGeneric(names) }
    is EtsUnclearRefType -> typeParameters.any { it.containsGeneric(names) }
    is EtsAliasType -> originalType.containsGeneric(names)
    else -> false
}
