package org.usvm.ts.pbt.calls

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsState
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.contains
import org.usvm.util.mkRegisterStackLValue
import kotlin.time.TimeSource

internal class CurrentTsCallsSymbolicEngine : CallsSymbolicEngine {
    override fun search(request: CallsSymbolicSearchRequest): CallsSymbolicSearchResult {
        val startedAt = TimeSource.Monotonic.markNow()
        val unsupportedInput = request.function.inputs.firstOrNull { input ->
            input.domain != BooleanDomain && input.domain !is NumberDomain
        }
        if (unsupportedInput != null) {
            return result(
                status = CallsSymbolicStatus.UNSUPPORTED,
                startedAt = startedAt,
                diagnostic = "Only boolean and number input domains are supported; found ${unsupportedInput.domain}",
            )
        }

        return runCatching {
            searchSupported(request = request, startedAt = startedAt)
        }.getOrElse { error ->
            result(
                status = CallsSymbolicStatus.TOOL_ERROR,
                startedAt = startedAt,
                diagnostic = error.message ?: error::class.java.name,
            )
        }
    }

    private fun searchSupported(
        request: CallsSymbolicSearchRequest,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
    ): CallsSymbolicSearchResult {
        val source = request.sourceRoot.resolve(request.function.sourceFile).normalize()
        val sourceFile = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(projectFiles = listOf(sourceFile))
        val frontendEntryPoint = request.function.entryPoint.copy(
            module = requireNotNull(source.fileName).toString(),
        )
        val propertyManifest = PropertyManifest(
            propertyId = "calls.mapping",
            inputs = request.function.inputs,
            predicate = frontendEntryPoint,
        )
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = listOf(request.sourceRoot)).map(propertyManifest)
        if (mapping.predicate.status != EtsMappingStatus.EXACT) {
            return result(
                status = mapping.predicate.status.toSymbolicStatus(),
                startedAt = startedAt,
                diagnostic = mapping.predicate.diagnostics.joinToString { diagnostic -> diagnostic.message },
            )
        }

        val method = mapping.predicate.targets.single().method
        val targetCandidates = exactTargetCandidates(method, request.target)
        if (targetCandidates.isEmpty()) {
            return result(
                status = CallsSymbolicStatus.UNMAPPED,
                startedAt = startedAt,
                diagnostic = "No EtsIR statement has the exact frozen source range",
            )
        }

        val modelSelection = if (request.profile.usesFrozenModels) {
            TsUnknownCallModelSelection.Only(request.frozenModelIds)
        } else {
            TsUnknownCallModelSelection.Only(emptySet())
        }
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.REACHED_TARGET,
            randomSeed = request.seed,
            timeout = request.budget,
            solverType = SolverType.Z3,
            stopOnTargetsReached = false,
        )
        val tsOptions = TsOptions(
            unknownCallModelSelection = modelSelection,
            unknownCallFallback = request.profile.fallback,
        )
        // One source statement may lower to consecutive EtsIR instructions with the same exact source span.
        // Reaching the first instruction is the stable entry point for that statement.
        val target = TsReachabilityTarget.FinalPoint(targetCandidates.first())
        val analysis = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = ReachabilityObserver(),
        ).use { machine ->
            machine.analyze(methods = listOf(method), targets = listOf(target)) to
                machine.unknownCallModelCatalogFingerprint
        }
        val states = analysis.first
        val fingerprint = analysis.second
        if (fingerprint != request.expectedCatalogFingerprint) {
            return result(
                status = CallsSymbolicStatus.TOOL_ERROR,
                startedAt = startedAt,
                catalogFingerprint = fingerprint,
                diagnostic = "Runtime model fingerprint $fingerprint does not match the frozen manifest",
            )
        }
        if (states.isEmpty()) {
            val status = if (startedAt.elapsedNow() >= request.budget) {
                CallsSymbolicStatus.TIMEOUT
            } else {
                CallsSymbolicStatus.UNREACHED
            }

            return result(
                status = status,
                startedAt = startedAt,
                catalogFingerprint = fingerprint,
            )
        }

        val inputs = states.asSequence()
            .map { state -> resolveScalarInputs(state, method) }
            .firstOrNull { candidate ->
                candidate.zip(request.function.inputs).all { (value, input) -> value in input.domain }
            }
        if (inputs == null) {
            return result(
                status = CallsSymbolicStatus.UNREPRESENTABLE,
                startedAt = startedAt,
                catalogFingerprint = fingerprint,
                diagnostic = "No reached state has inputs inside every frozen domain",
            )
        }

        return result(
            status = CallsSymbolicStatus.REACHED,
            inputs = inputs,
            startedAt = startedAt,
            catalogFingerprint = fingerprint,
            diagnostic = "exact-source-lowering-size=${targetCandidates.size}",
        )
    }

    private fun exactTargetCandidates(method: EtsMethod, target: CallsSourceTarget): List<EtsStmt> =
        method.cfg.stmts.filter { statement ->
            val origin = statement.location.origin ?: return@filter false
            origin.startOffset == target.startOffset &&
                origin.endOffset == target.endOffset &&
                origin.startLine == target.start.line &&
                origin.startColumn == target.start.column &&
                origin.endLine == target.end.line &&
                origin.endColumn == target.end.column
        }

    private fun resolveScalarInputs(state: TsState, method: EtsMethod): List<JsConcreteValue> = with(state.ctx) {
        val model = state.models.single()

        method.parameters.mapIndexed { index, parameter ->
            val stackIndex = index + 1
            when (parameter.type) {
                EtsNumberType -> {
                    val lValue = mkRegisterStackLValue(fp64Sort, stackIndex)
                    val value = model.eval(state.memory.read(lValue).asExpr(fp64Sort)).extractDouble()
                    JsConcreteValue.number(value)
                }

                EtsBooleanType -> {
                    val lValue = mkRegisterStackLValue(boolSort, stackIndex)
                    val value = model.eval(state.memory.read(lValue).asExpr(boolSort)).toConcreteBoolValue()
                    JsConcreteValue.Boolean(value)
                }

                else -> error("Unsupported scalar parameter type: ${parameter.type}")
            }
        }
    }

    private fun result(
        status: CallsSymbolicStatus,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
        inputs: List<JsConcreteValue>? = null,
        catalogFingerprint: String? = null,
        diagnostic: String? = null,
    ) = CallsSymbolicSearchResult(
        status = status,
        inputs = inputs,
        catalogFingerprint = catalogFingerprint,
        elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds,
        diagnostic = diagnostic,
    )
}

private fun EtsMappingStatus.toSymbolicStatus(): CallsSymbolicStatus = when (this) {
    EtsMappingStatus.EXACT -> error("Exact mapping has no failure status")
    EtsMappingStatus.AMBIGUOUS -> CallsSymbolicStatus.AMBIGUOUS
    EtsMappingStatus.UNMAPPED -> CallsSymbolicStatus.UNMAPPED
    EtsMappingStatus.UNSUPPORTED -> CallsSymbolicStatus.UNSUPPORTED
}
