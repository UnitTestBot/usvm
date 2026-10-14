package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.CoverageZone
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.UMachineObserver
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Serializable
internal data class CallsClassStartCase(
    val classId: String,
    val identity: String,
    val projectRevision: String,
    val sourceRoot: String,
    val sourceFile: String,
    val className: String,
    val inputSha256: Map<String, String>,
    val frontendConfig: String? = null,
    val acquisitionSha256: String? = null,
    val acquisitionReceipt: String? = null,
    val acquisitionComplete: Boolean = false,
) {
    init {
        require(classId.matches(Regex("[A-Za-z0-9_-]+")))
        require(identity.isNotBlank() && className.isNotBlank())
        require(projectRevision.matches(Regex("[0-9a-f]{40}")))
        require(sourceFile in inputSha256) { "The full target source must be frozen" }
        inputSha256.forEach { (name, digest) ->
            val path = Path.of(name)
            require(!path.isAbsolute && path.normalize() == path && !path.startsWith(".."))
            require(digest.matches(Regex("[0-9a-f]{64}")))
        }
        require(acquisitionSha256 == null || acquisitionSha256.matches(Regex("[0-9a-f]{64}")))
        val hasFrozenReceipt = acquisitionReceipt != null && acquisitionSha256 != null
        require(!acquisitionComplete || hasFrozenReceipt) { "Complete acquisition needs a frozen receipt" }
    }

    fun verifyInputs(): Path {
        val root = Path.of(sourceRoot).toRealPath()
        inputSha256.forEach { (name, expected) ->
            val file = root.resolve(name).toRealPath()
            require(file.startsWith(root)) { "Frozen input escapes its root: $name" }
            require(callsArtifactSha256(Files.readAllBytes(file)) == expected) { "Frozen input changed: $name" }
        }
        return root
    }
}

@Serializable
internal data class CallsClassStartManifest(
    val schemaVersion: Int = 1,
    val toolRevision: String,
    val nativeFrontendRevision: String,
    val ledgerSha256: String,
    val expectedClassCount: Int = 200,
    val budgetMillis: Long = 2_000,
    val solverLimitMillis: Long = 250,
    val collectedStatesLimit: Int = 10,
    val seed: Long = 17,
    val profile: CallsExperimentProfile = CallsExperimentProfile.FROZEN_STOP,
    val classes: List<CallsClassStartCase>,
) {
    init {
        require(schemaVersion == 1 && classes.size == expectedClassCount && expectedClassCount > 0)
        require(classes.map { it.classId }.distinct().size == classes.size)
        require(classes.map { it.identity }.distinct().size == classes.size)
        require(ledgerSha256.matches(Regex("[0-9a-f]{64}")))
        require(budgetMillis > 0 && solverLimitMillis in 1..budgetMillis && collectedStatesLimit > 0)
    }
}

@Serializable
internal data class CallsClassStartResult(
    val classId: String,
    val identity: String,
    val manifestSha256: String,
    val stage: String,
    val started: Boolean,
    val qualifyingStarted: Boolean,
    val initialStates: Int,
    val executedSteps: Long,
    val targetClassSteps: Long,
    val originalTargetClassSteps: Long,
    val syntheticOrUnmappedTargetClassSteps: Long,
    val stageElapsedMillis: Map<String, Long>,
    val targetStatementEntered: Boolean = false,
    val originalTargetStatementsEntered: Long = 0,
    val targetSuccessfulTransitions: Long = 0,
    val startupStatus: String = "NOT_STARTED",
    val acquisitionComplete: Boolean = false,
    val acquisitionSha256: String? = null,
    val targetSignature: String? = null,
    val methodSignatures: List<String> = emptyList(),
    val firstOriginalStep: CallsUnknownCallSite? = null,
    val stopReason: String? = null,
    val timedOut: Boolean = false,
    val unsupportedCall: Boolean = false,
    val runtimeLimited: Boolean = false,
    val engineFailed: Boolean = false,
    val unsupportedPaths: List<String> = emptyList(),
    val unknownCalls: List<CallsClassUnknownCallEvent> = emptyList(),
    val diagnostic: String? = null,
)

