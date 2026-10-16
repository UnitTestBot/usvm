package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.TsTarget
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsMachine
import org.usvm.machine.TsMachineAnalysisResult
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsEntryPointGuardOutcome
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.prependBooleanEntryPointGuard
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.coverage.captureCoverageIdentity
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.mapping.EtsEntryPointTarget
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.PropertyId
import java.io.IOException
import java.nio.file.Path

/** Searches mapped TypeScript predicates for concrete property violations with USVM. */
class UsvmPropertySearcher(
    private val scene: EtsScene,
    machineOptions: UMachineOptions = UMachineOptions(solverType = SolverType.YICES),
    private val tsOptions: TsOptions = TsOptions(),
    projectionOptions: UsvmProjectionOptions = UsvmProjectionOptions(),
    private val deadlineReached: (() -> Boolean)? = null,
) {
    private val machineOptions = machineOptions.copy(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        // Statement saturation cannot establish the absence of a property violation.
        stopOnCoverage = 0,
    )
    private val projectionOptions = projectionOptions.copy(
        maxSymbolicCollectionLength = minOf(projectionOptions.maxSymbolicCollectionLength, tsOptions.maxArraySize),
        maxSymbolicStringLength = minOf(projectionOptions.maxSymbolicStringLength, tsOptions.maxArraySize),
    )
    private val capabilityResolver = UsvmProjectionCapabilityResolver(scene)

    fun search(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
        coverageFeedback: UsvmCoverageFeedback? = null,
    ): UsvmPropertySearchResult = openSession(
        manifest = manifest,
        mapping = mapping,
        concreteCapability = concreteCapability,
        coverageFeedback = coverageFeedback,
    ).use { session -> session.advance() }

    /** Prepares the declared property once; later advances keep its pending and completed symbolic work. */
    fun openSession(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
        coverageFeedback: UsvmCoverageFeedback? = null,
    ): UsvmPropertySearchSession {
        val capability = capabilityResolver.resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = concreteCapability,
            options = projectionOptions,
        )
        if (capability.symbolic.level == ProjectionLevel.UNSUPPORTED) {
            val unsupported = result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.UNSUPPORTED,
                capability = capability,
            )
            return completedSession(unsupported)
        }

        val predicate = mapping.predicate.exactTargetOrNull()
        if (predicate == null) {
            val unavailable = result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.ENGINE_FAILURE,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "An exact predicate target was unavailable after capability validation",
                    path = "predicate",
                ),
            )
            return completedSession(unavailable)
        }

        val pinnedManifest = PropertyManifestJson.decode(PropertyManifestJson.encode(manifest))
        val precondition = mapping.precondition?.exactTargetOrNull()
        val guidedSelectionSupported = !machineOptions.useMerging && !machineOptions.loopIterativeDeepening &&
            machineOptions.loopIterationLimit == null
        val guidance = coverageFeedback?.let { feedback ->
            UsvmEdgeGuidance(
                scene = scene,
                manifest = pinnedManifest,
                mapping = mapping,
                feedback = feedback,
                selectionSupported = guidedSelectionSupported,
            )
        }

        return prepareSession(
            manifest = pinnedManifest,
            predicate = predicate,
            precondition = precondition,
            capability = capability,
            guidance = guidance,
            sourceRoots = mapping.provenance.sourceRoots.map(Path::of),
        )
    }

    private fun completedSession(result: UsvmPropertySearchResult) = UsvmPropertySearchSession(
        advanceSearch = { result },
        searchFinished = { true },
        applyCoverage = { UsvmCoverageUpdateResult(status = UsvmCoverageUpdateStatus.UNSUPPORTED) },
        release = {},
    )

    private fun prepareSession(
        manifest: PropertyManifest,
        predicate: EtsEntryPointTarget,
        precondition: EtsEntryPointTarget?,
        capability: UsvmPropertyProjectionCapability,
        guidance: UsvmEdgeGuidance?,
        sourceRoots: List<Path>,
    ): UsvmPropertySearchSession {
        val identity = captureCoverageIdentity(manifest, sourceRoots)
        lateinit var projection: UsvmDeclaredDomainProjection
        val target = UsvmViolationTsTarget()
        lateinit var observer: UsvmViolationObserver
        observer = UsvmViolationObserver(target) { state ->
            if (guidance != null) {
                val violation = observer.violationStates.lastOrNull { it.pathNode === state.pathNode }
                val replayState = violation ?: state
                val inputs = runCatching {
                    UsvmCandidateInputResolver(projection.objectShapes).resolve(
                        replayState, manifest.inputs, projection,
                    )
                }.getOrNull()
                if (inputs != null) guidance.confirm(replayState, inputs, expectsViolation = violation != null)
            }
        }
        val objectShapes = PlainObjectInputShapes(scene, manifest.inputs.map { it.domain })
        val domainProjector = UsvmDomainProjector(projectionOptions, objectShapes)

        val machine = TsMachine(
            scene = objectShapes.scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = observer,
            deadlineReached = deadlineReached,
            pathSelectorFactory = guidance?.takeIf { it.selectionSupported }?.let { it::selector },
        )
        var prepared = false
        val analysis = try {
            val retained = machine.openAnalysis(
                methods = listOf(predicate.method),
                targets = listOf(target),
                configureInitialState = { method, state ->
                    check(method == predicate.method)
                    projection = domainProjector.configure(
                        state = state,
                        inputs = manifest.inputs,
                        bindings = predicate.bindings.inputs,
                        generator = manifest.generator,
                    )
                    if (precondition != null) {
                        state.prependBooleanEntryPointGuard(
                            guard = precondition.method,
                            arguments = projection.inputs.map(UsvmProjectedInput::value),
                        )
                    }
                },
            )
            prepared = true
            retained
        } finally {
            if (!prepared) machine.close()
        }
        var released = false
        var invalidated = false

        fun release() {
            if (released) return

            released = true
            try {
                analysis.close()
            } finally {
                machine.close()
            }
        }

        fun invalidateChangedSource(): Boolean {
            if (invalidated) return true

            val unchanged = try {
                captureCoverageIdentity(manifest, sourceRoots) == identity
            } catch (_: IOException) {
                false
            }
            if (!unchanged) {
                invalidated = true
                release()
            }
            return invalidated
        }

        return UsvmPropertySearchSession(
            advanceSearch = { maxSteps ->
                val sourceChanged = invalidateChangedSource()
                val result = if (sourceChanged) {
                    invalidatedSourceResult(manifest, capability)
                } else {
                    val snapshot = analysis.advance(maxSteps)
                    classifySearchResult(
                        manifest = manifest,
                        capability = capability,
                        analysis = snapshot,
                        observer = observer,
                        projection = { projection },
                        finished = analysis.isFinished,
                    )
                }
                result.copy(edgeGuidance = guidance?.snapshot())
            },
            searchFinished = { invalidated || analysis.isFinished },
            applyCoverage = { artifact ->
                val sourceChanged = invalidateChangedSource()
                val update = if (sourceChanged) {
                    UsvmCoverageUpdateResult(status = UsvmCoverageUpdateStatus.SOURCE_CHANGED)
                } else {
                    guidance?.applyCoverage(artifact)
                        ?: UsvmCoverageUpdateResult(status = UsvmCoverageUpdateStatus.UNSUPPORTED)
                }
                if (update.status == UsvmCoverageUpdateStatus.SOURCE_CHANGED) {
                    invalidated = true
                    release()
                }
                update
            },
            release = ::release,
        )
    }

    private fun invalidatedSourceResult(
        manifest: PropertyManifest,
        capability: UsvmPropertyProjectionCapability,
    ) = result(
        manifest = manifest,
        status = UsvmPropertySearchStatus.SOURCE_INVALIDATED,
        capability = capability,
        additionalDiagnostic = diagnostic(
            code = PbtDiagnosticCode.USVM_SESSION_SOURCE_CHANGED,
            message = "The original property source changed; the retained analysis cannot continue",
            path = "source",
        ),
    )

    private fun classifySearchResult(
        manifest: PropertyManifest,
        capability: UsvmPropertyProjectionCapability,
        analysis: TsMachineAnalysisResult,
        observer: UsvmViolationObserver,
        projection: () -> UsvmDeclaredDomainProjection,
        finished: Boolean,
    ): UsvmPropertySearchResult {
        val violationState = observer.violationStates.firstOrNull { state ->
            state.methodResult is TsMethodResult.Success
        } ?: observer.violationStates.firstOrNull()
        val candidate = violationState?.let { state ->
            violationResult(
                manifest = manifest,
                capability = capability,
                violationState = state,
                projection = projection(),
            )
        }
        val terminalFailure = classifyTerminalFailure(
            manifest = manifest,
            capability = capability,
            analysis = analysis,
        )
        if (!finished) {
            val progress = result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.IN_PROGRESS,
                capability = capability,
            ).withCandidate(candidate)
            return progress.copy(
                diagnostics = (progress.diagnostics + terminalFailure?.diagnostics.orEmpty()).distinct(),
            )
        }
        if (terminalFailure != null) {
            return terminalFailure.withCandidate(candidate)
        }

        if (candidate != null) return candidate
        return noViolationResult(
            manifest = manifest,
            capability = capability,
            analysis = analysis,
            observer = observer,
        )
    }

    private fun classifyTerminalFailure(
        manifest: PropertyManifest,
        capability: UsvmPropertyProjectionCapability,
        analysis: TsMachineAnalysisResult,
    ): UsvmPropertySearchResult? {
        val preconditionFailure = classifyPreconditionFailure(manifest, capability, analysis)
        if (preconditionFailure != null) {
            return preconditionFailure
        }

        val predicateContractError = analysis.states.any { state ->
            val methodResult = state.methodResult as? TsMethodResult.Success
            state.entryPointGuardOutcome == TsEntryPointGuardOutcome.NONE &&
                methodResult != null &&
                methodResult.value.sort != state.ctx.boolSort
        }
        if (predicateContractError) {
            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.PROPERTY_ERROR,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_PREDICATE_RESULT_NON_BOOLEAN,
                    message = "The predicate returned a non-boolean symbolic value",
                    path = "predicate.result",
                ),
            )
        }
        val missingPredicateResult = analysis.states.any { state ->
            state.entryPointGuardOutcome == TsEntryPointGuardOutcome.NONE &&
                state.methodResult == TsMethodResult.NoCall
        }
        if (missingPredicateResult) {
            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.ENGINE_FAILURE,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "Predicate analysis terminated without a method result",
                    path = "predicate",
                ),
            )
        }
        if (analysis.unsupportedPaths.isNotEmpty()) {
            val unsupportedMessage = analysis.unsupportedPaths.joinToString()

            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.UNSUPPORTED,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_EXECUTION_UNSUPPORTED,
                    message = unsupportedMessage,
                    path = "predicate",
                ),
            )
        }
        if (analysis.unsupportedCall) {
            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.UNSUPPORTED,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_EXECUTION_UNSUPPORTED,
                    message = "The symbolic engine encountered an unsupported property call",
                    path = "predicate",
                ),
            )
        }
        if (analysis.engineFailed) {
            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.ENGINE_FAILURE,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "The symbolic engine could not execute every reachable property path",
                    path = "predicate",
                ),
            )
        }

        return null
    }

    private fun classifyPreconditionFailure(
        manifest: PropertyManifest,
        capability: UsvmPropertyProjectionCapability,
        analysis: TsMachineAnalysisResult,
    ): UsvmPropertySearchResult? {
        val preconditionError = analysis.states.firstOrNull { state ->
            state.entryPointGuardOutcome == TsEntryPointGuardOutcome.ERROR
        }
            ?: return null
        val diagnostic = when (preconditionError.methodResult) {
            is TsMethodResult.TsException -> diagnostic(
                code = PbtDiagnosticCode.USVM_PRECONDITION_THREW,
                message = "The precondition has a reachable escaping exception",
                path = "precondition",
            )

            is TsMethodResult.Success -> diagnostic(
                code = PbtDiagnosticCode.USVM_PRECONDITION_RESULT_NON_BOOLEAN,
                message = "The precondition returned a non-boolean symbolic value",
                path = "precondition.result",
            )

            TsMethodResult.NoCall -> diagnostic(
                code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                message = "The precondition guard terminated without a method result",
                path = "precondition",
            )
        }
        val status = if (preconditionError.methodResult == TsMethodResult.NoCall) {
            UsvmPropertySearchStatus.ENGINE_FAILURE
        } else {
            UsvmPropertySearchStatus.PROPERTY_ERROR
        }

        return result(
            manifest = manifest,
            status = status,
            capability = capability,
            additionalDiagnostic = diagnostic,
        )
    }

    private fun noViolationResult(
        manifest: PropertyManifest,
        capability: UsvmPropertyProjectionCapability,
        analysis: TsMachineAnalysisResult,
        observer: UsvmViolationObserver,
    ): UsvmPropertySearchResult {
        val predicateCompleted = analysis.states.any { state ->
            state.entryPointGuardOutcome == TsEntryPointGuardOutcome.NONE &&
                state.methodResult is TsMethodResult.Success
        }
        val preconditionRejected = analysis.states.any { state ->
            state.entryPointGuardOutcome == TsEntryPointGuardOutcome.REJECTED
        }
        val status = when {
            analysis.timedOut -> UsvmPropertySearchStatus.TIMEOUT
            observer.solverUnknown -> UsvmPropertySearchStatus.SOLVER_UNKNOWN
            predicateCompleted -> UsvmPropertySearchStatus.NO_VIOLATION_REACHED
            preconditionRejected -> UsvmPropertySearchStatus.PRECONDITION_REJECTED
            else -> UsvmPropertySearchStatus.ENGINE_FAILURE
        }
        val additionalDiagnostic = when (status) {
            UsvmPropertySearchStatus.SOLVER_UNKNOWN -> diagnostic(
                code = PbtDiagnosticCode.USVM_SOLVER_UNKNOWN,
                message = "The solver could not classify a predicate result",
                path = "predicate.result",
            )

            UsvmPropertySearchStatus.ENGINE_FAILURE -> diagnostic(
                code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                message = "Property search produced no classified terminal state",
                path = "predicate",
            )

            else -> null
        }

        return result(
            manifest = manifest,
            status = status,
            capability = capability,
            additionalDiagnostic = additionalDiagnostic,
        )
    }

    private fun violationResult(
        manifest: PropertyManifest,
        capability: UsvmPropertyProjectionCapability,
        violationState: TsState,
        projection: UsvmDeclaredDomainProjection,
    ): UsvmPropertySearchResult {
        val violationTarget = classifyViolation(violationState)
        val inputs = runCatching {
            UsvmCandidateInputResolver(projection.objectShapes).resolve(violationState, manifest.inputs, projection)
        }.getOrElse { failure ->
            val diagnostic = CapabilityDiagnostic(
                code = PbtDiagnosticCode.USVM_INPUT_RESOLUTION_FAILED,
                message = failure.message ?: "Failed to resolve symbolic property inputs",
                path = "inputs",
            )

            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.FAILED_INPUT_RESOLUTION,
                target = violationTarget,
                capability = capability,
                additionalDiagnostic = diagnostic,
            )
        }

        return result(
            manifest = manifest,
            status = UsvmPropertySearchStatus.VIOLATION_REACHED,
            target = violationTarget,
            inputs = inputs,
            capability = capability,
        )
    }

    private fun diagnostic(code: String, message: String, path: String) = CapabilityDiagnostic(
        code = code,
        message = message,
        path = path,
    )

    private fun classifyViolation(state: TsState): UsvmPropertyViolationTarget {
        return when (val result = state.methodResult) {
            is TsMethodResult.Success -> {
                UsvmPropertyViolationTarget.PREDICATE_FALSE
            }

            is TsMethodResult.TsException -> {
                val message = with(state.ctx) {
                    val ref = state.models.single().eval(result.value) as? org.usvm.UConcreteHeapRef
                    ref?.takeIf(::isAllocatedConcreteHeapRef)?.let(::getStringConstantValue)
                }
                if (message?.contains("AssertionError") == true) {
                    UsvmPropertyViolationTarget.ASSERTION_FAILURE
                } else {
                    UsvmPropertyViolationTarget.UNEXPECTED_EXCEPTION
                }
            }

            TsMethodResult.NoCall -> {
                error("Reached a violation target without a predicate result")
            }
        }
    }

    private fun result(
        manifest: PropertyManifest,
        status: UsvmPropertySearchStatus,
        capability: UsvmPropertyProjectionCapability,
        target: UsvmPropertyViolationTarget? = null,
        inputs: List<org.usvm.ts.pbt.model.JsConcreteValue>? = null,
        additionalDiagnostic: CapabilityDiagnostic? = null,
    ) = UsvmPropertySearchResult(
        propertyId = PropertyId(manifest.propertyId),
        status = status,
        target = target,
        inputs = inputs,
        capability = capability,
        diagnostics = capability.symbolic.diagnostics + listOfNotNull(additionalDiagnostic),
    )
}

