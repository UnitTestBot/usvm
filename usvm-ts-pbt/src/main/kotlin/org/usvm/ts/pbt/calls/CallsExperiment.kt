package org.usvm.ts.pbt.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Serializable
internal enum class CallsExperimentProfile(
    val usesFrozenModels: Boolean,
    val fallback: TsResidualCallPolicy,
) {
    EMPTY_STOP(usesFrozenModels = false, fallback = TsResidualCallPolicy.STOP_PATH),
    EMPTY_FRESH(usesFrozenModels = false, fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
    FROZEN_STOP(usesFrozenModels = true, fallback = TsResidualCallPolicy.STOP_PATH),
    FROZEN_FRESH(usesFrozenModels = true, fallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
}

@Serializable
internal data class CallsModelSetIdentity(
    val ids: Set<String>,
    val toolRevision: String,
    @SerialName("catalogFingerprint")
    val legacyCatalogFingerprint: String? = null,
    @SerialName("sourceHash")
    val legacyModelSourceHash: String? = null,
    @SerialName("etsIrHash")
    val legacyModelEtsIrHash: String? = null,
)

@Serializable
internal data class CallsFunctionCase(
    val functionId: String,
    val sourceFile: String,
    val entryPoint: TypeScriptEntryPoint,
    val inputs: List<PropertyInput>,
    val targets: List<CallsSourceTarget>,
)

@Serializable
internal data class CallsProjectCase(
    val projectId: String,
    val revision: String,
    val sourceRoot: String,
    val development: Boolean,
    val functions: List<CallsFunctionCase>,
)

@Serializable
internal data class CallsExperimentManifest(
    val schemaVersion: Int,
    val experimentId: String,
    val toolRevision: String,
    val nativeFrontendRevision: String,
    val nativeFrontendSha256: String,
    val solver: String,
    val searchPolicy: String,
    val modelSet: CallsModelSetIdentity,
    val seeds: List<Long>,
    val perTargetBudgetMillis: Long,
    val projects: List<CallsProjectCase>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported calls experiment schema: $schemaVersion" }
        require(experimentId.isNotBlank()) { "Experiment ID must not be blank" }
        require(seeds.isNotEmpty() && seeds.distinct().size == seeds.size) { "Seeds must be non-empty and unique" }
        require(perTargetBudgetMillis > 0) { "Per-target budget must be positive" }
        require(projects.isNotEmpty()) { "At least one project is required" }
        require(solver == "Z3") { "The frozen calls experiment requires the Z3 solver" }
        require(searchPolicy == "BFS") { "The frozen calls experiment requires BFS search" }
        val cleanGitRevision = Regex("[0-9a-f]{40}")
        require(toolRevision.matches(cleanGitRevision)) {
            "Tool revision must identify a clean Git commit"
        }
        require(toolRevision == modelSet.toolRevision) { "Tool and model-set revisions must match" }
        val functions = projects.flatMap(CallsProjectCase::functions)
        require(functions.map(CallsFunctionCase::functionId).distinct().size == functions.size) {
            "Function IDs must be unique"
        }
        require(functions.all { function -> function.sourceFile == function.entryPoint.module }) {
            "Function source files must match their replay entry-point modules"
        }
        val targets = functions.flatMap(CallsFunctionCase::targets)
        require(targets.map(CallsSourceTarget::targetId).distinct().size == targets.size) {
            "Target IDs must be unique"
        }
        require(
            functions.all { function ->
                function.targets.all { target -> target.sourcePath == function.sourceFile }
            },
        ) {
            "Every target must belong to its function source file"
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
internal enum class CallsSymbolicStatus {
    REACHED,
    UNREACHED,
    UNREPRESENTABLE,
    UNSUPPORTED,
    TIMEOUT,
    TOOL_ERROR,
    UNMAPPED,
    AMBIGUOUS,
}

internal data class CallsSymbolicSearchRequest(
    val sourceRoot: Path,
    val project: CallsProjectCase,
    val function: CallsFunctionCase,
    val target: CallsSourceTarget,
    val profile: CallsExperimentProfile,
    val frozenModelIds: Set<String>,
    val expectedNativeFrontendRevision: String,
    val expectedNativeFrontendSha256: String,
    val seed: Long,
    val budget: Duration,
)

internal data class CallsSymbolicSearchResult(
    val status: CallsSymbolicStatus,
    val solverReached: Boolean = status == CallsSymbolicStatus.REACHED,
    val inputs: List<JsConcreteValue>? = null,
    val elapsedMillis: Long,
    val diagnostic: String? = null,
) {
    init {
        require(solverReached || inputs == null) {
            "Only a solver-reached source target may carry extracted inputs"
        }
    }
}

internal fun interface CallsSymbolicEngine {
    fun search(request: CallsSymbolicSearchRequest): CallsSymbolicSearchResult
}

@Serializable
internal sealed interface CallsRawRecord

@Serializable
@SerialName("run-metadata")
internal data class CallsRunMetadata(
    val experimentId: String,
    val toolRevision: String,
    val nativeFrontendRevision: String,
    val nativeFrontendSha256: String? = null,
    val modelSet: CallsModelSetIdentity,
    val profiles: List<CallsExperimentProfile>,
    val seeds: List<Long>,
    val commonEligibleTargets: Int,
    val targets: List<CallsRunTargetIdentity>,
) : CallsRawRecord

@Serializable
internal data class CallsRunTargetIdentity(
    val projectId: String,
    val revision: String,
    val development: Boolean,
    val functionId: String,
    val targetId: String,
    val siteId: String,
)

@Serializable
@SerialName("target-result")
internal data class CallsTargetResult(
    val experimentId: String,
    val projectId: String,
    val revision: String,
    val development: Boolean,
    val functionId: String,
    val targetId: String,
    val siteId: String,
    val profile: CallsExperimentProfile,
    val seed: Long,
    val symbolicStatus: CallsSymbolicStatus,
    val solverReached: Boolean,
    val inputExtracted: Boolean,
    val inputs: List<JsConcreteValue>? = null,
    val replayStatus: CallsReplayStatus?,
    @SerialName("catalogFingerprint")
    val legacyCatalogFingerprint: String? = null,
    val symbolicElapsedMillis: Long,
    val diagnostic: String? = null,
) : CallsRawRecord

@Serializable
@SerialName("run-completion")
internal data class CallsRunCompletion(
    val experimentId: String,
    val resultRows: Int,
) : CallsRawRecord

@Serializable
internal data class CallsExperimentSummary(
    val experimentId: String,
    val commonEligibleTargets: Int,
    val resultRows: Int,
    val byProfile: Map<CallsExperimentProfile, CallsProfileSummary>,
)

@Serializable
internal data class CallsProfileSummary(
    val runs: Int,
    val solverReached: Int,
    val inputExtracted: Int,
    val replayConfirmed: Int,
    val replayRejected: Int,
    val unsupported: Int,
    val timeouts: Int,
    val toolErrors: Int,
    val symbolicStatuses: Map<CallsSymbolicStatus, Int>,
    val replayStatuses: Map<CallsReplayStatus, Int>,
    val replayNotRun: Int,
)

internal object CallsExperimentJson {
    val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        useAlternativeNames = false
        prettyPrint = false
    }

    fun decodeManifest(value: String): CallsExperimentManifest = json.decodeFromString(value)

    fun encodeManifest(manifest: CallsExperimentManifest): String = json.encodeToString(manifest)
}

internal object CallsBuildIdentity {
    val toolRevision: String by lazy {
        val properties = Properties()
        val resource = checkNotNull(javaClass.getResourceAsStream("/org/usvm/ts/pbt/calls/build.properties")) {
            "Missing calls build identity"
        }
        resource.use(properties::load)

        checkNotNull(properties.getProperty("tool.revision")).takeIf(String::isNotBlank)
            ?: error("Missing tool revision in calls build identity")
    }
}

internal class CallsExperimentRunner(
    private val symbolicEngine: CallsSymbolicEngine,
    private val targetReplayer: CallsTargetReplayer,
    private val runtimeToolRevision: String = CallsBuildIdentity.toolRevision,
) {
    fun run(
        manifest: CallsExperimentManifest,
        manifestDirectory: Path,
        rawOutput: Path,
    ) {
        require(manifest.toolRevision == runtimeToolRevision) {
            "Manifest tool revision ${manifest.toolRevision} does not match running build $runtimeToolRevision"
        }
        require(System.getenv("ETS_FRONTEND_SCRIPT") == null) {
            "ETS_FRONTEND_SCRIPT must be unset so the frozen native frontend runtime is used"
        }
        val outputDirectory = requireNotNull(rawOutput.parent) { "Raw output must have a parent directory" }
        val partialOutput = outputDirectory.resolve("${rawOutput.fileName}.partial")
        Files.createDirectories(outputDirectory)
        Files.deleteIfExists(partialOutput)
        val commonEligibleTargets = manifest.projects.sumOf { project ->
            project.functions.sumOf { function -> function.targets.size }
        }
        val metadata = CallsRunMetadata(
            experimentId = manifest.experimentId,
            toolRevision = manifest.toolRevision,
            nativeFrontendRevision = manifest.nativeFrontendRevision,
            nativeFrontendSha256 = manifest.nativeFrontendSha256,
            modelSet = manifest.modelSet,
            profiles = CallsExperimentProfile.entries,
            seeds = manifest.seeds,
            commonEligibleTargets = commonEligibleTargets,
            targets = manifest.projects.flatMap { project ->
                project.functions.flatMap { function ->
                    function.targets.map { target ->
                        CallsRunTargetIdentity(
                            projectId = project.projectId,
                            revision = project.revision,
                            development = project.development,
                            functionId = function.functionId,
                            targetId = target.targetId,
                            siteId = target.siteId,
                        )
                    }
                }
            },
        )

        append(partialOutput, metadata)

        manifest.projects.forEach { project ->
            runProject(
                manifest = manifest,
                manifestDirectory = manifestDirectory,
                rawOutput = partialOutput,
                project = project,
            )
        }

        val resultRows = Math.multiplyExact(
            Math.multiplyExact(commonEligibleTargets, manifest.seeds.size),
            CallsExperimentProfile.entries.size,
        )
        append(
            partialOutput,
            CallsRunCompletion(
                experimentId = manifest.experimentId,
                resultRows = resultRows,
            ),
        )
        Files.move(
            partialOutput,
            rawOutput,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun runProject(
        manifest: CallsExperimentManifest,
        manifestDirectory: Path,
        rawOutput: Path,
        project: CallsProjectCase,
    ) {
        val sourceRoot = manifestDirectory.resolve(project.sourceRoot).normalize().toRealPath()

        project.functions.forEach { function ->
            runFunction(
                manifest = manifest,
                rawOutput = rawOutput,
                sourceRoot = sourceRoot,
                project = project,
                function = function,
            )
        }
    }

    private fun runFunction(
        manifest: CallsExperimentManifest,
        rawOutput: Path,
        sourceRoot: Path,
        project: CallsProjectCase,
        function: CallsFunctionCase,
    ) {
        function.targets.forEach { target ->
            manifest.seeds.forEach { seed ->
                rotatedProfiles(seed).forEach { profile ->
                    val result = runTarget(
                        manifest = manifest,
                        sourceRoot = sourceRoot,
                        project = project,
                        function = function,
                        target = target,
                        seed = seed,
                        profile = profile,
                    )

                    append(rawOutput, result)
                }
            }
        }
    }

    private fun runTarget(
        manifest: CallsExperimentManifest,
        sourceRoot: Path,
        project: CallsProjectCase,
        function: CallsFunctionCase,
        target: CallsSourceTarget,
        seed: Long,
        profile: CallsExperimentProfile,
    ): CallsTargetResult {
        val symbolic = symbolicEngine.search(
            CallsSymbolicSearchRequest(
                sourceRoot = sourceRoot,
                project = project,
                function = function,
                target = target,
                profile = profile,
                frozenModelIds = manifest.modelSet.ids,
                expectedNativeFrontendRevision = manifest.nativeFrontendRevision,
                expectedNativeFrontendSha256 = manifest.nativeFrontendSha256,
                seed = seed,
                budget = manifest.perTargetBudgetMillis.milliseconds,
            ),
        )
        val replay = symbolic.inputs?.let { inputs ->
            targetReplayer.replay(
                sourceRoots = listOf(sourceRoot),
                entryPoint = function.entryPoint,
                inputs = inputs,
                target = target,
                timeoutMillis = manifest.perTargetBudgetMillis,
            )
        }

        return CallsTargetResult(
            experimentId = manifest.experimentId,
            projectId = project.projectId,
            revision = project.revision,
            development = project.development,
            functionId = function.functionId,
            targetId = target.targetId,
            siteId = target.siteId,
            profile = profile,
            seed = seed,
            symbolicStatus = symbolic.status,
            solverReached = symbolic.solverReached,
            inputExtracted = symbolic.inputs != null,
            inputs = symbolic.inputs,
            replayStatus = replay?.status,
            symbolicElapsedMillis = symbolic.elapsedMillis,
            diagnostic = replay?.message ?: replay?.reason ?: symbolic.diagnostic,
        )
    }

    private fun rotatedProfiles(seed: Long): List<CallsExperimentProfile> {
        val profiles = CallsExperimentProfile.entries
        val offset = Math.floorMod(seed, profiles.size.toLong()).toInt()

        return profiles.drop(offset) + profiles.take(offset)
    }

    private fun append(path: Path, record: CallsRawRecord) {
        Files.writeString(
            path,
            CallsExperimentJson.json.encodeToString<CallsRawRecord>(record) + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}

internal data class CallsValidatedRawResults(
    val metadata: CallsRunMetadata,
    val results: List<CallsTargetResult>,
)

internal object CallsRawResultsReader {
    @Suppress("LongMethod")
    fun read(rawInput: Path): CallsValidatedRawResults {
        val records = Files.readAllLines(rawInput).filter(String::isNotBlank).map { line ->
            CallsExperimentJson.json.decodeFromString<CallsRawRecord>(line)
        }
        val metadataRows = records.filterIsInstance<CallsRunMetadata>()
        require(metadataRows.size == 1) { "Raw results must contain exactly one metadata record" }
        val metadata = metadataRows.single()
        val results = records.filterIsInstance<CallsTargetResult>()
        val completionRows = records.filterIsInstance<CallsRunCompletion>()
        require(completionRows.size == 1) { "Raw results must contain exactly one completion record" }
        val completion = completionRows.single()
        val expectedRows = Math.multiplyExact(
            Math.multiplyExact(metadata.commonEligibleTargets, metadata.seeds.size),
            metadata.profiles.size,
        )
        require(completion.experimentId == metadata.experimentId) { "Completion experiment ID does not match metadata" }
        require(completion.resultRows == expectedRows) { "Completion row count does not match metadata" }
        require(results.size == expectedRows) { "Raw result row count does not match metadata" }
        require(metadata.targets.size == metadata.commonEligibleTargets) {
            "Metadata target count does not match common eligible target count"
        }
        val targetIdentities = metadata.targets.associateBy { target ->
            Triple(target.projectId, target.functionId, target.targetId)
        }
        require(targetIdentities.size == metadata.targets.size) { "Metadata contains duplicate targets" }
        require(results.all { result -> result.experimentId == metadata.experimentId }) {
            "Result experiment ID does not match metadata"
        }
        require(
            results.all { result ->
                val identity = targetIdentities[Triple(result.projectId, result.functionId, result.targetId)]
                identity != null &&
                    result.revision == identity.revision &&
                    result.development == identity.development &&
                    result.siteId == identity.siteId
            },
        ) { "Result target identity does not match metadata" }
        val resultKeys = results.map { result ->
            ResultKey(
                projectId = result.projectId,
                functionId = result.functionId,
                targetId = result.targetId,
                profile = result.profile,
                seed = result.seed,
            )
        }
        require(resultKeys.distinct().size == resultKeys.size) { "Raw results contain duplicate target runs" }
        val expectedKeys = metadata.targets.flatMap { target ->
            metadata.seeds.flatMap { seed ->
                metadata.profiles.map { profile ->
                    ResultKey(
                        projectId = target.projectId,
                        functionId = target.functionId,
                        targetId = target.targetId,
                        profile = profile,
                        seed = seed,
                    )
                }
            }
        }
        require(resultKeys.toSet() == expectedKeys.toSet()) { "Raw results do not match the frozen target matrix" }

        return CallsValidatedRawResults(metadata = metadata, results = results)
    }

    private data class ResultKey(
        val projectId: String,
        val functionId: String,
        val targetId: String,
        val profile: CallsExperimentProfile,
        val seed: Long,
    )
}

internal object CallsExperimentAggregator {
    fun summarize(rawInput: Path): CallsExperimentSummary {
        val (metadata, results) = CallsRawResultsReader.read(rawInput)
        val byProfile = CallsExperimentProfile.entries.associateWith { profile ->
            val rows = results.filter { result -> result.profile == profile }
            val symbolicStatuses = CallsSymbolicStatus.entries.associateWith { status ->
                rows.count { result -> result.symbolicStatus == status }
            }
            val replayStatuses = CallsReplayStatus.entries.associateWith { status ->
                rows.count { result -> result.replayStatus == status }
            }
            val replayNotRun = rows.count { result -> result.replayStatus == null }
            check(symbolicStatuses.values.sum() == rows.size) { "Symbolic statuses do not reconcile with profile runs" }
            check(replayStatuses.values.sum() + replayNotRun == rows.size) {
                "Replay statuses do not reconcile with profile runs"
            }
            CallsProfileSummary(
                runs = rows.size,
                solverReached = rows.count(CallsTargetResult::solverReached),
                inputExtracted = rows.count(CallsTargetResult::inputExtracted),
                replayConfirmed = rows.count { result -> result.replayStatus == CallsReplayStatus.CONFIRMED },
                replayRejected = rows.count { result -> result.replayStatus == CallsReplayStatus.REJECTED },
                unsupported = rows.count { result ->
                    result.symbolicStatus == CallsSymbolicStatus.UNSUPPORTED
                },
                timeouts = rows.count { result ->
                    result.symbolicStatus == CallsSymbolicStatus.TIMEOUT ||
                        result.replayStatus == CallsReplayStatus.TIMEOUT
                },
                toolErrors = rows.count { result ->
                    result.symbolicStatus == CallsSymbolicStatus.TOOL_ERROR ||
                        result.replayStatus == CallsReplayStatus.TOOL_ERROR
                },
                symbolicStatuses = symbolicStatuses,
                replayStatuses = replayStatuses,
                replayNotRun = replayNotRun,
            )
        }

        return CallsExperimentSummary(
            experimentId = metadata.experimentId,
            commonEligibleTargets = metadata.commonEligibleTargets,
            resultRows = results.size,
            byProfile = byProfile,
        )
    }
}
