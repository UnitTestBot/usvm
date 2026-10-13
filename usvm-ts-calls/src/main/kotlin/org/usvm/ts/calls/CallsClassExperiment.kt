package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.ts.pbt.model.PropertyDomain
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class CallsClassCase(
    val classId: String,
    val projectId: String,
    val revision: String,
    val sourceRoot: String,
    val sourceSha256: Map<String, String>,
    val surface: CallsClassSurface,
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
    val schemaVersion: Int = 1,
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
) {
    init {
        require(schemaVersion == 1)
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
) {
    fun harness(classDirectory: Path): CallsClassPreparedHarness {
        val root = classDirectory.resolve("source").toRealPath()
        classCase.verifySources(root, verifyCheckout = false)
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

    fun verifyOriginal() = classCase.verifySources(Path.of(originalSourceRoot), verifyCheckout = true)
}

@Serializable
internal data class CallsClassPreparationFailure(
    val classId: String,
    val diagnostic: String,
    val preparationElapsedMillis: Long = 0,
)

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
)

@Serializable
internal data class CallsClassReplayEvent(
    val index: Int,
    val scenarioId: String,
    val original: CallsClassReplayObservation? = null,
    val instrumented: CallsClassReplayObservation? = null,
    val oracle: CallsClassOracleResult,
    val error: String? = null,
    val replayElapsedMillis: Long = 0,
)

@Serializable
internal data class CallsClassCellIdentity(
    val schemaVersion: Int = 1,
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
            search.search.candidates[index].emittedAtMillis <= checkpoint
        }
        val accepted = indices.map { events[it] }.filter { it.oracle.accepted && it.error == null }
        val covered = universe.checkedCoveredIds(
            accepted.flatMap { event -> requireNotNull(event.instrumented).coveredStatementKeys },
        )
        CallsClassCoverageCheckpoint(
            searchBudgetMillis = checkpoint,
            emittedCandidates = indices.size,
            acceptedCandidates = accepted.size,
            rejectedCandidates = indices.size - accepted.size,
            coveredStatementIds = covered,
            uncoveredStatementIds = universe.statementIds - covered,
            totalStatements = universe.statementIds.size,
        )
    }
}

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