/** A source return must be explicit; a synthetic default constructor return cannot prove startup. */
internal fun isOriginalClassStartupStep(
    stmt: EtsStmt,
    target: EtsClassSignature,
    sourceFile: String,
    sourceText: String,
): Boolean {
    if (stmt.location.method.signature.enclosingClass != target || stmt is EtsNopStmt) return false
    val origin = stmt.location.origin ?: return false
    val normalizedFile = origin.fileName.replace('\\', '/')
    val matchesFile = normalizedFile == sourceFile || normalizedFile.endsWith("/$sourceFile")
    val validRange = origin.startOffset >= 0 && origin.endOffset > origin.startOffset &&
        origin.endOffset <= sourceText.length
    if (!matchesFile || !validRange) return false
    val declarationOnly = origin.nodeKind in setOf(
        "ClassDeclaration", "InterfaceDeclaration", "SourceFile", "Constructor", "MethodDeclaration", "Parameter",
    )
    if (declarationOnly) return false
    if (stmt is EtsReturnStmt && origin.nodeKind != "ReturnStatement") return false
    if (origin.nodeKind == "PropertyDeclaration") {
        val declaration = sourceText.substring(origin.startOffset, origin.endOffset)
        if (!hasStartupFieldInitializer(declaration)) return false
    }
    return true
}

/** Recognizes an outer assignment, excluding comments, quoted type literals and function-type arrows. */
@Suppress("NestedBlockDepth")
internal fun hasStartupFieldInitializer(declaration: String): Boolean {
    var quote: Char? = null
    var escaped = false
    var depth = 0
    var blockComment = false
    var lineComment = false
    var index = 0
    while (index < declaration.length) {
        val char = declaration[index]
        val next = declaration.getOrNull(index + 1)
        if (blockComment) {
            if (char == '*' && next == '/') {
                blockComment = false
                index++
            }
        } else if (lineComment) {
            if (char == '\n' || char == '\r') lineComment = false
        } else if (quote != null) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == quote -> quote = null
            }
        } else if (char == '/' && next == '*') {
            blockComment = true
            index++
        } else if (char == '/' && next == '/') {
            lineComment = true
            index++
        } else {
            when (char) {
                '\'', '"', '`' -> { quote = char }
                '(', '[', '{' -> { depth++ }
                ')', ']', '}' -> { depth-- }
                '=' -> {
                    val previous = declaration.getOrNull(index - 1)
                    val assignment = depth == 0 && next != '>' && next != '=' && previous !in listOf('=', '!', '<')
                    if (assignment) return true
                }
            }
        }
        index++
    }
    return false
}

