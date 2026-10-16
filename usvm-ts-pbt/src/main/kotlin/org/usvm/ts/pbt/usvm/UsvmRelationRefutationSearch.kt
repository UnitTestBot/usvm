package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
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
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import java.util.concurrent.CancellationException

/** Auxiliary search outcomes, independently of the original property's truth. */
enum class UsvmRelationRefutationStatus {
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

internal data class UsvmRelationRefutationSearchResult(
    val status: UsvmRelationRefutationStatus,
    val inputs: List<JsConcreteValue>?,
    val capability: UsvmPropertyProjectionCapability,
    val diagnostics: List<CapabilityDiagnostic>,
    val timedOut: Boolean,
)

/** Shared machine lifecycle; entry and post-store triggers keep their own point semantics. */
internal class UsvmRelationRefutationSearch(
    private val scene: EtsScene,
    machineOptions: UMachineOptions,
    private val tsOptions: TsOptions,
    projectionOptions: UsvmProjectionOptions,
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

    fun capability(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
    ): UsvmPropertyProjectionCapability = UsvmProjectionCapabilityResolver(scene).resolve(
        manifest = manifest,
        mapping = mapping,
        concreteCapability = concreteCapability,
        options = projectionOptions,
    )

    fun search(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        capability: UsvmPropertyProjectionCapability,
        observer: UsvmRelationRefutationObserver,
    ): UsvmRelationRefutationSearchResult {
        val predicate = requireNotNull(mapping.predicate.exactTargetOrNull())
        val precondition = mapping.precondition?.exactTargetOrNull()
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
                targets = listOf(observer.target),
                configureInitialState = { _, state ->
                    projection = projector.configure(
                        state = state,
                        inputs = manifest.inputs,
                        bindings = predicate.bindings.inputs,
                        generator = manifest.generator,
                    )
                    observer.configureProjection(state, projection)
                    if (precondition != null) {
                        state.prependBooleanEntryPointGuard(
                            guard = precondition.method,
                            arguments = projection.inputs.map(UsvmProjectedInput::value),
                        )
                    }
                },
            )
            val extraction = observer.reached?.let { state -> resolveInputs(state, manifest, projection, shapes) }
            val status = when {
                extraction?.isFailure == true -> UsvmRelationRefutationStatus.FAILED_INPUT_RESOLUTION
                extraction?.isSuccess == true -> UsvmRelationRefutationStatus.REFUTATION_REACHED
                else -> classifyUnreached(
                    analysis,
                    solverUnknown = observer.solverUnknown,
                    runtimeLimited = observer.diagnostics.isNotEmpty(),
                    admittedPointReached = observer.admittedPointReached,
                )
            }
            val diagnostics = buildList {
                addAll(capability.symbolic.diagnostics)
                addAll(observer.diagnostics)
                analysis.unsupportedPaths.forEach { message ->
                    val diagnostic = CapabilityDiagnostic(
                        code = "usvm.relation-refutation.unsupported",
                        message = message,
                        path = "original-prefix",
                    )
                    add(diagnostic)
                }
                val failure = extraction?.exceptionOrNull()
                if (failure != null) {
                    val diagnostic = CapabilityDiagnostic(
                        code = "usvm.relation-refutation.extraction",
                        message = failure.toString(),
                        path = "inputs",
                    )
                    add(diagnostic)
                }
            }

            UsvmRelationRefutationSearchResult(
                status = status,
                inputs = extraction?.getOrNull(),
                capability = capability,
                diagnostics = diagnostics,
                timedOut = analysis.timedOut,
            )
        }
    }
}

internal open class UsvmRelationRefutationObserver : TsInterpreterObserver {
    val target = TsTarget(location = null)
    protected lateinit var projection: UsvmDeclaredDomainProjection
    protected val pointDiagnostics = mutableListOf<CapabilityDiagnostic>()
    private val runtimeLimitations = UsvmRuntimeLimitationObserver()

    val diagnostics: List<CapabilityDiagnostic>
        get() = runtimeLimitations.diagnostics + pointDiagnostics

    var reached: TsState? = null
        private set

    var solverUnknown: Boolean = false
        private set

    var admittedPointReached: Boolean = false
        protected set

    open fun configureProjection(state: TsState, projection: UsvmDeclaredDomainProjection) {
        this.projection = projection
    }

    protected fun isAdmittedRoot(scope: TsStepScope): Boolean = scope.calcOnState {
        !entryPointGuardActive && entryPointGuardOutcome == TsEntryPointGuardOutcome.NONE && callStack.size == 1
    }

    protected fun consider(candidate: TsState) {
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

    override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
        runtimeLimitations.onRuntimeFeatureLimitation(event)
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
    admittedPointReached: Boolean,
): UsvmRelationRefutationStatus {
    val guardError = analysis.states.any { it.entryPointGuardOutcome == TsEntryPointGuardOutcome.ERROR }
    val guardRejected = analysis.states.any { it.entryPointGuardOutcome == TsEntryPointGuardOutcome.REJECTED }

    return when {
        guardError -> UsvmRelationRefutationStatus.PROPERTY_ERROR
        analysis.timedOut -> UsvmRelationRefutationStatus.TIMEOUT
        solverUnknown -> UsvmRelationRefutationStatus.SOLVER_UNKNOWN
        runtimeLimited || analysis.unsupportedCall || analysis.unsupportedPaths.isNotEmpty() -> {
            UsvmRelationRefutationStatus.UNSUPPORTED
        }
        analysis.engineFailed -> UsvmRelationRefutationStatus.ENGINE_FAILURE
        analysis.stopReason != TsAnalysisStopReason.EXHAUSTED -> UsvmRelationRefutationStatus.INCOMPLETE
        guardRejected && !admittedPointReached -> UsvmRelationRefutationStatus.PRECONDITION_REJECTED
        else -> UsvmRelationRefutationStatus.NO_REFUTATION_REACHED
    }
}
