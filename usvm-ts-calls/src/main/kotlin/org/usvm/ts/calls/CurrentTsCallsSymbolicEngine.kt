package org.usvm.ts.calls

import mu.KotlinLogging
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLexicalEnvType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
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
import org.usvm.machine.interpreter.TsInitialStateSolverUnknownException
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceCapture
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsInputBinding
import org.usvm.ts.pbt.mapping.EtsLexicalEnvironmentBinding
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.contains
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

internal data class CallsSymbolicPreflightRequest(
    val sourceRoot: Path,
    val project: CallsProjectCase,
    val function: CallsFunctionCase,
    val target: CallsSourceTarget,
    val expectedNativeFrontendRevision: String,
)

internal data class CallsFunctionPreflightRequest(
    val sourceRoot: Path,
    val project: CallsProjectCase,
    val function: CallsFunctionCase,
    val expectedNativeFrontendRevision: String,
    val verifySourceCheckout: Boolean = true,
)

internal sealed interface CallsFunctionPreparation {
    data class Eligible(
        val scene: EtsScene,
        val method: EtsMethod,
        val inputBindings: List<EtsInputBinding>,
        val lexicalEnvironment: EtsLexicalEnvironmentBinding?,
        val captures: List<TypeScriptSourceCapture>,
        val sourcePaths: Map<EtsFileSignature, String>,
    ) : CallsFunctionPreparation

