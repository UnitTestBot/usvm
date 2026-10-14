package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.ts.pbt.fastcheck.TypeScriptFrozenModuleResolution
import org.usvm.ts.pbt.model.PropertyDomain
import java.nio.file.Files
import java.nio.file.Path

/** Frozen external correctness evidence, independent of the outcome of a treatment's symbolic pilot. */
@Serializable
internal data class CallsClassCorrectnessFrontierFailure(val reason: String, val provenance: String) {
    init {
        require(reason.isNotBlank() && provenance.isNotBlank()) {
            "Correctness limitations require evidence provenance"
        }
    }
}

@Serializable
internal data class CallsClassCase(
    val classId: String,
    val projectId: String,
    val revision: String,
    val sourceRoot: String,
    val sourceSha256: Map<String, String>,
    val surface: CallsClassSurface,
    val correctnessFrontierFailures: List<CallsClassCorrectnessFrontierFailure> = emptyList(),
) {
    init {
        require(classId.matches(Regex("[A-Za-z0-9_-]+"))) { "Class ID must be a safe directory name" }
        require(revision.matches(Regex("[0-9a-f]{40}"))) { "Source revision must be a Git commit" }
        require(surface.sourceFile in sourceSha256) { "Source closure omits the class source" }
        require(sourceSha256.values.all { it.matches(Regex("[0-9a-f]{64}")) }) { "Invalid source hashes" }
        require(
            sourceSha256.keys.all { path ->
                val value = Path.of(path)
                !value.isAbsolute && value.normalize() == value && !value.startsWith("..")
            },
        ) { "Source closure contains an unsafe path" }
    }

    fun originalRoot(manifestDirectory: Path): Path = manifestDirectory.resolve(sourceRoot).normalize().toRealPath()

    fun verifySources(root: Path, verifyCheckout: Boolean) {
        val canonicalRoot = root.toRealPath()
        if (verifyCheckout) verifyCallsGitCheckout(canonicalRoot, revision)
        sourceSha256.forEach { (relative, expected) ->
            val path = canonicalRoot.resolve(relative).normalize().toRealPath()
            require(path.startsWith(canonicalRoot)) { "Source closure escapes its root: $relative" }
            require(callsClassSourceSha256(Files.readAllBytes(path)) == expected) {
                "Frozen class source changed: $relative"
            }
        }
    }
}

@Serializable
internal data class CallsClassExperimentManifest(
    val schemaVersion: Int = 2,
    val experimentId: String,
    val toolRevision: String,
    val nativeFrontendRevision: String,
    val solver: String = "YICES",
    val modelSet: CallsModelSetIdentity,
    val seeds: List<Long>,
    val budgetsMillis: List<Long> = CLASS_SEARCH_BUDGETS_MILLIS,
    val maxSequenceLength: Int = 2,
    val timeQuantumMillis: Long = 250,
    val solverLimitMillis: Long = 1_000,
    val candidateCap: Int = 10_000,
    val classes: List<CallsClassCase>,
    val coveragePolicy: String = CLASS_NATIVE_COVERAGE_POLICY,
    val suspendedCoveragePolicy: String = CLASS_SUSPENDED_COVERAGE_POLICY,
) {
    init {
        require(schemaVersion == 2) { "Class measurement requires the native coverage and suspended-input protocol" }
        require(coveragePolicy == CLASS_NATIVE_COVERAGE_POLICY)
        require(suspendedCoveragePolicy == CLASS_SUSPENDED_COVERAGE_POLICY)
        require(experimentId.isNotBlank())
        require(toolRevision.matches(Regex("[0-9a-f]{40}"))) { "Tool revision must be clean" }
        require(modelSet.toolRevision == toolRevision) { "Frozen model revision differs" }
        require(solver == "YICES" || solver == "Z3")
        require(seeds.isNotEmpty() && seeds.distinct().size == seeds.size)
        require(budgetsMillis == CLASS_SEARCH_BUDGETS_MILLIS) { "Class treatments are exactly 30 and 120 seconds" }
        require(maxSequenceLength in 1..2)
        require(timeQuantumMillis in 1..budgetsMillis.first())
        require(solverLimitMillis in 1..budgetsMillis.first())
        require(candidateCap > 0)
        require(classes.isNotEmpty() && classes.map { it.classId }.distinct().size == classes.size)
    }

    fun verifyBuild(toolRevision: String, nativeFrontendRevision: String) {
        require(this.toolRevision == toolRevision) { "Class manifest and running tool revision differ" }
        require(this.nativeFrontendRevision == nativeFrontendRevision) { "Class manifest and frontend revision differ" }
        val availableModels = TsBuiltInUnknownCallModels.catalog().modelIds.toSet()
        require(availableModels.containsAll(modelSet.ids)) { "Frozen models are absent from this build" }
        require(System.getenv("ETS_FRONTEND_SCRIPT") == null) { "ETS_FRONTEND_SCRIPT must be unset" }
    }
}

