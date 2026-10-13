package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.milliseconds

@Serializable
internal data class CallsCoverageCellIdentity(
    val schemaVersion: Int,
    val toolRevision: String,
    val nativeFrontendRevision: String,
    val sourceManifest: String,
    val sourceIdentity: CallsCoverageSourceIdentity,
    val projectIndex: Int,
    val functionIndex: Int,
    val profile: CallsExperimentProfile,
    val seed: Long,
    val budgetMillis: Long,
    val solverLimitMillis: Long,
    val candidateCap: Int,
)

@Serializable
internal data class CallsCoverageReplayEvent(
    val index: Int,
    val replay: CallsCoverageReplayResult? = null,
    val error: String? = null,
)

@Serializable
internal data class CallsCoverageCheckpoint(
    val searchBudgetMillis: Long,
    val emittedCandidates: Int,
    val replayedCandidates: Int,
    val coveredStatements: Int,
    val coveredIfArms: Int,
)

@Serializable
internal data class CallsCoverageCellResult(
    val identity: CallsCoverageCellIdentity,
    val search: CallsCoverageSearchResult,
    val replayErrors: Int,
    val completionMismatches: Int,
    val checkpoints: List<CallsCoverageCheckpoint>,
)

internal fun runCoverageCell(
    args: List<String>,
    runtimeToolRevision: String = CallsBuildIdentity.toolRevision,
    runtimeNativeFrontendRevision: String = CallsBuildIdentity.nativeFrontendRevision,
    searchFunction: (CallsCoverageSearchRequest) -> CallsCoverageSearchResult = CurrentTsCallsCoverageEngine()::search,
) {
    val request = parseCoverageCellRequest(
        args = args,
        runtimeToolRevision = runtimeToolRevision,
        runtimeNativeFrontendRevision = runtimeNativeFrontendRevision,
    )
    val source = request.source
    val identity = request.identity
    val output = request.output
    require(Regex("[0-9a-f]{40}").matches(identity.toolRevision)) { "Coverage build must be clean" }

    Files.createDirectories(output)
    val identityPath = output.resolve("identity.json")
    val expectedIdentity = encode(identity)
    if (Files.exists(identityPath)) {
        require(Files.readString(identityPath).trim() == expectedIdentity) { "Cell identity changed" }
    } else {
        require(Files.list(output).use { entries -> entries.findAny().isEmpty }) { "Output directory is not empty" }
        atomicWrite(identityPath, expectedIdentity)
    }
    require(!Files.exists(output.resolve("result.json"))) { "Cell already completed" }

    val search = loadOrRunCoverageSearch(
        source = source,
        identity = identity,
        output = output,
        searchFunction = searchFunction,
    )
    val events = replayCoverageCandidates(source = source, search = search, output = output)

    source.verifyUnchanged()
    val result = CallsCoverageCellResult(
        identity = identity,
        search = search,
        replayErrors = events.count { it.error != null },
        completionMismatches = events.indices.count { index ->
            events[index].replay?.completion != null &&
                events[index].replay?.completion != search.candidates[index].completion
        },
        checkpoints = coverageCheckpoints(search = search, events = events, budgetMillis = identity.budgetMillis),
    )
    atomicWrite(output.resolve("result.json"), encode(result))
    System.out.appendLine(encode(result))
}

private data class CoverageCellRequest(
    val source: CallsCoverageSource,
    val identity: CallsCoverageCellIdentity,
    val output: Path,
)

private fun parseCoverageCellRequest(
    args: List<String>,
    runtimeToolRevision: String,
    runtimeNativeFrontendRevision: String,
): CoverageCellRequest {
    require(args.size == COVERAGE_CELL_ARGUMENT_COUNT) {
        "coverage-cell requires $COVERAGE_CELL_ARGUMENT_COUNT arguments"
    }
    val arguments = args.iterator()
    val manifestPath = Path.of(arguments.next())
    val projectIndex = arguments.next().toInt()
    val functionIndex = arguments.next().toInt()
    val source = CallsCoverageSource.load(manifestPath, projectIndex = projectIndex, functionIndex = functionIndex)
    val profile = CallsExperimentProfile.valueOf(arguments.next())
    val seed = arguments.next().toLong()
    val output = Path.of(arguments.next()).toAbsolutePath().normalize()
    val budgetMillis = arguments.next().toLong()
    val solverLimitMillis = arguments.next().toLong()
    val candidateCap = arguments.next().toInt()
    require(seed in source.manifest.seeds) { "Seed is outside the frozen source manifest" }
    require(budgetMillis > 0 && solverLimitMillis in 1..budgetMillis && candidateCap > 0)
    require(System.getenv("ETS_FRONTEND_SCRIPT") == null) { "ETS_FRONTEND_SCRIPT must be unset" }

    val identity = CallsCoverageCellIdentity(
        schemaVersion = 2,
        toolRevision = runtimeToolRevision,
        nativeFrontendRevision = runtimeNativeFrontendRevision,
        sourceManifest = source.manifestPath.toString(),
        sourceIdentity = source.identity,
        projectIndex = projectIndex,
        functionIndex = functionIndex,
        profile = profile,
        seed = seed,
        budgetMillis = budgetMillis,
        solverLimitMillis = solverLimitMillis,
        candidateCap = candidateCap,
    )

    return CoverageCellRequest(source = source, identity = identity, output = output)
}

