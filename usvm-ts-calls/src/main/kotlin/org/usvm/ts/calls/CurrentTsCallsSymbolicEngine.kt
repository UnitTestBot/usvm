package org.usvm.ts.calls

import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsGraph
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.fastcheck.TypeScriptCompletedReturnTargetKind
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.mapping.EtsLexicalEnvironmentBinding
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.contains
import java.nio.file.Path
import kotlin.time.TimeSource

internal data class CallsSymbolicPreflightRequest(
    val sourceRoot: Path,
    val project: CallsProjectCase,
    val function: CallsFunctionCase,
    val target: CallsSourceTarget,
    val expectedNativeFrontendRevision: String,
)

internal enum class CallsSymbolicPreflightStatus {
    ELIGIBLE,
    UNSUPPORTED,
    UNMAPPED,
    AMBIGUOUS,
    TOOL_ERROR,
}

internal enum class CallsSymbolicPreflightReasonCode {
    INPUT_DOMAIN_UNSUPPORTED,
    IMPORTED_CALLEES_UNSUPPORTED,
    ENTRY_MAPPING_UNSUPPORTED,
    ENTRY_MAPPING_UNMAPPED,
    ENTRY_MAPPING_AMBIGUOUS,
    LEXICAL_CAPTURE_UNSUPPORTED,
    LEXICAL_ENVIRONMENT_UNSUPPORTED,
    DESTRUCTURING_UNSUPPORTED,
    SPREAD_UNSUPPORTED,
    REGEX_LITERAL_UNSUPPORTED,
    RAW_ENTITY_UNSUPPORTED,
    PROTOTYPE_ACCESS_UNSUPPORTED,
    SYMBOLIC_NUMBER_TO_STRING_UNSUPPORTED,
    EXPONENTIATION_UNSUPPORTED,
    TARGET_ORIGIN_UNMAPPED,
    TARGET_ORIGIN_UNSUPPORTED,
    FRONTEND_OR_PREFLIGHT_ERROR,
}

internal data class CallsSymbolicPreflightResult(
    val status: CallsSymbolicPreflightStatus,
    val reasonCode: CallsSymbolicPreflightReasonCode? = null,
    val diagnostic: String? = null,
) {
    init {
        require((status == CallsSymbolicPreflightStatus.ELIGIBLE) == (reasonCode == null)) {
            "Eligible preflight has no exclusion reason; rejected preflight has exactly one"
        }
    }
}