@Serializable
internal data class CallsClassPreparedRecord(
    val classCase: CallsClassCase,
    val originalSourceRoot: String,
    val scenarios: List<CallsClassScenario>,
    val functions: List<CallsFunctionCase>,
    val observations: List<Map<String, PropertyDomain>>,
    val harnessFile: String,
    val harnessSha256: String,
    val instrumentation: CallsClassSourceInstrumentation,
    val preparationElapsedMillis: Long = 0,
    val resolution: TypeScriptFrozenModuleResolution? = null,
) {
    fun harness(classDirectory: Path): CallsClassPreparedHarness {
        val root = classDirectory.resolve("source").toRealPath()
        classCase.verifySources(root, verifyCheckout = false)
        if (resolution != null) {
            verifyCallsFrozenResolution(root = root, resolution = resolution)
            require(Files.readString(root.resolve(CALLS_FROZEN_TSCONFIG)) == resolution.tsconfig) {
                "Prepared module resolution configuration changed"
            }
        }
        require(callsClassSourceSha256(Files.readAllBytes(root.resolve(harnessFile))) == harnessSha256) {
            "Generated class wrapper changed"
        }
        require(functions.map { it.functionId } == scenarios.map { it.id }) { "Scenario schedule differs" }
        require(functions.all { it.sourceFile == harnessFile }) { "Scenarios do not share one wrapper module" }
        require(observations.size == functions.size)
        instrumentation.validateSurface(classCase.surface)
        instrumentation.universe.verifyUnchanged(root)

        return CallsClassPreparedHarness(
            surface = classCase.surface,
            scenarios = scenarios,
            sourceRoot = root,
            project = CallsProjectCase(
                projectId = classCase.projectId,
                revision = classCase.revision,
                sourceRoot = root.toString(),
                development = true,
                functions = functions,
            ),
            functions = functions,
            observations = observations,
        )
    }

    fun verifyOriginal(checkResolution: Boolean = false) {
        val root = Path.of(originalSourceRoot)
        classCase.verifySources(root, verifyCheckout = true)
        if (resolution != null) verifyCallsFrozenResolution(root = root, resolution = resolution)
        if (checkResolution) {
            val closure = checkedCallsSourceClosure(root = root, entry = root.resolve(classCase.surface.sourceFile))
            require(closure.resolution == resolution) { "Frozen project module resolution changed" }
        }
    }
}

@Serializable
internal data class CallsClassPreparationFailure(
    val classId: String,
    val diagnostic: String,
    val preparationElapsedMillis: Long = 0,
)

@Serializable
internal enum class CallsClassNativeSmokeStatus { VERIFIED, INVALID, INCONCLUSIVE }

@Serializable
internal data class CallsClassNativeSmokeResult(
    val seed: Long,
    val scenarioId: String,
    val status: CallsClassNativeSmokeStatus,
    val original: CallsClassReplayObservation? = null,
    val instrumented: CallsClassReplayObservation? = null,
    val diagnostic: String? = null,
)

