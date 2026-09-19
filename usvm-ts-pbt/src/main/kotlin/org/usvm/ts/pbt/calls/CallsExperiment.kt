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
import java.nio.file.StandardOpenOption
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
    val catalogFingerprint: String,
    val sourceHash: String,
    val etsIrHash: String,
    val toolRevision: String,
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
    val expectedCatalogFingerprint: String,
    val seed: Long,
    val budget: Duration,
)

internal data class CallsSymbolicSearchResult(
    val status: CallsSymbolicStatus,
    val inputs: List<JsConcreteValue>? = null,
    val catalogFingerprint: String? = null,
    val elapsedMillis: Long,
    val diagnostic: String? = null,
) {
    init {
        require(status == CallsSymbolicStatus.REACHED || inputs == null) {
            "Only a reached source target may carry extracted inputs"
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
    val modelSet: CallsModelSetIdentity,
    val profiles: List<CallsExperimentProfile>,
    val seeds: List<Long>,
    val commonEligibleTargets: Int,
) : CallsRawRecord

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
    val replayStatus: CallsReplayStatus?,
    val catalogFingerprint: String?,
    val symbolicElapsedMillis: Long,
    val diagnostic: String? = null,
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

internal class CallsExperimentRunner(
    private val symbolicEngine: CallsSymbolicEngine,
    private val targetReplayer: CallsTargetReplayer,
) {
    fun run(
        manifest: CallsExperimentManifest,
        manifestDirectory: Path,
        rawOutput: Path,
    ) {
        Files.createDirectories(requireNotNull(rawOutput.parent) { "Raw output must have a parent directory" })
        Files.deleteIfExists(rawOutput)
        val commonEligibleTargets = manifest.projects.sumOf { project ->
            project.functions.sumOf { function -> function.targets.size }
        }
        val metadata = CallsRunMetadata(
            experimentId = manifest.experimentId,
            toolRevision = manifest.toolRevision,
            nativeFrontendRevision = manifest.nativeFrontendRevision,
            modelSet = manifest.modelSet,
            profiles = CallsExperimentProfile.entries,
            seeds = manifest.seeds,
            commonEligibleTargets = commonEligibleTargets,
        )

        append(rawOutput, metadata)

        manifest.projects.forEach { project ->
            runProject(
                manifest = manifest,
                manifestDirectory = manifestDirectory,
                rawOutput = rawOutput,
                project = project,
            )
        }
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
                expectedCatalogFingerprint = if (profile.usesFrozenModels) {
                    manifest.modelSet.catalogFingerprint
                } else {
                    EMPTY_CATALOG_FINGERPRINT
                },
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
            solverReached = symbolic.status == CallsSymbolicStatus.REACHED,
            inputExtracted = symbolic.inputs != null,
            replayStatus = replay?.status,
            catalogFingerprint = symbolic.catalogFingerprint,
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

    private companion object {
        const val EMPTY_CATALOG_FINGERPRINT =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}

internal object CallsExperimentAggregator {
    fun summarize(rawInput: Path): CallsExperimentSummary {
        val records = Files.readAllLines(rawInput).filter(String::isNotBlank).map { line ->
            CallsExperimentJson.json.decodeFromString<CallsRawRecord>(line)
        }
        val metadata = records.filterIsInstance<CallsRunMetadata>().single()
        val results = records.filterIsInstance<CallsTargetResult>()
        val byProfile = CallsExperimentProfile.entries.associateWith { profile ->
            val rows = results.filter { result -> result.profile == profile }
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