internal class CurrentTsCallsSymbolicEngine(
    private val environment: (String) -> String? = System::getenv,
    private val bundledNativeFrontendRevision: String = CallsBuildIdentity.nativeFrontendRevision,
) : CallsSymbolicEngine {
    private val verifiedProjects = mutableMapOf<Path, String>()
    private val preparedTargets = mutableMapOf<CallsSymbolicPreflightRequest, CallsTargetPreparation>()
    private var verifiedNativeFrontendIdentity: String? = null

    override fun search(request: CallsSymbolicSearchRequest): CallsSymbolicSearchResult {
        val startedAt = TimeSource.Monotonic.markNow()
        val preflightRequest = request.toPreflightRequest()
        val preparation = prepareSafely(preflightRequest)
        if (preparation is CallsTargetPreparation.Rejected) {
            return result(
                status = preparation.status,
                startedAt = startedAt,
                diagnostic = preparation.diagnostic,
            )
        }

        return runCatching {
            searchSupported(
                request = request,
                startedAt = startedAt,
                prepared = preparation as CallsTargetPreparation.Eligible,
            )
        }.getOrElse { error ->
            result(
                status = CallsSymbolicStatus.TOOL_ERROR,
                startedAt = startedAt,
                diagnostic = error.message ?: error::class.java.name,
            )
        }
    }

    fun preflight(request: CallsSymbolicPreflightRequest): CallsSymbolicPreflightResult =
        when (val preparation = prepareSafely(request)) {
            is CallsTargetPreparation.Eligible -> CallsSymbolicPreflightResult(
                status = CallsSymbolicPreflightStatus.ELIGIBLE,
            )

            is CallsTargetPreparation.Rejected -> CallsSymbolicPreflightResult(
                status = preparation.status.toPreflightStatus(),
                reasonCode = preparation.reasonCode,
                diagnostic = preparation.diagnostic,
            )
        }

    @Suppress("LongMethod")
    private fun searchSupported(
        request: CallsSymbolicSearchRequest,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
        prepared: CallsTargetPreparation.Eligible,
    ): CallsSymbolicSearchResult {
        val scene = prepared.scene
        val method = prepared.method
        val targetObserver = prepared.targetObservation.createObserver(method)

        val modelSelection = if (request.profile.usesFrozenModels) {
            TsUnknownCallModelSelection.Only(request.frozenModelIds)
        } else {
            TsUnknownCallModelSelection.Only(emptySet())
        }
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(CALLS_PATH_SELECTION_STRATEGY),
            stateCollectionStrategy = StateCollectionStrategy.REACHED_TARGET,
            randomSeed = request.seed,
            timeout = request.budget,
            solverType = SolverType.Z3,
            stopOnCoverage = CALLS_STOP_ON_COVERAGE,
            stopOnTargetsReached = false,
            throwExceptionOnStepFailure = true,
        )
        val tsOptions = TsOptions(
            unknownCallModelSelection = modelSelection,
            unknownCallFallback = request.profile.fallback,
        )
        val symbolicInputs = CallsSymbolicInputs(
            inputs = request.function.inputs,
            bindings = prepared.inputBindings,
            lexicalEnvironment = prepared.lexicalEnvironment,
        )
        val interpreterObserver = UnknownCallEventSinkObserver(
            sink = request.unknownCallEventSink,
            runtimeLimitationSink = request.runtimeLimitationEventSink,
        )
        val analysis = TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = tsOptions,
            initialStateConfigurator = symbolicInputs::initialize,
            initialParameterSortOverride = symbolicInputs::sortOverride,
            machineObserver = targetObserver,
            observer = interpreterObserver,
        ).use { machine ->
            val outcome = machine.analyzeWithOutcome(methods = listOf(method))
            MachineResult(
                states = targetObserver.reachedStates,
                stopReason = outcome.stopReason,
            )
        }
        val states = analysis.states
        if (states.isEmpty()) {
            val status = when (analysis.stopReason) {
                TsAnalysisStopReason.EXHAUSTED -> {
                    if (interpreterObserver.runtimeLimitations.isEmpty()) {
                        CallsSymbolicStatus.UNREACHED
                    } else {
                        CallsSymbolicStatus.RUNTIME_LIMITATION
                    }
                }
                // The machine options above disable every stop condition except the per-target timeout.
                TsAnalysisStopReason.STOPPED -> {
                    CallsSymbolicStatus.TIMEOUT
                }
            }

            return result(
                status = status,
                startedAt = startedAt,
                diagnostic = interpreterObserver.runtimeLimitations.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "Runtime feature limitations: "),
            )
        }

        val inputs = states.asSequence()
            .map(symbolicInputs::resolve)
            .firstOrNull { candidate ->
                candidate.zip(request.function.inputs).all { (value, input) -> value in input.domain }
            }
        if (inputs == null) {
            return result(
                status = CallsSymbolicStatus.UNREPRESENTABLE,
                solverReached = true,
                startedAt = startedAt,
                diagnostic = "No reached state has inputs inside every frozen domain",
            )
        }

        return result(
            status = CallsSymbolicStatus.REACHED,
            inputs = inputs,
            startedAt = startedAt,
            diagnostic = "source-target-mode=${request.target.mode};" +
                "source-statement-lowering-size=${targetObserver.loweringSize};" +
                "exact-source-lowering-size=${prepared.exactTargetCandidateCount}",
        )
    }

    private fun prepareSafely(request: CallsSymbolicPreflightRequest): CallsTargetPreparation {
        preparedTargets[request]?.let { preparation -> return preparation }

        val preparation = runCatching { prepare(request) }.getOrElse { error ->
            CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.TOOL_ERROR,
                reasonCode = CallsSymbolicPreflightReasonCode.FRONTEND_OR_PREFLIGHT_ERROR,
                diagnostic = error.message ?: error::class.java.name,
            )
        }
        preparedTargets[request] = preparation

        return preparation
    }

    @Suppress("LongMethod")
    private fun prepare(request: CallsSymbolicPreflightRequest): CallsTargetPreparation {
        callsSymbolicInputPreflight(request.function.inputs)?.let { diagnostic ->
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED,
                diagnostic = diagnostic,
            )
        }

        verifyGitCheckoutOnce(
            checkout = request.sourceRoot,
            expectedRevision = request.project.revision,
            cache = verifiedProjects,
        )
        verifyNativeFrontendOnce(expectedRevision = request.expectedNativeFrontendRevision)

        val source = request.sourceRoot.resolve(request.function.sourceFile).normalize()
        require(source.startsWith(request.sourceRoot)) { "Function source escapes its frozen source root" }
        val sourceText = java.nio.file.Files.readString(source)
        callsTargetCoordinateDiagnostic(source = sourceText, target = request.target)?.let { diagnostic ->
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNMAPPED,
                reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNMAPPED,
                diagnostic = diagnostic,
            )
        }

        val sourceFile = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        if (sourceFile.importInfos.isNotEmpty()) {
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.IMPORTED_CALLEES_UNSUPPORTED,
                diagnostic = "Single-file symbolic replay does not support imported project callees",
            )
        }

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
            return CallsTargetPreparation.Rejected(
                status = mapping.predicate.status.toSymbolicStatus(),
                reasonCode = mapping.predicate.status.toPreflightReasonCode(),
                diagnostic = mapping.predicate.diagnostics.joinToString { diagnostic -> diagnostic.message },
            )
        }

        val mappedTarget = mapping.predicate.targets.single()
        val lexicalEnvironment = mappedTarget.bindings.lexicalEnvironment
        if (lexicalEnvironment != null && lexicalEnvironment.parameter.type !is EtsLexicalEnvType) {
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.LEXICAL_CAPTURE_UNSUPPORTED,
                diagnostic = "Mapped lexical environment does not have a lexical-environment type",
            )
        }

        val unsupportedCaptures = (lexicalEnvironment?.parameter?.type as? EtsLexicalEnvType)
            ?.closures
            ?.map { closure -> closure.name }
            ?.filterNot { closure -> closure in SUPPORTED_BUILTIN_CAPTURES }
            .orEmpty()
        if (unsupportedCaptures.isNotEmpty()) {
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.LEXICAL_CAPTURE_UNSUPPORTED,
                diagnostic = "Symbolic entry point has unsupported runtime captures: " +
                    unsupportedCaptures.joinToString(),
            )
        }

        val method = mappedTarget.method
        callsIrReadinessIssue(
            method = method,
            graph = TsGraph(scene),
            source = sourceText,
            admittedLexicalEnvironment = lexicalEnvironment?.parameter?.type as? EtsLexicalEnvType,
        )?.let { issue ->
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = issue.reasonCode,
                diagnostic = issue.diagnostic,
            )
        }
        val exactTargetCandidates = exactTargetCandidates(method, request.target)
        val targetObservation = when (request.target.mode) {
            CallsSourceTargetMode.ENTRY -> {
                if (exactTargetCandidates.isEmpty()) {
                    return CallsTargetPreparation.Rejected(
                        status = CallsSymbolicStatus.UNMAPPED,
                        reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNMAPPED,
                        diagnostic = "No EtsIR statement has the exact frozen source range",
                    )
                }

                val statementEntry = sourceStatementEntry(method, request.target)
                    ?: return CallsTargetPreparation.Rejected(
                        status = CallsSymbolicStatus.UNSUPPORTED,
                        reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED,
                        diagnostic = "EtsIR origins do not prove entry before evaluation " +
                            "of the frozen source statement",
                    )

                SourceTargetObservation.Entry(statementEntry)
            }

            CallsSourceTargetMode.COMPLETED_RETURN -> {
                val completedReturnTargetKind = TypeScriptSourceInspector.completedReturnTargetKind(
                    source = source,
                    exportName = request.function.entryPoint.exportName,
                    startOffset = request.target.startOffset,
                    endOffset = request.target.endOffset,
                    expressionStartOffset = request.target.returnExpressionStartOffset,
                    expressionEndOffset = request.target.returnExpressionEndOffset,
                ) ?: return CallsTargetPreparation.Rejected(
                    status = CallsSymbolicStatus.UNSUPPORTED,
                    reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED,
                    diagnostic = "TypeScript AST does not identify the requested completed return",
                )
                val returnStatement = completedReturnCandidate(
                    method = method,
                    target = request.target,
                    exactTargetCandidates = exactTargetCandidates,
                    allowsUniqueOriginlessReturn = {
                        completedReturnTargetKind == TypeScriptCompletedReturnTargetKind.EXPRESSION_ARROW
                    },
                ) ?: return CallsTargetPreparation.Rejected(
                    status = CallsSymbolicStatus.UNSUPPORTED,
                    reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED,
                    diagnostic = "EtsIR does not identify one requested return statement",
                )

                SourceTargetObservation.CompletedReturn(returnStatement)
            }
        }

        return CallsTargetPreparation.Eligible(
            scene = scene,
            method = method,
            inputBindings = mappedTarget.bindings.inputs,
            lexicalEnvironment = mappedTarget.bindings.lexicalEnvironment,
            targetObservation = targetObservation,
            exactTargetCandidateCount = exactTargetCandidates.size,
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

    private fun completedReturnCandidate(
        method: EtsMethod,
        target: CallsSourceTarget,
        exactTargetCandidates: List<EtsStmt>,
        allowsUniqueOriginlessReturn: () -> Boolean,
    ): EtsReturnStmt? {
        val exactReturns = exactTargetCandidates.filterIsInstance<EtsReturnStmt>()
        if (exactReturns.isNotEmpty()) {
            return exactReturns.singleOrNull()
        }

        val expressionStart = target.returnExpressionStartOffset ?: return null
        val expressionEnd = target.returnExpressionEndOffset ?: return null
        val targetIsExpression = target.startOffset == expressionStart && target.endOffset == expressionEnd
        if (!targetIsExpression) {
            return null
        }

        val containingReturns = method.cfg.stmts.filterIsInstance<EtsReturnStmt>().filter { statement ->
            val origin = statement.location.origin ?: return@filter false
            origin.startOffset <= expressionStart && origin.endOffset >= expressionEnd
        }
        val smallestContainingRange = containingReturns.minOfOrNull { statement ->
            val origin = requireNotNull(statement.location.origin)
            origin.endOffset - origin.startOffset
        }
        if (smallestContainingRange != null) {
            val smallestReturns = containingReturns.filter { statement ->
                val origin = requireNotNull(statement.location.origin)
                origin.endOffset - origin.startOffset == smallestContainingRange
            }
            if (smallestReturns.isNotEmpty()) {
                return smallestReturns.singleOrNull()
            }
        }

        val containingStatements = method.cfg.stmts.filter { statement ->
            val origin = statement.location.origin ?: return@filter false
            origin.startOffset <= expressionStart && origin.endOffset >= expressionEnd
        }
        val smallestStatementRange = containingStatements.minOfOrNull { statement ->
            val origin = requireNotNull(statement.location.origin)
            origin.endOffset - origin.startOffset
        } ?: return if (allowsUniqueOriginlessReturn()) {
            method.cfg.stmts.filterIsInstance<EtsReturnStmt>().singleOrNull()
        } else {
            null
        }
        val anchors = containingStatements.filter { statement ->
            val origin = requireNotNull(statement.location.origin)
            origin.endOffset - origin.startOffset == smallestStatementRange
        }

        val reachableReturns = mutableSetOf<EtsReturnStmt>()
        val visited = mutableSetOf<EtsStmt>()
        val pending = ArrayDeque<EtsStmt>()
        pending.addAll(anchors)
        while (pending.isNotEmpty()) {
            val statement = pending.removeFirst()
            if (!visited.add(statement)) {
                continue
            }
            if (statement is EtsReturnStmt) {
                reachableReturns += statement
                continue
            }

            pending.addAll(method.cfg.successors(statement))
        }

        return reachableReturns.singleOrNull()
    }

    private fun result(
        status: CallsSymbolicStatus,
        startedAt: TimeSource.Monotonic.ValueTimeMark,
        solverReached: Boolean = status == CallsSymbolicStatus.REACHED,
        inputs: List<JsConcreteValue>? = null,
        diagnostic: String? = null,
    ) = CallsSymbolicSearchResult(
        status = status,
        solverReached = solverReached,
        inputs = inputs,
        elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds,
        diagnostic = diagnostic,
    )

    internal fun verifyNativeFrontendOnce(expectedRevision: String) {
        val configuredScript = environment("ETS_FRONTEND_SCRIPT")
        require(configuredScript == null) {
            "ETS_FRONTEND_SCRIPT must be unset so the frozen native frontend runtime is used"
        }
        if (expectedRevision.startsWith(BUNDLED_FRONTEND_PREFIX)) {
            require(environment("ETS_FRONTEND_DIR") == null) {
                "ETS_FRONTEND_DIR must be unset when the bundled native frontend is selected"
            }
            require(expectedRevision == bundledNativeFrontendRevision) {
                "Bundled native frontend revision $expectedRevision does not match running build " +
                    bundledNativeFrontendRevision
            }
            verifiedNativeFrontendIdentity = expectedRevision
            return
        }

        val configuredFrontend = requireNotNull(environment("ETS_FRONTEND_DIR")) {
            "ETS_FRONTEND_DIR is required to verify the frozen native frontend revision"
        }
        val frontendDirectory = Path.of(configuredFrontend).toRealPath()
        val expectedIdentity = "$frontendDirectory@$expectedRevision"
        if (verifiedNativeFrontendIdentity == expectedIdentity) {
            return
        }

        verifyCallsGitCheckout(frontendDirectory, expectedRevision)
        verifiedNativeFrontendIdentity = expectedIdentity
    }

    private fun verifyGitCheckoutOnce(
        checkout: Path,
        expectedRevision: String,
        cache: MutableMap<Path, String>,
    ) {
        if (cache[checkout] == expectedRevision) {
            return
        }

        verifyCallsGitCheckout(checkout, expectedRevision)
        cache[checkout] = expectedRevision
    }

    private sealed interface CallsTargetPreparation {
        data class Eligible(
            val scene: EtsScene,
            val method: EtsMethod,
            val inputBindings: List<EtsInputBinding>,
            val lexicalEnvironment: EtsLexicalEnvironmentBinding?,
            val targetObservation: SourceTargetObservation,
            val exactTargetCandidateCount: Int,
        ) : CallsTargetPreparation

        data class Rejected(
            val status: CallsSymbolicStatus,
            val reasonCode: CallsSymbolicPreflightReasonCode,
            val diagnostic: String,
        ) : CallsTargetPreparation
    }

    private sealed interface SourceTargetObservation {
        fun createObserver(method: EtsMethod): SourceTargetObserver

        data class Entry(val entry: SourceStatementEntry) : SourceTargetObservation {
            override fun createObserver(method: EtsMethod): SourceTargetObserver =
                SourceStatementEntryObserver(entry)
        }

        data class CompletedReturn(val statement: EtsReturnStmt) : SourceTargetObservation {
            override fun createObserver(method: EtsMethod): SourceTargetObserver =
                SourceCompletedReturnObserver(method = method, target = statement)
        }
    }

    private interface SourceTargetObserver : UMachineObserver<TsState> {
        val reachedStates: List<TsState>
        val loweringSize: Int
    }

    private class SourceStatementEntryObserver(
        private val target: SourceStatementEntry,
    ) : SourceTargetObserver {
        override val reachedStates = mutableListOf<TsState>()
        override val loweringSize: Int = target.loweringSize

        override fun onStatePeeked(state: TsState) {
            if (state.currentStatement == target.statement) {
                reachedStates += state.clone()
            }
        }
    }

    private class SourceCompletedReturnObserver(
        private val method: EtsMethod,
        private val target: EtsReturnStmt,
    ) : SourceTargetObserver {
        override val reachedStates = mutableListOf<TsState>()
        override val loweringSize: Int = 1

        override fun onState(state: TsState, forks: Sequence<TsState>) {
            sequenceOf(state).plus(forks)
                .filter { candidate -> candidate.currentStatement == target }
                .filter { candidate -> candidate.callStack.isEmpty() }
                .filter { candidate ->
                    val result = candidate.methodResult
                    result is TsMethodResult.Success.RegularCall && result.method == method
                }
                .mapTo(reachedStates) { candidate -> candidate.clone() }
        }
    }

    private class UnknownCallEventSinkObserver(
        private val sink: ((TsUnknownCallEvent) -> Unit)?,
        private val runtimeLimitationSink: ((TsRuntimeFeatureLimitationEvent) -> Unit)?,
    ) : TsInterpreterObserver {
        val runtimeLimitations = linkedSetOf<String>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            sink?.invoke(event)
        }

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            runtimeLimitations += event.reason.name
            runtimeLimitationSink?.invoke(event)
        }
    }

    private data class MachineResult(
        val states: List<TsState>,
        val stopReason: TsAnalysisStopReason,
    )

    private companion object {
        const val BUNDLED_FRONTEND_PREFIX: String = "bundled:"

        val SUPPORTED_BUILTIN_CAPTURES: Set<String> = setOf(
            "Array",
            "Boolean",
            "Date",
            "Error",
            "Infinity",
            "Map",
            "Math",
            "NaN",
            "Number",
            "Object",
            "RangeError",
            "Set",
            "String",
            "TypeError",
            "isNaN",
        )
    }
}