/** Check the native observation mechanism independently of symbolic policies and their selected candidates. */
internal fun callsClassNativeConstructorSmoke(
    harness: CallsClassPreparedHarness,
    instrumentation: CallsClassSourceInstrumentation,
    seed: Long,
    timeoutMillis: Long,
): CallsClassNativeSmokeResult {
    val index = harness.scenarios.indexOfFirst { it.memberIds.isEmpty() }
    require(index >= 0) { "Class schedule omits the constructor observation scenario" }
    val function = harness.functions[index]
    val fields = harness.observations[index]
    val scenarioId = harness.scenarios[index].id
    val original = runCatching {
        OriginalTypeScriptClassReplayer(harness.sourceRoot, function, fields).use {
            it.sample(seed = seed, timeoutMillis = timeoutMillis)
        }
    }
    val originalValue = original.getOrNull()
    if (originalValue == null) {
        val failure = requireNotNull(original.exceptionOrNull())
        return CallsClassNativeSmokeResult(
            seed = seed,
            scenarioId = scenarioId,
            status = CallsClassNativeSmokeStatus.INCONCLUSIVE,
            diagnostic = "Native original sampling did not complete: ${failure.message ?: failure::class.java.name}",
        )
    }

    val instrumented = runCatching {
        OriginalTypeScriptClassReplayer(
            sourceRoot = harness.sourceRoot,
            function = function,
            observationFields = fields,
            sourceOverride = instrumentation.instrumentedSource,
            overrideSourceFile = harness.surface.sourceFile,
            counterGlobalName = instrumentation.hitGlobalName,
        ).use { it.replay(inputs = originalValue.inputs, timeoutMillis = timeoutMillis) }
    }
    val instrumentedValue = instrumented.getOrNull()
    if (instrumentedValue == null) {
        val failure = requireNotNull(instrumented.exceptionOrNull())
        return CallsClassNativeSmokeResult(
            seed = seed,
            scenarioId = scenarioId,
            status = CallsClassNativeSmokeStatus.INCONCLUSIVE,
            original = originalValue,
            diagnostic = "Native instrumented replay did not complete: ${failure.message ?: failure::class.java.name}",
        )
    }

    val invalid = callsClassNativeInfrastructureFailure(originalValue, instrumentedValue)
    val checkedIds = runCatching { instrumentation.universe.checkedCoveredIds(instrumentedValue.coveredStatementKeys) }
    val invalidDiagnostic = invalid ?: checkedIds.exceptionOrNull()?.let { it.message ?: it::class.java.name }
    val representable = originalValue.observation.representable && instrumentedValue.observation.representable
    val status = when {
        invalidDiagnostic != null -> CallsClassNativeSmokeStatus.INVALID
        representable -> CallsClassNativeSmokeStatus.VERIFIED
        else -> CallsClassNativeSmokeStatus.INCONCLUSIVE
    }

    val diagnostic = invalidDiagnostic ?: if (representable) {
        null
    } else {
        "Native observation is incomplete at the sampled input"
    }
    return CallsClassNativeSmokeResult(
        seed = seed,
        scenarioId = scenarioId,
        status = status,
        original = originalValue,
        instrumented = instrumentedValue,
        diagnostic = diagnostic,
    )
}

/** An incomplete lawful observation is not evidence of an instrumentation defect. */
internal fun callsClassNativeInfrastructureFailure(
    original: CallsClassReplayObservation,
    instrumented: CallsClassReplayObservation,
): String? {
    if (original.inputs != instrumented.inputs) return "Instrumentation changed the frozen native input transport"
    if (original.observation.completion != instrumented.observation.completion) {
        return "Instrumentation changed native completion"
    }
    if (original.observation.representable != instrumented.observation.representable) {
        return "Instrumentation changed native observability or observation schema"
    }
    val completeObservations = original.observation.representable && instrumented.observation.representable
    if (completeObservations && original.observation != instrumented.observation) {
        return "Instrumentation changed native returned values or receiver state"
    }
    return null
}