internal class ClassStartObserver(
    private val target: EtsClassSignature,
    private val sourceFile: String,
    private val sourceText: String,
    private val onFirstOriginalStep: () -> Unit,
    private val unknownCallObserver: (TsUnknownCallEvent) -> Unit = {},
) : UMachineObserver<TsState>, TsInterpreterObserver {
    private val initial = java.util.Collections.newSetFromMap(IdentityHashMap<TsState, Boolean>())
    private val seenInitial = java.util.Collections.newSetFromMap(IdentityHashMap<TsState, Boolean>())
    private val pending = IdentityHashMap<TsState, EtsStmt>()
    private val entered = java.util.Collections.newSetFromMap(IdentityHashMap<TsState, Boolean>())
    var executedSteps: Long = 0
        private set
    var targetSteps: Long = 0
        private set
    var originalSteps: Long = 0
        private set
    var originalStepCallbacks: Long = 0
        private set
    var originalStatementsEntered: Long = 0
        private set
    var firstOriginalStep: CallsUnknownCallSite? = null
        private set
    val initialStates: Int get() = seenInitial.size

    fun registerInitialState(state: TsState) { initial += state }

    override fun onStatePeeked(state: TsState) {
        if (state in initial && state.models.isNotEmpty()) seenInitial += state
        entered -= state
        pending[state] = state.lastStmt
    }

    /** Statement callbacks run inside interpreter visits, before expression resolution or fallback. */
    private fun onStatementEntered(stmt: EtsStmt, scope: TsStepScope) {
        val state = scope.calcOnState { this }
        val original =
            isOriginalClassStartupStep(stmt = stmt, target = target, sourceFile = sourceFile, sourceText = sourceText)
        if (!original || state.pathConstraints.isFalse || state in entered) return
        entered += state
        originalStatementsEntered++
        if (firstOriginalStep == null) {
            firstOriginalStep = stmt.toCallsUnknownCallSite()
            onFirstOriginalStep()
        }
    }

    override fun onAssignStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsAssignStmt,
        scope: TsStepScope,
    ) {
        onStatementEntered(stmt = stmt, scope = scope)
    }

    override fun onCallWithUnresolvedArguments(
        simpleValueResolver: TsSimpleValueResolver,
        expr: EtsCallExpr,
        scope: TsStepScope,
    ) {
        onStatementEntered(stmt = scope.calcOnState { lastStmt }, scope = scope)
    }

    override fun onCallStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsCallStmt, scope: TsStepScope) {
        onStatementEntered(stmt = stmt, scope = scope)
    }

    override fun onIfStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsIfStmt, scope: TsStepScope) {
        onStatementEntered(stmt = stmt, scope = scope)
    }

    override fun onReturnStatement(
        simpleValueResolver: TsSimpleValueResolver,
        stmt: EtsReturnStmt,
        scope: TsStepScope,
    ) {
        onStatementEntered(stmt = stmt, scope = scope)
    }

    override fun onThrowStatement(simpleValueResolver: TsSimpleValueResolver, stmt: EtsThrowStmt, scope: TsStepScope) {
        onStatementEntered(stmt = stmt, scope = scope)
    }

    override fun onUnknownCall(event: TsUnknownCallEvent) {
        unknownCallObserver(event)
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        executedSteps++
        val stmt = pending.remove(parent) ?: return
        val statementEntered = entered.remove(parent)
        if (stmt.location.method.signature.enclosingClass != target) return
        targetSteps++
        val parentSucceeded = !parent.pathConstraints.isFalse && parent.unsupportedReason == null
        val forkSucceeded = forks.any { !it.pathConstraints.isFalse && it.unsupportedReason == null }
        val feasibleTransition = parentSucceeded || forkSucceeded
        val original =
            isOriginalClassStartupStep(stmt = stmt, target = target, sourceFile = sourceFile, sourceText = sourceText)
        if (original) originalStepCallbacks++
        if (!feasibleTransition || !original || !statementEntered) return
        originalSteps++
    }
}