    data class Rejected(
        val status: CallsSymbolicStatus,
        val reasonCode: CallsSymbolicPreflightReasonCode,
        val diagnostic: String,
    ) : CallsFunctionPreparation
}

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
    TOP_LEVEL_INITIALIZATION_UNSUPPORTED,
    ENTRY_MAPPING_UNSUPPORTED,
    ENTRY_MAPPING_UNMAPPED,
    ENTRY_MAPPING_AMBIGUOUS,
    LEXICAL_CAPTURE_UNSUPPORTED,
    DESTRUCTURING_UNSUPPORTED,
    SPREAD_UNSUPPORTED,
    REGEX_LITERAL_UNSUPPORTED,
    RAW_ENTITY_UNSUPPORTED,
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
    private val preparedSources = mutableMapOf<Pair<Path, Path>, CallsSourceProject>()
    private var preparedTarget: Pair<CallsSymbolicPreflightRequest, CallsTargetPreparation>? = null

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
            logger.warn(error) { "Calls symbolic search did not produce a result" }
            result(
                status = if (error is TsInitialStateSolverUnknownException) {
                    CallsSymbolicStatus.UNKNOWN
                } else {
                    CallsSymbolicStatus.TOOL_ERROR
                },
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
            solverType = request.solverType,
            stopOnCoverage = CALLS_STOP_ON_COVERAGE,
            stopOnTargetsReached = false,
            throwExceptionOnStepFailure = true,
        )
        val inputCapacity = request.function.inputs.maxOfOrNull { input -> input.domain.callsInputCapacity() } ?: 0
        val tsOptions = TsOptions(
            maxArraySize = maxOf(TsOptions().maxArraySize, inputCapacity),
            unknownCallModelSelection = modelSelection,
            unknownCallFallback = request.profile.fallback,
            eraseTypeAssertions = true,
        )
        val symbolicInputs = CallsSymbolicInputs(
            inputs = request.function.inputs,
            bindings = prepared.inputBindings,
            lexicalEnvironment = prepared.lexicalEnvironment,
            captures = prepared.captures,
            sourceScene = scene,
        )
        val interpreterObserver = UnknownCallEventSinkObserver(
            sink = request.unknownCallEventSink,
            runtimeLimitationSink = request.runtimeLimitationEventSink,
        )
        val analysis = TsMachine(
            scene = symbolicInputs.scene,
            options = machineOptions,
            tsOptions = tsOptions,
            machineObserver = targetObserver,
            observer = interpreterObserver,
        ).use { machine ->
            val outcome = machine.analyzeWithMetadata(
                methods = listOf(method),
                configureInitialState = { _, state -> symbolicInputs.initialize(state) },
            )
            MachineResult(
                states = targetObserver.reachedStates,
                stopReason = outcome.stopReason,
                unsupportedPaths = outcome.unsupportedPaths,
                unsupportedCall = outcome.unsupportedCall,
            )
        }
        val states = analysis.states
        if (states.isEmpty()) {
            val status = when (analysis.stopReason) {
                TsAnalysisStopReason.EXHAUSTED -> {
                    when {
                        interpreterObserver.runtimeLimitations.isNotEmpty() -> {
                            CallsSymbolicStatus.RUNTIME_LIMITATION
                        }

                        analysis.unsupportedPaths.isNotEmpty() || analysis.unsupportedCall -> {
                            CallsSymbolicStatus.UNSUPPORTED
                        }

                        else -> {
                            CallsSymbolicStatus.UNREACHED
                        }
                    }
                }
                // The machine options above disable every stop condition except the per-target timeout.
                TsAnalysisStopReason.STOPPED -> {
                    CallsSymbolicStatus.TIMEOUT
                }
                TsAnalysisStopReason.FORK_LIMIT -> {
                    CallsSymbolicStatus.RUNTIME_LIMITATION
                }
            }

            return result(
                status = status,
                startedAt = startedAt,
                diagnostic = buildList {
                    if (analysis.stopReason == TsAnalysisStopReason.FORK_LIMIT) add("FORK_LIMIT")
                    addAll(analysis.unsupportedPaths)
                    if (interpreterObserver.runtimeLimitations.isNotEmpty()) {
                        add(
                            interpreterObserver.runtimeLimitations.joinToString(
                                prefix = "Runtime feature limitations: ",
                            ),
                        )
                    }
                }.joinToString().ifEmpty { null },
            )
        }

        val inputs = states.asSequence()
            .flatMap(symbolicInputs::resolve)
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
        val cached = preparedTarget
        val cachedRevisionMatches = verifiedProjects[request.sourceRoot] == request.project.revision
        val preparation = runCatching {
            if (cached?.first == request && cachedRevisionMatches) {
                verifyNativeFrontend(expectedRevision = request.expectedNativeFrontendRevision)
                cached.second
            } else {
                prepare(request)
            }
        }.getOrElse { error ->
            CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.TOOL_ERROR,
                reasonCode = CallsSymbolicPreflightReasonCode.FRONTEND_OR_PREFLIGHT_ERROR,
                diagnostic = error.message ?: error::class.java.name,
            )
        }
        if (preparation !is CallsTargetPreparation.Rejected || preparation.status != CallsSymbolicStatus.TOOL_ERROR) {
            preparedTarget = request to preparation
        }

        return preparation
    }

    internal fun prepareFunction(request: CallsFunctionPreflightRequest): CallsFunctionPreparation =
        runCatching { prepareFunctionSupported(request) }.getOrElse { error ->
            CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.TOOL_ERROR,
                reasonCode = CallsSymbolicPreflightReasonCode.FRONTEND_OR_PREFLIGHT_ERROR,
                diagnostic = error.message ?: error::class.java.name,
            )
        }

    @Suppress("LongMethod")
    private fun prepareFunctionSupported(request: CallsFunctionPreflightRequest): CallsFunctionPreparation {
        val inputDomainDiagnostic = callsSymbolicInputPreflight(request.function.inputs)
        if (inputDomainDiagnostic != null) {
            return CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED,
                diagnostic = inputDomainDiagnostic,
            )
        }

        if (request.verifySourceCheckout) {
            verifyGitCheckoutOnce(
                checkout = request.sourceRoot,
                expectedRevision = request.project.revision,
                cache = verifiedProjects,
            )
        }

        verifyNativeFrontend(expectedRevision = request.expectedNativeFrontendRevision)

        val source = request.sourceRoot.resolve(request.function.sourceFile).normalize()
        require(source.startsWith(request.sourceRoot)) { "Function source escapes its frozen source root" }

        val project = if (request.verifySourceCheckout) {
            loadCallsSourceProject(sourceRoot = request.sourceRoot, source = source)
        } else {
            preparedSources.getOrPut(request.sourceRoot to source) {
                loadCallsSourceProject(sourceRoot = request.sourceRoot, source = source)
            }
        }
        val loaded = when (project) {
            is CallsSourceProject.Loaded -> project
            is CallsSourceProject.Unsupported -> return CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = project.issue.reasonCode,
                diagnostic = project.issue.diagnostic,
            )
        }
        val scene = loaded.scene
        val frontendEntryPoint = request.function.entryPoint.copy(module = loaded.entryModule)
        val propertyManifest = PropertyManifest(
            propertyId = "calls.mapping",
            inputs = request.function.inputs,
            predicate = frontendEntryPoint,
        )
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = listOf(request.sourceRoot)).map(propertyManifest)
        if (mapping.predicate.status != EtsMappingStatus.EXACT) {
            return CallsFunctionPreparation.Rejected(
                status = mapping.predicate.status.toSymbolicStatus(),
                reasonCode = mapping.predicate.status.toPreflightReasonCode(),
                diagnostic = mapping.predicate.diagnostics.joinToString { diagnostic -> diagnostic.message },
            )
        }

        val mappedTarget = mapping.predicate.targets.single()
        val method = mappedTarget.method
        val inputTypeLimitation = callsInputTypePreflight(
            inputs = request.function.inputs,
            bindings = mappedTarget.bindings.inputs,
            method = method,
            scene = scene,
        )
        if (inputTypeLimitation != null) {
            return CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.INPUT_DOMAIN_UNSUPPORTED,
                diagnostic = inputTypeLimitation,
            )
        }

        val lexicalEnvironment = mappedTarget.bindings.lexicalEnvironment
        if (lexicalEnvironment != null && lexicalEnvironment.parameter.type !is EtsLexicalEnvType) {
            return CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.LEXICAL_CAPTURE_UNSUPPORTED,
                diagnostic = "Mapped lexical environment does not have a lexical-environment type",
            )
        }

        val sourceCaptures = if (lexicalEnvironment == null) {
            emptyList()
        } else {
            val environment = lexicalEnvironment.parameter.type as EtsLexicalEnvType
            TypeScriptSourceInspector.captures(
                source = source,
                exportName = request.function.entryPoint.exportName,
                names = environment.closures.map { closure -> closure.name },
            )
        }
        val captureLimitation = lexicalEnvironment?.let { binding ->
            callsCapturePreflight(scene, binding, sourceCaptures)
        }
        if (captureLimitation != null) {
            return CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = CallsSymbolicPreflightReasonCode.LEXICAL_CAPTURE_UNSUPPORTED,
                diagnostic = captureLimitation,
            )
        }

        val readinessIssue = callsIrReadinessIssue(
            method = method,
            graph = TsGraph(scene),
            sourceByFile = loaded.sourceByFile,
        )
        if (readinessIssue != null) {
            return CallsFunctionPreparation.Rejected(
                status = CallsSymbolicStatus.UNSUPPORTED,
                reasonCode = readinessIssue.reasonCode,
                diagnostic = readinessIssue.diagnostic,
            )
        }

        return CallsFunctionPreparation.Eligible(
            scene = scene,
            method = method,
            inputBindings = mappedTarget.bindings.inputs,
            lexicalEnvironment = lexicalEnvironment,
            captures = sourceCaptures,
            sourcePaths = loaded.sourcePaths,
        )
    }

    private fun prepare(request: CallsSymbolicPreflightRequest): CallsTargetPreparation {
        val function = prepareFunction(
            CallsFunctionPreflightRequest(
                sourceRoot = request.sourceRoot,
                project = request.project,
                function = request.function,
                expectedNativeFrontendRevision = request.expectedNativeFrontendRevision,
            ),
        )
        if (function is CallsFunctionPreparation.Rejected) {
            return CallsTargetPreparation.Rejected(function.status, function.reasonCode, function.diagnostic)
        }
        function as CallsFunctionPreparation.Eligible
        val method = function.method
        val source = request.sourceRoot.resolve(request.function.sourceFile).normalize()
        val sourceText = java.nio.file.Files.readString(source)
        val targetCoordinateDiagnostic = callsTargetCoordinateDiagnostic(source = sourceText, target = request.target)
        if (targetCoordinateDiagnostic != null) {
            return CallsTargetPreparation.Rejected(
                status = CallsSymbolicStatus.UNMAPPED,
                reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNMAPPED,
                diagnostic = targetCoordinateDiagnostic,
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
                val sourceTarget = TypeScriptSourceInspector.instrumentReplay(
                    source = source,
                    mode = request.target.mode.name,
                    startOffset = request.target.startOffset,
                    endOffset = request.target.endOffset,
                    expressionStartOffset = request.target.returnExpressionStartOffset,
                    expressionEndOffset = request.target.returnExpressionEndOffset,
                    marker = "__usvm_completed_return_preflight",
                )
                val sourceTargetReason = sourceTarget.reason
                if (sourceTargetReason != null) {
                    return CallsTargetPreparation.Rejected(
                        status = CallsSymbolicStatus.UNSUPPORTED,
                        reasonCode = CallsSymbolicPreflightReasonCode.TARGET_ORIGIN_UNSUPPORTED,
                        diagnostic = sourceTargetReason,
                    )
                }

                val returnStatement = completedReturnCandidate(
                    method = method,
                    target = request.target,
                    exactTargetCandidates = exactTargetCandidates,
                    allowsUniqueOriginlessReturn = {
                        TypeScriptSourceInspector.isExportedExpressionArrowBody(
                            source = source,
                            exportName = request.function.entryPoint.exportName,
                            startOffset = request.target.startOffset,
                            endOffset = request.target.endOffset,
                        )
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
            scene = function.scene,
            method = method,
            inputBindings = function.inputBindings,
            lexicalEnvironment = function.lexicalEnvironment,
            captures = function.captures,
            targetObservation = targetObservation,
            exactTargetCandidateCount = exactTargetCandidates.size,
        )
    }

    private fun exactTargetCandidates(method: EtsMethod, target: CallsSourceTarget): List<EtsStmt> =
        method.cfg.stmts.filter { statement ->
            val origin = statement.location.origin
            origin != null && origin.startOffset == target.startOffset &&
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
            val origin = statement.location.origin
            origin != null && origin.startOffset <= expressionStart && origin.endOffset >= expressionEnd
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
            val origin = statement.location.origin
            origin != null && origin.startOffset <= expressionStart && origin.endOffset >= expressionEnd
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

    internal fun verifyNativeFrontend(expectedRevision: String) {
        val configuredScript = environment("ETS_FRONTEND_SCRIPT")
        require(configuredScript == null) {
            "ETS_FRONTEND_SCRIPT must be unset so the frozen native frontend runtime is used"
        }
        if (expectedRevision.startsWith(BUNDLED_FRONTEND_PREFIX)) {
            require(environment("ETS_FRONTEND_DIR") == null) {
                "ETS_FRONTEND_DIR must be unset when the bundled native frontend is selected"
            }
            require(System.getProperty(ETS_FRONTEND_DIR_PROPERTY) == null) {
                "$ETS_FRONTEND_DIR_PROPERTY must be unset when the bundled native frontend is selected"
            }
            require(expectedRevision == bundledNativeFrontendRevision) {
                "Bundled native frontend revision $expectedRevision does not match running build " +
                    bundledNativeFrontendRevision
            }
            return
        }

        val configuredFrontend = requireNotNull(environment("ETS_FRONTEND_DIR")) {
            "ETS_FRONTEND_DIR is required to verify the frozen native frontend revision"
        }
        val frontendDirectory = Path.of(configuredFrontend).toRealPath()
        verifyCallsExternalFrontendRuntime(frontendDirectory, expectedRevision)
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
            val captures: List<TypeScriptSourceCapture>,
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
        val unsupportedPaths: List<String>,
        val unsupportedCall: Boolean,
    )

    private companion object {
        const val BUNDLED_FRONTEND_PREFIX: String = "bundled:"
        const val ETS_FRONTEND_DIR_PROPERTY: String = "ets.frontend.dir"
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
        val origin = statement.location.origin
        origin != null && origin.startOffset >= target.startOffset && origin.endOffset <= target.endOffset
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
        val origin = predecessor.location.origin
        origin != null && origin.endOffset <= target.startOffset
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

internal fun verifyCallsExternalFrontendRuntime(expectedIdentity: String) {
    val configuredFrontend = requireNotNull(System.getenv("ETS_FRONTEND_DIR")) {
        "ETS_FRONTEND_DIR is required to verify the frozen native frontend runtime"
    }
    val frontendDirectory = Path.of(configuredFrontend).toRealPath()
    verifyCallsExternalFrontendRuntime(frontendDirectory, expectedIdentity)
}

internal fun verifyCallsExternalFrontendRuntime(frontendDirectory: Path, expectedIdentity: String) {
    val match = requireNotNull(EXTERNAL_FRONTEND_IDENTITY.matchEntire(expectedIdentity)) {
        "External native frontend identity must be <40-character Git SHA>@sha256:<64-character runtime digest>"
    }
    val expectedRevision = match.groupValues[1]
    val expectedDigest = match.groupValues[2]

    verifyCallsGitCheckout(frontendDirectory, expectedRevision)
    val actualDigest = callsExternalFrontendRuntimeDigest(frontendDirectory)
    require(actualDigest == expectedDigest) {
        "External native frontend runtime digest differs from frozen identity for $frontendDirectory"
    }
}

internal fun callsExternalFrontendIdentity(frontendDirectory: Path): String {
    val realDirectory = frontendDirectory.toRealPath()
    val revision = runCallsGit(realDirectory, "rev-parse", "HEAD").trim()
    verifyCallsGitCheckout(realDirectory, revision)
    return "$revision@sha256:${callsExternalFrontendRuntimeDigest(realDirectory)}"
}

private fun callsExternalFrontendRuntimeDigest(frontendDirectory: Path): String {
    val dist = frontendDirectory.resolve("dist")
    require(Files.isDirectory(dist)) { "External native frontend dist directory is missing: $dist" }

    val files = Files.walk(dist).use { paths ->
        paths.iterator().asSequence()
            .filter(::isCallsExternalFrontendRuntimeFile)
            .sortedBy { path -> dist.relativize(path).toString().replace('\\', '/') }
            .toList()
    }
    require(dist.resolve("index.js") in files) { "External native frontend dist is missing index.js: $dist" }

    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    for (file in files) {
        updateCallsExternalFrontendRuntimeFileDigest(
            digest = digest,
            dist = dist,
            file = file,
            buffer = buffer,
        )
    }

    return digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK)
    }
}

private fun updateCallsExternalFrontendRuntimeFileDigest(
    digest: MessageDigest,
    dist: Path,
    file: Path,
    buffer: ByteArray,
) {
    val relativePath = dist.relativize(file).toString().replace('\\', '/')
    val nameBytes = relativePath.toByteArray(Charsets.UTF_8)
    val fileSize = Files.size(file)
    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(nameBytes.size).array())
    digest.update(nameBytes)
    digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(fileSize).array())

    var bytesRead = 0L
    Files.newInputStream(file).use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            bytesRead += count
        }
    }
    require(bytesRead == fileSize) { "External native frontend dist changed while hashing: $file" }
}

private fun isCallsExternalFrontendRuntimeFile(path: Path): Boolean {
    require(!Files.isSymbolicLink(path)) { "External native frontend dist contains a symlink: $path" }
    val isDirectory = Files.isDirectory(path)
    if (!isDirectory) {
        require(Files.isRegularFile(path)) { "External native frontend dist contains a non-file: $path" }
    }

    return !isDirectory
}

private const val UNSIGNED_BYTE_MASK = 0xff
private val EXTERNAL_FRONTEND_IDENTITY = Regex("([0-9a-f]{40})@sha256:([0-9a-f]{64})")

internal fun runCallsGit(checkout: Path, vararg arguments: String): String {
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