@Serializable
internal data class CallsClassPreflightResult(
    val eligible: Boolean,
    val classId: String,
    val preparedSha256: String,
    val toolRevision: String,
    val search: CallsClassSearchResult,
    val replays: List<CallsClassReplayEvent>,
    val missingRequiredScenarios: List<String>,
    val reasons: List<String>,
    val admissionVersion: Int = 0,
    val sourceAdmissionFailures: List<String> = emptyList(),
    val nativeInfrastructureFailures: List<String> = emptyList(),
    val nativeSmoke: CallsClassNativeSmokeResult? = null,
    val diagnosticReady: Boolean? = null,
    val primaryInferenceEligible: Boolean? = null,
    val correctnessFrontierFailures: List<CallsClassCorrectnessFrontierFailure> = emptyList(),
) {
    init {
        require(admissionVersion in 0..1) { "Unsupported class admission version" }
        if (admissionVersion == 1) {
            require(diagnosticReady != null && primaryInferenceEligible != null) {
                "Version-one admission must distinguish pilot readiness and primary inference"
            }
            require(sourceAdmissionFailures.isNotEmpty() || nativeSmoke != null) {
                "Source-ready admission requires a native probe"
            }
        }
    }
}

internal fun callsClassPreflightResult(
    classCase: CallsClassCase,
    preparedSha256: String,
    toolRevision: String,
    search: CallsClassSearchResult,
    replays: List<CallsClassReplayEvent>,
    missingScenarios: List<String>,
    pilotDiagnostics: List<String>,
    sourceAdmissionFailures: List<String>,
    nativeSmoke: CallsClassNativeSmokeResult?,
    nativeInfrastructureFailures: List<String>,
): CallsClassPreflightResult {
    require(sourceAdmissionFailures.isNotEmpty() || nativeSmoke != null) {
        "Source-ready admission requires a native probe"
    }
    val invalidSmoke = nativeSmoke?.status == CallsClassNativeSmokeStatus.INVALID
    val eligible = sourceAdmissionFailures.isEmpty() && !invalidSmoke
    val primaryInferenceEligible = eligible && nativeSmoke?.status == CallsClassNativeSmokeStatus.VERIFIED &&
        classCase.correctnessFrontierFailures.isEmpty()
    val admissionDiagnostics = sourceAdmissionFailures + nativeInfrastructureFailures +
        listOfNotNull(nativeSmoke?.diagnostic) +
        classCase.correctnessFrontierFailures.map { "${it.reason} (${it.provenance})" }

    return CallsClassPreflightResult(
        eligible = eligible,
        classId = classCase.classId,
        preparedSha256 = preparedSha256,
        toolRevision = toolRevision,
        search = search,
        replays = replays,
        missingRequiredScenarios = missingScenarios,
        reasons = admissionDiagnostics + pilotDiagnostics,
        admissionVersion = 1,
        sourceAdmissionFailures = sourceAdmissionFailures,
        nativeInfrastructureFailures = nativeInfrastructureFailures,
        nativeSmoke = nativeSmoke,
        diagnosticReady = pilotDiagnostics.isEmpty() && nativeInfrastructureFailures.isEmpty(),
        primaryInferenceEligible = primaryInferenceEligible,
        correctnessFrontierFailures = classCase.correctnessFrontierFailures,
    )
}

@Serializable
internal data class CallsClassReplayEvent(
    val index: Int,
    val scenarioId: String,
    val original: CallsClassReplayObservation? = null,
    val instrumented: CallsClassReplayObservation? = null,
    val oracle: CallsClassOracleResult,
    val error: String? = null,
    val replayElapsedMillis: Long = 0,
    val nativeCoverage: CallsClassOracleResult,
    val origin: CallsCoverageCandidateOrigin = CallsCoverageCandidateOrigin.TERMINATED,
    val strictOracleApplicable: Boolean = true,
)