private fun CallsSymbolicSearchRequest.toPreflightRequest() = CallsSymbolicPreflightRequest(
    sourceRoot = sourceRoot,
    project = project,
    function = function,
    target = target,
    expectedNativeFrontendRevision = expectedNativeFrontendRevision,
)

private fun CallsSymbolicStatus.toPreflightStatus(): CallsSymbolicPreflightStatus = when (this) {
    CallsSymbolicStatus.UNSUPPORTED -> CallsSymbolicPreflightStatus.UNSUPPORTED
    CallsSymbolicStatus.UNMAPPED -> CallsSymbolicPreflightStatus.UNMAPPED
    CallsSymbolicStatus.AMBIGUOUS -> CallsSymbolicPreflightStatus.AMBIGUOUS
    CallsSymbolicStatus.TOOL_ERROR -> CallsSymbolicPreflightStatus.TOOL_ERROR
    else -> error("Symbolic execution status $this is not a preflight result")
}

internal data class SourceStatementEntry(
    val statement: EtsStmt,
    val loweringSize: Int,
)

internal fun sourceStatementEntry(method: EtsMethod, target: CallsSourceTarget): SourceStatementEntry? {
    val loweringRegion = method.cfg.stmts.filter { statement ->
        val origin = statement.location.origin ?: return@filter false
        origin.startOffset >= target.startOffset && origin.endOffset <= target.endOffset
    }
    val loweringRegionSet = loweringRegion.toSet()
    if (loweringRegionSet.isEmpty()) {
        return null
    }

    val boundaryStatements = loweringRegion.filter { statement ->
        val predecessors = method.cfg.predecessors(statement)
        predecessors.isEmpty() || predecessors.any { predecessor -> predecessor !in loweringRegionSet }
    }
    val entry = boundaryStatements.singleOrNull() ?: return null
    val outsidePredecessors = method.cfg.predecessors(entry).filter { predecessor ->
        predecessor !in loweringRegionSet
    }
    val outsideOriginsAreBeforeTarget = outsidePredecessors.all { predecessor ->
        val origin = predecessor.location.origin ?: return@all false
        origin.endOffset <= target.startOffset
    }
    if (!outsideOriginsAreBeforeTarget) {
        return null
    }

    val reachable = mutableSetOf<EtsStmt>()
    val pending = ArrayDeque<EtsStmt>()
    pending += entry
    while (pending.isNotEmpty()) {
        val statement = pending.removeFirst()
        if (!reachable.add(statement)) {
            continue
        }

        method.cfg.successors(statement)
            .filterTo(pending) { successor -> successor in loweringRegionSet }
    }
    if (reachable.size != loweringRegionSet.size) {
        return null
    }

    return SourceStatementEntry(
        statement = entry,
        loweringSize = loweringRegionSet.size,
    )
}

