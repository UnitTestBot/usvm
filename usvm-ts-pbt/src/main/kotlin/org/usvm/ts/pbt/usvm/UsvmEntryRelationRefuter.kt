package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsType
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.TsTarget
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsMachineAnalysisResult
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsEntryPointGuardOutcome
import org.usvm.machine.state.TsState
import org.usvm.machine.state.prependBooleanEntryPointGuard
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.inference.ScalarRelation
import org.usvm.ts.pbt.inference.ScalarRelationInference
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import java.util.concurrent.CancellationException

/** Outcomes of an auxiliary relation search, independently of the original property's truth. */
enum class UsvmEntryRefutationStatus {
    REFUTATION_REACHED,
    NO_REFUTATION_REACHED,
    PRECONDITION_REJECTED,
    PROPERTY_ERROR,
    TIMEOUT,
    SOLVER_UNKNOWN,
    UNSUPPORTED,
    INCOMPLETE,
    ENGINE_FAILURE,
    FAILED_INPUT_RESOLUTION,
}

/** Reaches an admitted original input that refutes a sample; the original oracle still needs native execution. */
data class UsvmEntryRelationRefutation(
    val inference: ScalarRelationInference,
    val binding: EtsMappingResult<UsvmEntryRelationBinding>,
    val status: UsvmEntryRefutationStatus,
    val inputs: List<JsConcreteValue>?,
    val capability: UsvmPropertyProjectionCapability,
    val diagnostics: List<CapabilityDiagnostic>,
    val timedOut: Boolean = false,
) {
    init {
        require(status != UsvmEntryRefutationStatus.REFUTATION_REACHED || inputs != null)
        require(inputs == null || binding.status == EtsMappingStatus.EXACT)
    }
}

/**
 * Searches domain AND original admission AND NOT(entry relation), without negating the original oracle.
 * The standalone machine uses the supplied timeout; the campaign owns its shared deadline and cancellation.
 */
class UsvmEntryRelationRefuter(
    private val scene: EtsScene,
    machineOptions: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
    private val tsOptions: TsOptions = TsOptions(),
    projectionOptions: UsvmProjectionOptions = UsvmProjectionOptions(),
) {
    private val machineOptions = machineOptions.copy(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnTargetsReached = true,
        stopOnCoverage = 0,
    )
    private val projectionOptions = projectionOptions.copy(
        maxSymbolicCollectionLength = minOf(projectionOptions.maxSymbolicCollectionLength, tsOptions.maxArraySize),
        maxSymbolicStringLength = minOf(projectionOptions.maxSymbolicStringLength, tsOptions.maxArraySize),
    )

    fun refute(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
        inference: ScalarRelationInference,
        relation: ScalarRelation,
    ): UsvmEntryRelationRefutation {
        val binding = bindEntryRelation(manifest, mapping, inference, relation)
        val capability = UsvmProjectionCapabilityResolver(scene).resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = concreteCapability,
            options = projectionOptions,
        )
        val unsupported = binding.status != EtsMappingStatus.EXACT ||
            capability.symbolic.level == ProjectionLevel.UNSUPPORTED
        if (unsupported) {
            return UsvmEntryRelationRefutation(
                inference = inference,
                binding = binding,
                status = UsvmEntryRefutationStatus.UNSUPPORTED,
                inputs = null,
                capability = capability,
                diagnostics = capability.symbolic.diagnostics,
            )
        }

        val predicate = requireNotNull(mapping.predicate.exactTargetOrNull())
        val precondition = mapping.precondition?.exactTargetOrNull()
        val target = TsTarget(location = null)
        val runtimeLimitations = UsvmRuntimeLimitationObserver()
        val observer = EntryRefutationObserver(predicate.method, target, runtimeLimitations)
        val shapes = PlainObjectInputShapes(scene, manifest.inputs.map { it.domain })
        val projector = UsvmDomainProjector(projectionOptions, shapes)
        lateinit var projection: UsvmDeclaredDomainProjection

        return TsMachine(
            scene = shapes.scene,
            options = machineOptions,
            tsOptions = tsOptions,
            observer = observer,
        ).use { machine ->
            val analysis = machine.analyzeWithMetadata(
                methods = listOf(predicate.method),
                targets = listOf(target),
                configureInitialState = { _, state ->
                    projection = projector.configure(
                        state = state,
                        inputs = manifest.inputs,
                        bindings = predicate.bindings.inputs,
                        generator = manifest.generator,
                    )
                    state.pathConstraints += binding.targets.single().negatedRelation(state, projection)
                    if (precondition != null) {
                        state.prependBooleanEntryPointGuard(
                            guard = precondition.method,
                            arguments = projection.inputs.map(UsvmProjectedInput::value),
                        )
                    }
                },
            )
            val reached = observer.reached
            val extraction = reached?.let { state ->
                resolveInputs(state, manifest, projection, shapes)
            }
            val status = when {
                extraction?.isFailure == true -> UsvmEntryRefutationStatus.FAILED_INPUT_RESOLUTION
                extraction?.isSuccess == true -> UsvmEntryRefutationStatus.REFUTATION_REACHED
                else -> classifyUnreached(
                    analysis,
                    solverUnknown = observer.solverUnknown,
                    runtimeLimited = runtimeLimitations.diagnostics.isNotEmpty(),
                )
            }
            val diagnostics = buildList {
                addAll(capability.symbolic.diagnostics)
                addAll(runtimeLimitations.diagnostics)
                analysis.unsupportedPaths.forEach { message ->
                    val diagnostic = CapabilityDiagnostic(
                        code = "usvm.entry-refutation.unsupported",
                        message = message,
                        path = "precondition",
                    )
                    add(diagnostic)
                }
                val failure = extraction?.exceptionOrNull()
                if (failure != null) {
                    val diagnostic = CapabilityDiagnostic(
                        code = "usvm.entry-refutation.extraction",
                        message = failure.toString(),
                        path = "inputs",
                    )
                    add(diagnostic)
                }
            }

            UsvmEntryRelationRefutation(
                inference = inference,
                binding = binding,
                status = status,
                inputs = extraction?.getOrNull(),
                capability = capability,
                diagnostics = diagnostics,
                timedOut = analysis.timedOut,
            )
        }
    }
}