private fun UsvmPropertySearchResult.withCandidate(
    candidate: UsvmPropertySearchResult?,
): UsvmPropertySearchResult {
    if (candidate == null) return this

    return copy(
        target = candidate.target,
        inputs = candidate.inputs,
        diagnostics = (diagnostics + candidate.diagnostics).distinct(),
    )
}

private class UsvmViolationTsTarget : TsTarget(location = null)

private class UsvmViolationObserver(
    private val target: UsvmViolationTsTarget,
    private val onCompletedState: (TsState) -> Unit = {},
) : UMachineObserver<TsState> {
    private val mutableViolationStates = mutableListOf<TsState>()

    val violationStates: List<TsState>
        get() = mutableViolationStates

    var solverUnknown: Boolean = false
        private set

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        if (
            !stateReachable ||
            state.entryPointGuardActive ||
            state.entryPointGuardOutcome != TsEntryPointGuardOutcome.NONE
        ) {
            return
        }

        when (val result = state.methodResult) {
            is TsMethodResult.TsException -> recordViolation(state)
            is TsMethodResult.Success -> with(state.ctx) {
                val returnValue = result.value.takeIf { it.sort == boolSort }?.asExpr(boolSort) ?: return@with
                val candidateState = state.clone()
                candidateState.pathConstraints += returnValue.not()
                val solverResult = solver<EtsType>().check(candidateState.pathConstraints)

                when (solverResult) {
                    is USatResult -> {
                        candidateState.models = listOf(solverResult.model)
                        recordViolation(candidateState)
                    }

                    is UUnsatResult -> Unit
                    is UUnknownResult -> solverUnknown = true
                }
            }

            TsMethodResult.NoCall -> Unit
        }
        onCompletedState(state)
    }

    private fun recordViolation(state: TsState) {
        target.propagate(state)
        mutableViolationStates += state
    }
}