internal fun verifyCallsGitCheckout(checkout: Path, expectedRevision: String) {
    val actualRevision = runCallsGit(checkout, "rev-parse", "HEAD").trim()
    require(actualRevision == expectedRevision) {
        "Checkout $checkout is at $actualRevision, expected frozen revision $expectedRevision"
    }
    runCallsGit(checkout, "diff", "--quiet", "HEAD", "--")
}

private fun runCallsGit(checkout: Path, vararg arguments: String): String {
    val process = ProcessBuilder(listOf("git", "-C", checkout.toString()) + arguments)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
    val exitCode = process.waitFor()
    require(exitCode == 0) {
        val command = arguments.joinToString(separator = " ")
        "Git $command failed for $checkout with exit $exitCode: ${output.trim()}"
    }

    return output
}

private fun EtsMappingStatus.toSymbolicStatus(): CallsSymbolicStatus = when (this) {
    EtsMappingStatus.EXACT -> error("Exact mapping has no failure status")
    EtsMappingStatus.AMBIGUOUS -> CallsSymbolicStatus.AMBIGUOUS
    EtsMappingStatus.UNMAPPED -> CallsSymbolicStatus.UNMAPPED
    EtsMappingStatus.UNSUPPORTED -> CallsSymbolicStatus.UNSUPPORTED
}

private fun EtsMappingStatus.toPreflightReasonCode(): CallsSymbolicPreflightReasonCode = when (this) {
    EtsMappingStatus.EXACT -> error("Exact mapping has no exclusion reason")
    EtsMappingStatus.AMBIGUOUS -> CallsSymbolicPreflightReasonCode.ENTRY_MAPPING_AMBIGUOUS
    EtsMappingStatus.UNMAPPED -> CallsSymbolicPreflightReasonCode.ENTRY_MAPPING_UNMAPPED
    EtsMappingStatus.UNSUPPORTED -> CallsSymbolicPreflightReasonCode.ENTRY_MAPPING_UNSUPPORTED
}