private fun loadOrRunCoverageSearch(
    source: CallsCoverageSource,
    identity: CallsCoverageCellIdentity,
    output: Path,
    searchFunction: (CallsCoverageSearchRequest) -> CallsCoverageSearchResult,
): CallsCoverageSearchResult {
    val candidatePath = output.resolve("candidates.jsonl")
    val searchPath = output.resolve("search.json")

    return if (Files.exists(searchPath)) {
        CallsExperimentJson.json.decodeFromString<CallsCoverageSearchResult>(Files.readString(searchPath))
    } else {
        require(!Files.exists(candidatePath)) { "Interrupted search requires investigation before restart" }
        source.verifyUnchanged()
        val request = CallsCoverageSearchRequest(
            sourceRoot = source.sourceRoot,
            project = source.project,
            function = source.function,
            profile = identity.profile,
            frozenModelIds = source.manifest.modelSet.ids,
            expectedNativeFrontendRevision = identity.nativeFrontendRevision,
            seed = identity.seed,
            budget = identity.budgetMillis.milliseconds,
            solverQueryLimit = identity.solverLimitMillis.milliseconds,
            candidateCap = identity.candidateCap,
            onCandidate = { candidate -> appendDurably(candidatePath, encode(candidate)) },
        )
        val result = searchFunction(request)
        source.verifyUnchanged()
        val persistedCandidates = readLines<CallsCoverageCandidate>(candidatePath)
        require(result.candidates == persistedCandidates) { "Persisted candidate journal differs from search result" }
        atomicWrite(searchPath, encode(result))
        result
    }
}

private fun replayCoverageCandidates(
    source: CallsCoverageSource,
    search: CallsCoverageSearchResult,
    output: Path,
): List<CallsCoverageReplayEvent> {
    val replayPath = output.resolve("replays.jsonl")
    val events = readLines<CallsCoverageReplayEvent>(replayPath).toMutableList()
    require(events.map(CallsCoverageReplayEvent::index) == events.indices.toList()) {
        "Replay journal has missing or duplicate indices"
    }
    source.verifyUnchanged()
    if (search.candidates.isNotEmpty()) {
        OriginalTypeScriptCoverageReplayer(
            sourceRoot = source.sourceRoot,
            function = source.function,
        ).use { replayer ->
            search.candidates.indices.drop(events.size).forEach { index ->
                val candidate = search.candidates[index]
                source.verifyUnchanged()
                val replay = runCatching {
                    replayer.replay(inputs = candidate.inputs, timeoutMillis = REPLAY_TIMEOUT_MILLIS)
                }
                source.verifyUnchanged()
                val event = CallsCoverageReplayEvent(
                    index = index,
                    replay = replay.getOrNull(),
                    error = replay.exceptionOrNull()?.let { error -> error.message ?: error::class.java.name },
                )
                appendDurably(replayPath, encode(event))
                events += event
            }
        }
    }

    return events
}

private fun coverageCheckpoints(
    search: CallsCoverageSearchResult,
    events: List<CallsCoverageReplayEvent>,
    budgetMillis: Long,
): List<CallsCoverageCheckpoint> = COVERAGE_CHECKPOINT_MILLIS.filter { it <= budgetMillis }.map { checkpoint ->
    val indices = search.candidates.indices.filter { index ->
        search.candidates[index].emittedAtMillis <= checkpoint
    }
    val replayed = indices.mapNotNull { index -> events[index].replay }

    CallsCoverageCheckpoint(
        searchBudgetMillis = checkpoint,
        emittedCandidates = indices.size,
        replayedCandidates = replayed.size,
        coveredStatements = replayed.flatMap { it.coveredStatementKeys }.toSet().size,
        coveredIfArms = replayed.flatMap { it.coveredIfArmKeys }.toSet().size,
    )
}

private inline fun <reified T> readLines(path: Path): List<T> = if (Files.exists(path)) {
    Files.readAllLines(path).filter(String::isNotBlank).map { line ->
        CallsExperimentJson.json.decodeFromString<T>(line)
    }
} else {
    emptyList()
}

private inline fun <reified T> encode(value: T): String = CallsExperimentJson.json.encodeToUtf8SafeString(value)

private fun appendDurably(path: Path, line: String) {
    val bytes = ByteBuffer.wrap((line + "\n").toByteArray(Charsets.UTF_8))
    FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND,
    ).use { channel ->
        while (bytes.hasRemaining()) channel.write(bytes)
        channel.force(true)
    }
}

private fun atomicWrite(path: Path, contents: String) {
    val temporary = path.resolveSibling("${path.fileName}.tmp")
    Files.writeString(temporary, contents + "\n")
    FileChannel.open(temporary, StandardOpenOption.READ).use { it.force(true) }
    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
}

private const val COVERAGE_CELL_ARGUMENT_COUNT = 9
private const val REPLAY_TIMEOUT_MILLIS = 20_000L
private const val FIRST_CHECKPOINT_MILLIS = 30_000L
private const val SECOND_CHECKPOINT_MILLIS = 60_000L
private const val THIRD_CHECKPOINT_MILLIS = 120_000L
private val COVERAGE_CHECKPOINT_MILLIS = listOf(
    FIRST_CHECKPOINT_MILLIS,
    SECOND_CHECKPOINT_MILLIS,
    THIRD_CHECKPOINT_MILLIS,
)
