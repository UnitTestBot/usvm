package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.usvm.PathSelectionStrategy
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
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsEntryPointTarget
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyId
import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Searches mapped TypeScript predicates for concrete property violations with USVM. */
class UsvmPropertySearcher(
    private val scene: EtsScene,
    machineOptions: UMachineOptions = UMachineOptions(
        solverType = SolverType.YICES,
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
    ),
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
        deadlineReached: (() -> Boolean)? = null,
        coverageFeedback: UsvmCoverageFeedback? = null,
        completedInputLimit: Int = 0,
    ): UsvmPropertySearchResult {
        require(completedInputLimit >= 0) { "Completed input limit must not be negative" }
        val capability = capabilityResolver.resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = concreteCapability,
            options = projectionOptions,
        )
        if (capability.symbolic.level == ProjectionLevel.UNSUPPORTED) {
            return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.UNSUPPORTED,
                capability = capability,
            )
        }

        val predicate = mapping.predicate.exactTargetOrNull()
            ?: return result(
                manifest = manifest,
                status = UsvmPropertySearchStatus.ENGINE_FAILURE,
                capability = capability,
                additionalDiagnostic = diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "An exact predicate target was unavailable after capability validation",
                    path = "predicate",
                ),
            )
        val precondition = mapping.precondition?.exactTargetOrNull()
        val guidedSelectionSupported = !machineOptions.useMerging && !machineOptions.loopIterativeDeepening &&
            machineOptions.loopIterationLimit == null
        val guidance = coverageFeedback?.let { feedback ->
            UsvmEdgeGuidance(
                scene = scene,
                manifest = manifest,
                mapping = mapping,
                feedback = feedback,
                selectionSupported = guidedSelectionSupported,
                random = Random(seed = machineOptions.randomSeed),
            )
        }

        return executeSearch(
            manifest = manifest,
            predicate = predicate,
            precondition = precondition,
            capability = capability,
            deadlineReached = deadlineReached ?: this.deadlineReached,
            guidance = guidance,
            completedInputLimit = completedInputLimit,
        ).copy(edgeGuidance = guidance?.snapshot())
    }

    private fun executeSearch(
        manifest: PropertyManifest,
        predicate: EtsEntryPointTarget,
        precondition: EtsEntryPointTarget?,
        capability: UsvmPropertyProjectionCapability,
        deadlineReached: (() -> Boolean)?,
        guidance: UsvmEdgeGuidance?,
        completedInputLimit: Int,
    ): UsvmPropertySearchResult {
        lateinit var projection: UsvmDeclaredDomainProjection
        val completedInputs = linkedSetOf<UsvmCompletedInput>()
        val completionDiagnostics = mutableListOf<CapabilityDiagnostic>()
        var omittedCompletedInputs = 0
        val target = UsvmViolationTsTarget()
        lateinit var observer: UsvmViolationObserver
        observer = UsvmViolationObserver(target) { state ->
            val methodResult = state.methodResult
            val completed = methodResult is TsMethodResult.TsException ||
                (methodResult is TsMethodResult.Success && methodResult.value.sort == state.ctx.boolSort)
            val capture = completed && completedInputLimit > 0 && completedInputs.size < completedInputLimit
            if (completed && completedInputLimit > 0 && !capture) omittedCompletedInputs += 1
            if (guidance != null || capture) {
                val violation = observer.violationStates.lastOrNull { it.pathNode === state.pathNode }
                val replayState = violation ?: state
                val resolved = resolveCompletedInput(replayState, manifest, projection)
                val inputs = resolved.getOrNull()
                if (capture && inputs == null) {
                    completionDiagnostics += diagnostic(
                        code = "usvm.completed-input.unavailable",
                        message = resolved.exceptionOrNull()?.message ?: "Original input could not be resolved",
                        path = "completedInputs",
                    )
                }
                if (inputs != null) {
                    if (capture) {
                        val expected = completedOutcome(violation)
                        completedInputs += UsvmCompletedInput(inputs = inputs, expectedOutcome = expected)
                    }
                    guidance?.confirm(replayState, inputs, expectsViolation = violation != null)
                }
            }
        }

        val runtimeLimitations = UsvmRuntimeLimitationObserver()
        val objectShapes = PlainObjectInputShapes(scene, manifest.inputs.map { it.domain })
        val domainProjector = UsvmDomainProjector(projectionOptions, objectShapes)

        return TsMachine(
            scene = objectShapes.scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = observer,
            deadlineReached = deadlineReached,
            pathSelectorFactory = guidance?.takeIf { it.selectionSupported }?.let { it::selector },
            observer = runtimeLimitations,
        ).use { machine ->
            val analysis = machine.analyzeWithMetadata(
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
            val violationState = observer.violationStates.firstOrNull { state ->
                state.methodResult is TsMethodResult.Success
            } ?: observer.violationStates.firstOrNull()
            val candidate = violationState?.let { state ->
                violationResult(
                    manifest = manifest,
                    capability = capability,
                    violationState = state,
                    projection = projection,
                )
            }
            val terminalFailure = classifyTerminalFailure(
                manifest = manifest,
                capability = capability,
                analysis = analysis,
            )
            val runtimeDiagnostics = runtimeLimitations.diagnostics
            val searchResult = terminalFailure?.withCandidate(candidate)
                ?: candidate
                ?: noViolationResult(
                    manifest = manifest,
                    capability = capability,
                    analysis = analysis,
                    observer = observer,
                    runtimeLimited = runtimeDiagnostics.isNotEmpty(),
                )

            searchResult.copy(
                diagnostics = (searchResult.diagnostics + runtimeDiagnostics + completionDiagnostics).distinct(),
                completedInputs = completedInputs.toList(),
                omittedCompletedInputs = omittedCompletedInputs,
            )
        }
    }

    private fun completedOutcome(violation: TsState?): WitnessOracleOutcome = when {
        violation?.methodResult is TsMethodResult.TsException -> WitnessOracleOutcome.THREW
        violation != null -> WitnessOracleOutcome.FALSE
        else -> WitnessOracleOutcome.HOLDS
    }

    private fun resolveCompletedInput(
        state: TsState,
        manifest: PropertyManifest,
        projection: UsvmDeclaredDomainProjection,
    ): Result<List<JsConcreteValue>> {
        val resolved = runCatching {
            UsvmCandidateInputResolver(projection.objectShapes).resolve(state, manifest.inputs, projection)
        }
        val error = resolved.exceptionOrNull()
        if (error is CancellationException) throw error
        if (error is InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        }

        return resolved
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
        runtimeLimited: Boolean,
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
            runtimeLimited -> UsvmPropertySearchStatus.UNSUPPORTED
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
        inputs: List<JsConcreteValue>? = null,
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