/** Primary coverage checks the native measurement, independently of symbolic execution predictions. */
internal fun callsClassNativeCoverageAcceptance(
    original: CallsClassReplayObservation?,
    instrumented: CallsClassReplayObservation?,
    universe: CallsClassSourceUniverse,
    error: String? = null,
): CallsClassOracleResult {
    if (error != null || original == null || instrumented == null) {
        return CallsClassOracleResult(accepted = false, diagnostic = error ?: "Concrete replay missing")
    }

    val foreignIds = runCatching { universe.checkedCoveredIds(instrumented.coveredStatementKeys) }.exceptionOrNull()
    val failure = callsClassNativeInfrastructureFailure(original, instrumented)
        ?: foreignIds?.let { it.message ?: it::class.java.name }
    if (failure != null) return CallsClassOracleResult(accepted = false, diagnostic = failure)

    val complete = original.observation.representable && instrumented.observation.representable
    if (!complete) {
        return CallsClassOracleResult(accepted = false, diagnostic = "Native observation is incomplete")
    }

    return CallsClassOracleResult(accepted = true)
}

@Serializable
internal data class CallsClassCellIdentity(
    val schemaVersion: Int = 2,
    val experimentId: String,
    val toolRevision: String,
    val nativeFrontendRevision: String,
    val manifestSha256: String,
    val preparedSha256: String,
    val preflightSha256: String,
    val universeSha256: String,
    val classId: String,
    val profile: CallsExperimentProfile,
    val seed: Long,
    val budgetMillis: Long,
    val coveragePolicy: String = CLASS_NATIVE_COVERAGE_POLICY,
    val suspendedCoveragePolicy: String = CLASS_SUSPENDED_COVERAGE_POLICY,
)

@Serializable
internal data class CallsClassCoverageCheckpoint(
    val searchBudgetMillis: Long,
    val emittedCandidates: Int,
    val acceptedCandidates: Int,
    val rejectedCandidates: Int,
    val coveredStatementIds: Set<String>,
    val uncoveredStatementIds: Set<String>,
    val totalStatements: Int,
    val strictAcceptedCandidates: Int,
    val strictRejectedCandidates: Int,
    val strictOracleNotApplicable: Int,
    val strictCoveredStatementIds: Set<String>,
    val strictUncoveredStatementIds: Set<String>,
    val suspendedCandidates: Int,
    val includesSuspendedExtraction: Boolean,
    val postStopSuspendedCandidates: Int,
)

@Serializable
internal data class CallsClassCellResult(
    val identity: CallsClassCellIdentity,
    val search: CallsClassSearchResult,
    val checkpoints: List<CallsClassCoverageCheckpoint>,
    val replayErrors: Int,
    val oracleRejections: Int,
    val replayElapsedMillis: Long,
    val cellElapsedMillis: Long,
    val primaryInferenceEligible: Boolean? = null,
    val correctnessFrontierFailures: List<CallsClassCorrectnessFrontierFailure> = emptyList(),
    val nativeInfrastructureFailures: List<String> = emptyList(),
    val nativeCoverageRejections: Int = 0,
    val strictOracleNotApplicable: Int = 0,
)