/** Full raw source/IR/machine smoke, independent of observation domains and native coverage admission. */
@Suppress("LongMethod")
internal fun runClassStartSmoke(args: List<String>) {
    require(args.size == CLASS_START_ARGUMENT_COUNT) {
        "class-start-smoke requires <manifest.json> <class-id> <output-directory>"
    }
    val manifestPath = Path.of(args[0]).toRealPath()
    val bytes = Files.readAllBytes(manifestPath)
    val manifest = CallsExperimentJson.json.decodeFromString<CallsClassStartManifest>(bytes.toString(Charsets.UTF_8))
    require(manifest.toolRevision == CallsBuildIdentity.toolRevision) { "Smoke runtime revision changed" }
    require(manifest.nativeFrontendRevision == CallsBuildIdentity.nativeFrontendRevision) { "Smoke frontend changed" }
    require(System.getenv("ETS_FRONTEND_SCRIPT") == null) { "Use the pinned bundled frontend" }
    val case = manifest.classes.single { it.classId == args[1] }
    val output = Path.of(args[2]).toAbsolutePath().normalize()
    Files.createDirectories(output)
    require(Files.list(output).use { it.findAny().isEmpty }) { "Smoke child output must be fresh" }
    val fingerprint = callsArtifactSha256(bytes)
    val elapsed = linkedMapOf<String, Long>()
    val startedAt = TimeSource.Monotonic.markNow()
    var stage = "INPUT_VERIFICATION"
    var targetSignature: EtsClassSignature? = null
    var methods = emptyList<EtsMethod>()
    var observer: ClassStartObserver? = null
    val unknownCalls = mutableListOf<CallsClassUnknownCallEvent>()
    var outcome: org.usvm.machine.TsMachineAnalysisResult? = null
    var lastCheckpointMillis = 0L
    fun checkpoint(next: String) {
        val now = startedAt.elapsedNow().inWholeMilliseconds
        elapsed[stage] = now - lastCheckpointMillis
        lastCheckpointMillis = now
        stage = next
        writeCallsArtifactAtomically(
            output.resolve("stage.json"),
            encodeCallsArtifact(
                mapOf(
                    "classId" to case.classId,
                    "identity" to case.identity,
                    "stage" to stage,
                    "manifestSha256" to fingerprint,
                    "elapsedMillis" to startedAt.elapsedNow().inWholeMilliseconds.toString(),
                )
            )
        )
    }
    val failure = runCatching {
        case.verifyStartupAcquisition(manifestDirectory = manifestPath.parent)
        val root = case.verifyInputs()
        val scope = output.resolve("source")
        case.inputSha256.keys.forEach { relative ->
            val target = scope.resolve(relative)
            Files.createDirectories(target.parent)
            Files.copy(root.resolve(relative), target)
        }
        case.frontendConfig?.let { Files.writeString(scope.resolve("tsconfig.json"), it) }
        checkpoint(next = "FRONTEND")
        val ir = preparedClassStartupIr(case = case, scope = scope, output = output)
        checkpoint(next = "SCENE")
        val scene = loadEtsProjectFromIR(projectFilesPath = ir, sdkFilesPath = null)
        checkpoint(next = "TARGET")
        val targets = scene.projectClasses.filter { owner ->
            val file = owner.signature.file.fileName.replace('\\', '/')
            owner.name == case.className && (file == case.sourceFile || file.endsWith("/${case.sourceFile}"))
        }
        require(targets.size == 1) { "Target class is absent or ambiguous: ${targets.size}" }
        val target = targets.single()
        targetSignature = target.signature
        val sourceText = Files.readString(scope.resolve(case.sourceFile))
        methods = target.methods.filter { it.cfg.stmts.isNotEmpty() }.sortedByDescending { method ->
            method.cfg.stmts.any { isOriginalClassStartupStep(it, target.signature, case.sourceFile, sourceText) }
        }
        require(methods.isNotEmpty()) { "Target class has no executable IR bodies" }
        checkpoint(next = "MACHINE")
        val machineObserver = ClassStartObserver(
            target = target.signature,
            sourceFile = case.sourceFile,
            sourceText = sourceText,
            onFirstOriginalStep = {
                checkpoint(next = "SOURCE_CLASS_ENTERED")
                val current = checkNotNull(observer)
                val started = current.initialStates > 0 && current.originalStatementsEntered > 0
                val progress = CallsClassStartResult(
                    classId = case.classId,
                    identity = case.identity,
                    manifestSha256 = fingerprint,
                    stage = stage,
                    started = started,
                    qualifyingStarted = started && case.acquisitionComplete,
                    initialStates = current.initialStates,
                    executedSteps = current.executedSteps,
                    targetClassSteps = current.targetSteps,
                    originalTargetClassSteps = current.originalSteps,
                    syntheticOrUnmappedTargetClassSteps = current.targetSteps - current.originalStepCallbacks,
                    targetStatementEntered = current.originalStatementsEntered > 0,
                    originalTargetStatementsEntered = current.originalStatementsEntered,
                    targetSuccessfulTransitions = current.originalSteps,
                    startupStatus = "STARTED",
                    acquisitionComplete = case.acquisitionComplete,
                    acquisitionSha256 = case.acquisitionSha256,
                    stageElapsedMillis = elapsed,
                    targetSignature = target.signature.toString(),
                    methodSignatures = methods.map { it.signature.toString() },
                    firstOriginalStep = current.firstOriginalStep,
                )
                writeCallsArtifactAtomically(output.resolve("progress.json"), encodeCallsArtifact(progress))
            },
            unknownCallObserver = { event ->
                unknownCalls += event.toCallsClassUnknownCallEvent(eventIndex = unknownCalls.size + 1)
                writeCallsArtifactAtomically(output.resolve("unknown-calls.json"), encodeCallsArtifact(unknownCalls))
            },
        )
        observer = machineObserver
        TsMachine(
            scene = scene,
            options = UMachineOptions(
                stateCollectionStrategy = StateCollectionStrategy.ALL,
                randomSeed = manifest.seed,
                collectedStatesLimit = manifest.collectedStatesLimit,
                timeout = manifest.budgetMillis.milliseconds,
                solverTimeout = manifest.solverLimitMillis.milliseconds,
                solverType = SolverType.YICES,
                coverageZone = CoverageZone.CLASS,
                stopOnCoverage = 0,
                removeCoveredEntryPoints = false,
                throwExceptionOnStepFailure = true,
            ),
            tsOptions = TsOptions(
                unknownCallModelSelection = if (manifest.profile.usesFrozenModels) {
                    TsUnknownCallModelSelection.All
                } else {
                    TsUnknownCallModelSelection.Only(emptySet())
                },
                unknownCallFallback = manifest.profile.fallback,
                eraseTypeAssertions = true,
                classCoverageTargets = setOf(target.signature),
            ),
            machineObserver = machineObserver,
            observer = machineObserver,
        ).use { machine ->
            checkpoint(next = "INITIAL_STATE")
            outcome = machine.analyzeWithMetadata(
                methods = methods,
                configureInitialState = { _, state ->
                    machineObserver.registerInitialState(state)
                }
            )
        }
        case.verifyInputs()
        checkpoint(next = "STOPPED")
    }.exceptionOrNull()
    val metrics = observer
    val drift = runCatching {
        case.verifyStartupAcquisition(manifestDirectory = manifestPath.parent)
        case.verifyInputs()
    }.exceptionOrNull()
    val entered = (metrics?.originalStatementsEntered ?: 0) > 0
    val started = (metrics?.initialStates ?: 0) > 0 && entered
    val engineFailed = outcome?.engineFailed == true || (failure != null && observer != null)
    val limited = outcome?.unsupportedCall == true || outcome?.runtimeLimited == true || engineFailed
    val result = CallsClassStartResult(
        classId = case.classId,
        identity = case.identity,
        manifestSha256 = fingerprint,
        stage = stage,
        started = started,
        qualifyingStarted = started && case.acquisitionComplete && drift == null,
        initialStates = metrics?.initialStates ?: 0,
        executedSteps = metrics?.executedSteps ?: 0,
        targetClassSteps = metrics?.targetSteps ?: 0,
        originalTargetClassSteps = metrics?.originalSteps ?: 0,
        syntheticOrUnmappedTargetClassSteps = (metrics?.targetSteps ?: 0) - (metrics?.originalStepCallbacks ?: 0),
        targetStatementEntered = entered,
        originalTargetStatementsEntered = metrics?.originalStatementsEntered ?: 0,
        targetSuccessfulTransitions = metrics?.originalSteps ?: 0,
        acquisitionComplete = case.acquisitionComplete,
        acquisitionSha256 = case.acquisitionSha256,
        startupStatus = when {
            started && limited -> "STARTED_WITH_LIMITATION"
            started -> "STARTED"
            else -> "NOT_STARTED"
        },
        stageElapsedMillis = elapsed,
        targetSignature = targetSignature?.toString(),
        methodSignatures = methods.map { it.signature.toString() },
        firstOriginalStep = metrics?.firstOriginalStep,
        stopReason = outcome?.stopReason?.name,
        timedOut = outcome?.timedOut == true,
        unsupportedCall = outcome?.unsupportedCall == true,
        runtimeLimited = outcome?.runtimeLimited == true,
        engineFailed = engineFailed,
        unsupportedPaths = outcome?.unsupportedPaths.orEmpty(),
        unknownCalls = unknownCalls,
        diagnostic = listOfNotNull(failure, drift).distinct().takeIf { it.isNotEmpty() }?.joinToString {
            "${it::class.java.name}: ${it.message}"
        },
    )
    writeCallsArtifactAtomically(output.resolve("result.json"), encodeCallsArtifact(result))
    System.out.appendLine(encodeCallsArtifact(result))
}

private const val CLASS_START_ARGUMENT_COUNT = 3
