package org.usvm.ts.pbt.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsMachineAnalysisResult
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.solver.USatResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsEntryPointTarget
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact

/** Overall result of projecting and evaluating one declared precondition. */
enum class UsvmPreconditionStatus {
    ACCEPTED,
    REJECTED,
    PROPERTY_ERROR,
    TIMEOUT,
    SOLVER_UNKNOWN,
    UNSUPPORTED,
    ENGINE_FAILURE,
}

/** Capability and terminal states satisfying a declared mapped precondition. */
data class UsvmPreconditionResult(
    val capability: UsvmPropertyProjectionCapability,
    val status: UsvmPreconditionStatus,
    val acceptedStates: List<TsState>,
    val diagnostics: List<CapabilityDiagnostic>,
) {
    init {
        if (status == UsvmPreconditionStatus.ACCEPTED) {
            require(acceptedStates.isNotEmpty()) { "An accepted precondition requires at least one state" }
        } else {
            require(acceptedStates.isEmpty()) { "Only an accepted precondition can expose states" }
        }
    }
}

/** Orchestrates declared-domain materialization and mapped TypeScript precondition execution. */
class UsvmPropertyProjector(
    private val scene: EtsScene,
    private val machineOptions: UMachineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
    ),
    private val tsOptions: TsOptions = TsOptions(),
    private val projectionOptions: UsvmProjectionOptions = UsvmProjectionOptions(),
) {
    private val capabilityResolver = UsvmProjectionCapabilityResolver()
    private val domainProjector = UsvmDomainProjector(projectionOptions)

    fun analyzePrecondition(
        manifest: PropertyManifest,
        mapping: PropertyEtsMappingArtifact,
        concreteCapability: ProjectionCapability,
    ): UsvmPreconditionResult {
        requireNotNull(manifest.precondition) { "Precondition analysis requires a declared precondition" }

        val capability = capabilityResolver.resolve(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = concreteCapability,
            options = projectionOptions,
        )
        val capabilityDiagnostics = capability.symbolic.diagnostics
        if (capability.symbolic.level == ProjectionLevel.UNSUPPORTED) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.UNSUPPORTED,
                diagnostics = capabilityDiagnostics,
            )
        }

        val preconditionTarget = mapping.precondition?.exactTargetOrNull()
            ?: return result(
                capability = capability,
                status = UsvmPreconditionStatus.ENGINE_FAILURE,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "An exact precondition target was unavailable after capability validation",
                    path = "precondition",
                ),
            )
        val execution = analyzePreconditionTarget(manifest, preconditionTarget)

        return classifyPreconditionAnalysis(capability, execution)
    }

    private fun analyzePreconditionTarget(
        manifest: PropertyManifest,
        preconditionTarget: EtsEntryPointTarget,
    ): UsvmPreconditionExecution = TsMachine(
        scene = scene,
        options = machineOptions,
        tsOptions = tsOptions,
    ).use { machine ->
        val analysis = machine.analyzeWithMetadata(
            methods = listOf(preconditionTarget.method),
            configureInitialState = { method, state ->
                check(method == preconditionTarget.method) {
                    "Unexpected precondition initial state for ${method.signature}"
                }

                domainProjector.configure(
                    state = state,
                    inputs = manifest.inputs,
                    bindings = preconditionTarget.bindings.inputs,
                )
            },
        )
        val canResolve = !analysis.timedOut && analysis.states.isNotEmpty() && analysis.states.all { state ->
            val methodResult = state.methodResult as? TsMethodResult.Success
            methodResult != null && methodResult.value.sort == state.ctx.boolSort
        }
        val resolutions = if (canResolve) {
            analysis.states.map(::retainTruePreconditionState)
        } else {
            emptyList()
        }

        UsvmPreconditionExecution(analysis, resolutions)
    }

    private fun classifyPreconditionAnalysis(
        capability: UsvmPropertyProjectionCapability,
        execution: UsvmPreconditionExecution,
    ): UsvmPreconditionResult {
        val analysis = execution.analysis
        val failure = classifyPreconditionFailure(capability, analysis)
        if (failure != null) {
            return failure
        }

        val capabilityDiagnostics = capability.symbolic.diagnostics
        val resolutions = execution.resolutions
        if (resolutions.any { it is PreconditionStateResolution.SolverUnknown }) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.SOLVER_UNKNOWN,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_SOLVER_UNKNOWN,
                    message = "The solver could not classify a precondition result",
                    path = "precondition.result",
                ),
            )
        }
        val acceptedStates = resolutions.mapNotNull { resolution ->
            (resolution as? PreconditionStateResolution.Accepted)?.state
        }
        val status = if (acceptedStates.isEmpty()) {
            UsvmPreconditionStatus.REJECTED
        } else {
            UsvmPreconditionStatus.ACCEPTED
        }

        return result(
            capability = capability,
            status = status,
            acceptedStates = acceptedStates,
            diagnostics = capabilityDiagnostics,
        )
    }

    private fun classifyPreconditionFailure(
        capability: UsvmPropertyProjectionCapability,
        analysis: TsMachineAnalysisResult,
    ): UsvmPreconditionResult? {
        val capabilityDiagnostics = capability.symbolic.diagnostics
        val terminalStates = analysis.states
        if (terminalStates.any { state -> state.methodResult is TsMethodResult.TsException }) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.PROPERTY_ERROR,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_PRECONDITION_THREW,
                    message = "The precondition has a reachable escaping exception",
                    path = "precondition",
                ),
            )
        }
        if (terminalStates.any { state -> state.methodResult !is TsMethodResult.Success }) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.ENGINE_FAILURE,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "Precondition analysis terminated without a method result",
                    path = "precondition",
                ),
            )
        }
        val nonBooleanResult = terminalStates.any { state ->
            val methodResult = state.methodResult as TsMethodResult.Success
            methodResult.value.sort != state.ctx.boolSort
        }
        if (nonBooleanResult) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.PROPERTY_ERROR,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_PRECONDITION_RESULT_NON_BOOLEAN,
                    message = "The precondition returned a non-boolean symbolic value",
                    path = "precondition.result",
                ),
            )
        }
        if (analysis.unsupportedCall) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.UNSUPPORTED,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_EXECUTION_UNSUPPORTED,
                    message = "The symbolic engine encountered an unsupported precondition call",
                    path = "precondition",
                ),
            )
        }
        if (analysis.engineFailed) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.ENGINE_FAILURE,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "The symbolic engine could not execute every reachable precondition path",
                    path = "precondition",
                ),
            )
        }
        if (analysis.timedOut) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.TIMEOUT,
                diagnostics = capabilityDiagnostics,
            )
        }
        if (terminalStates.isEmpty()) {
            return result(
                capability = capability,
                status = UsvmPreconditionStatus.ENGINE_FAILURE,
                diagnostics = capabilityDiagnostics + diagnostic(
                    code = PbtDiagnosticCode.USVM_ENGINE_FAILURE,
                    message = "Precondition analysis produced no terminal states",
                    path = "precondition",
                ),
            )
        }

        return null
    }

    private fun retainTruePreconditionState(state: TsState): PreconditionStateResolution {
        val result = state.methodResult as TsMethodResult.Success
        val returnValue = with(state.ctx) {
            result.value.asExpr(boolSort)
        }
        val acceptedState = state.clone()
        acceptedState.pathConstraints += returnValue
        val solverResult = acceptedState.ctx.solver<EtsType>().check(acceptedState.pathConstraints)

        return when (solverResult) {
            is USatResult -> {
                acceptedState.models = listOf(solverResult.model)
                PreconditionStateResolution.Accepted(acceptedState)
            }

            is UUnsatResult -> PreconditionStateResolution.Rejected
            is UUnknownResult -> PreconditionStateResolution.SolverUnknown
        }
    }

    private fun result(
        capability: UsvmPropertyProjectionCapability,
        status: UsvmPreconditionStatus,
        diagnostics: List<CapabilityDiagnostic>,
        acceptedStates: List<TsState> = emptyList(),
    ) = UsvmPreconditionResult(
        capability = capability,
        status = status,
        acceptedStates = acceptedStates,
        diagnostics = diagnostics,
    )

    private fun diagnostic(code: String, message: String, path: String) = CapabilityDiagnostic(
        code = code,
        message = message,
        path = path,
    )
}

private data class UsvmPreconditionExecution(
    val analysis: TsMachineAnalysisResult,
    val resolutions: List<PreconditionStateResolution>,
)

private sealed interface PreconditionStateResolution {
    data class Accepted(val state: TsState) : PreconditionStateResolution
    data object Rejected : PreconditionStateResolution
    data object SolverUnknown : PreconditionStateResolution
}