internal fun callsClassCoverageCheckpoints(
    search: CallsClassSearchResult,
    events: List<CallsClassReplayEvent>,
    universe: CallsClassSourceUniverse,
    budgetMillis: Long,
): List<CallsClassCoverageCheckpoint> {
    require(events.size == search.search.candidates.size) { "Incomplete class replay journal" }
    val checkpoints = (CLASS_COVERAGE_CHECKPOINTS_MILLIS + budgetMillis)
        .filter { it <= budgetMillis }.distinct().sorted()

    return checkpoints.map { checkpoint ->
        val indices = search.search.candidates.indices.filter { index ->
            val candidate = search.search.candidates[index]
            when (candidate.origin) {
                CallsCoverageCandidateOrigin.TERMINATED ->
                    candidate.emittedAtMillis <= checkpoint
                CallsCoverageCandidateOrigin.SUSPENDED -> if (candidate.suspendedAtMachineStop) {
                    checkpoint == budgetMillis
                } else {
                    candidate.emittedAtMillis <= checkpoint
                }
            }
        }
        val selectedEvents = indices.map { events[it] }
        indices.forEach { index ->
            val event = events[index]
            val candidate = search.search.candidates[index]
            val matchesCandidate = event.index == index &&
                event.scenarioId == candidate.entryPointId && event.origin == candidate.origin
            require(matchesCandidate) {
                "Class replay event differs from its candidate"
            }
            require(event.strictOracleApplicable == (candidate.origin == CallsCoverageCandidateOrigin.TERMINATED)) {
                "Class replay symbolic oracle applicability differs from its candidate"
            }
            val expectedNative = callsClassNativeCoverageAcceptance(
                original = event.original,
                instrumented = event.instrumented,
                universe = universe,
                error = event.error,
            )
            require(event.nativeCoverage == expectedNative) { "Class replay native coverage decision differs" }
        }
        val accepted = selectedEvents.filter { it.nativeCoverage.accepted && it.error == null }
        val covered = universe.checkedCoveredIds(
            accepted.flatMap { event -> requireNotNull(event.instrumented).coveredStatementKeys },
        )
        val strictAccepted = selectedEvents.filter {
            it.strictOracleApplicable && it.oracle.accepted && it.error == null
        }
        val strictCovered = universe.checkedCoveredIds(
            strictAccepted.flatMap { event -> requireNotNull(event.instrumented).coveredStatementKeys },
        )
        val strictNotApplicable = selectedEvents.count { !it.strictOracleApplicable }
        CallsClassCoverageCheckpoint(
            searchBudgetMillis = checkpoint,
            emittedCandidates = indices.size,
            acceptedCandidates = accepted.size,
            rejectedCandidates = indices.size - accepted.size,
            coveredStatementIds = covered,
            uncoveredStatementIds = universe.statementIds - covered,
            totalStatements = universe.statementIds.size,
            strictAcceptedCandidates = strictAccepted.size,
            strictRejectedCandidates = indices.size - strictAccepted.size - strictNotApplicable,
            strictOracleNotApplicable = strictNotApplicable,
            strictCoveredStatementIds = strictCovered,
            strictUncoveredStatementIds = universe.statementIds - strictCovered,
            suspendedCandidates = indices.count {
                search.search.candidates[it].origin == CallsCoverageCandidateOrigin.SUSPENDED
            },
            includesSuspendedExtraction = checkpoint == budgetMillis,
            postStopSuspendedCandidates = indices.count { search.search.candidates[it].suspendedAtMachineStop },
        )
    }
}

internal const val CLASS_NATIVE_COVERAGE_POLICY = "native-original-instrumented-complete-v2"
internal const val CLASS_SUSPENDED_COVERAGE_POLICY = "reached-prefix-and-final-frontier-inputs-v1"

private const val SHORT_CLASS_BUDGET_MILLIS = 30_000L
private const val LONG_CLASS_BUDGET_MILLIS = 120_000L
private const val FIRST_CLASS_CHECKPOINT_MILLIS = 1_000L
private const val SECOND_CLASS_CHECKPOINT_MILLIS = 5_000L
private const val THIRD_CLASS_CHECKPOINT_MILLIS = 10_000L
private val CLASS_SEARCH_BUDGETS_MILLIS = listOf(SHORT_CLASS_BUDGET_MILLIS, LONG_CLASS_BUDGET_MILLIS)
private val CLASS_COVERAGE_CHECKPOINTS_MILLIS = listOf(
    FIRST_CLASS_CHECKPOINT_MILLIS,
    SECOND_CLASS_CHECKPOINT_MILLIS,
    THIRD_CLASS_CHECKPOINT_MILLIS,
    SHORT_CLASS_BUDGET_MILLIS,
    LONG_CLASS_BUDGET_MILLIS,
)