private fun resolveInputs(
    state: TsState,
    manifest: PropertyManifest,
    projection: UsvmDeclaredDomainProjection,
    shapes: PlainObjectInputShapes,
): Result<List<JsConcreteValue>> = try {
    Result.success(UsvmCandidateInputResolver(shapes).resolve(state, manifest.inputs, projection))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: IllegalStateException) {
    Result.failure(failure)
} catch (failure: IllegalArgumentException) {
    Result.failure(failure)
}

private fun classifyUnreached(
    analysis: TsMachineAnalysisResult,
    solverUnknown: Boolean,
    runtimeLimited: Boolean,
): UsvmEntryRefutationStatus {
    val guardError = analysis.states.any { it.entryPointGuardOutcome == TsEntryPointGuardOutcome.ERROR }
    val guardRejected = analysis.states.any { it.entryPointGuardOutcome == TsEntryPointGuardOutcome.REJECTED }

    return when {
        guardError -> UsvmEntryRefutationStatus.PROPERTY_ERROR
        analysis.timedOut -> UsvmEntryRefutationStatus.TIMEOUT
        solverUnknown -> UsvmEntryRefutationStatus.SOLVER_UNKNOWN
        runtimeLimited || analysis.unsupportedCall || analysis.unsupportedPaths.isNotEmpty() -> {
            UsvmEntryRefutationStatus.UNSUPPORTED
        }
        analysis.engineFailed -> UsvmEntryRefutationStatus.ENGINE_FAILURE
        analysis.stopReason != TsAnalysisStopReason.EXHAUSTED -> UsvmEntryRefutationStatus.INCOMPLETE
        guardRejected -> UsvmEntryRefutationStatus.PRECONDITION_REJECTED
        else -> UsvmEntryRefutationStatus.NO_REFUTATION_REACHED
    }
}

private class EntryRefutationObserver(
    private val predicate: EtsMethod,
    private val target: TsTarget,
    private val runtimeLimitations: UsvmRuntimeLimitationObserver,
) : TsInterpreterObserver {
    var reached: TsState? = null
        private set

    var solverUnknown: Boolean = false
        private set

    override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
        runtimeLimitations.onRuntimeFeatureLimitation(event)
    }

    override fun onStatementEntered(stmt: EtsStmt, scope: TsStepScope) {
        val firstEntryStatement = stmt === predicate.cfg.instructions.first()
        val admittedEntry = scope.calcOnState {
            !entryPointGuardActive && entryPointGuardOutcome == TsEntryPointGuardOutcome.NONE && callStack.size == 1
        }
        if (reached != null || !firstEntryStatement || !admittedEntry) return

        val candidate = scope.calcOnState { clone() }
        when (val result = candidate.ctx.solver<EtsType>().check(candidate.pathConstraints)) {
            is USatResult -> {
                candidate.models = listOf(result.model)
                target.propagate(candidate)
                reached = candidate
            }

            is UUnknownResult -> solverUnknown = true
            is UUnsatResult -> Unit
        }
    }
}
