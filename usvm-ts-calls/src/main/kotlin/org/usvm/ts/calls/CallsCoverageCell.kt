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

internal fun runCoverageCell(args: List<String>) {
    require(args.size == 9) { "coverage-cell requires 9 arguments" }
    val manifestPath = Path.of(args[0]).toRealPath()
    val manifest = CallsExperimentJson.decodeManifest(Files.readString(manifestPath))
    val projectIndex = args[1].toInt()
    val functionIndex = args[2].toInt()
    val profile = CallsExperimentProfile.valueOf(args[3])
    val seed = args[4].toLong()
    val output = Path.of(args[5]).toAbsolutePath().normalize()
    val budgetMillis = args[6].toLong()
    val solverLimitMillis = args[7].toLong()
    val candidateCap = args[8].toInt()
    val project = manifest.projects[projectIndex]
    val function = project.functions[functionIndex]
    require(seed in manifest.seeds) { "Seed is outside the frozen source manifest" }
    require(budgetMillis > 0 && solverLimitMillis in 1..budgetMillis && candidateCap > 0)
    require(System.getenv("ETS_FRONTEND_SCRIPT") == null) { "ETS_FRONTEND_SCRIPT must be unset" }

    val identity = CallsCoverageCellIdentity(
        schemaVersion = 1,
        toolRevision = CallsBuildIdentity.toolRevision,
        nativeFrontendRevision = CallsBuildIdentity.nativeFrontendRevision,
        sourceManifest = manifestPath.toString(),
        projectIndex = projectIndex,
        functionIndex = functionIndex,
        profile = profile,
        seed = seed,
        budgetMillis = budgetMillis,
        solverLimitMillis = solverLimitMillis,
        candidateCap = candidateCap,
    )
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

    val candidatePath = output.resolve("candidates.jsonl")
    val searchPath = output.resolve("search.json")
    val search = if (Files.exists(searchPath)) {
        CallsExperimentJson.json.decodeFromString<CallsCoverageSearchResult>(Files.readString(searchPath))
    } else {
        require(!Files.exists(candidatePath)) { "Interrupted search requires investigation before restart" }
        val request = CallsCoverageSearchRequest(
            sourceRoot = manifestPath.parent.resolve(project.sourceRoot).toRealPath(),
            project = project,
            function = function,
            profile = profile,
            frozenModelIds = manifest.modelSet.ids,
            expectedNativeFrontendRevision = identity.nativeFrontendRevision,
            seed = seed,
            budget = budgetMillis.milliseconds,
            solverQueryLimit = solverLimitMillis.milliseconds,
            candidateCap = candidateCap,
            onCandidate = { candidate -> appendDurably(candidatePath, encode(candidate)) },
        )
        val result = CurrentTsCallsCoverageEngine().search(request)
        val persistedCandidates = readLines<CallsCoverageCandidate>(candidatePath)
        require(result.candidates == persistedCandidates) { "Persisted candidate journal differs from search result" }
        atomicWrite(searchPath, encode(result))
        result
    }

    val replayPath = output.resolve("replays.jsonl")
    val events = readLines<CallsCoverageReplayEvent>(replayPath).toMutableList()
    require(events.map(CallsCoverageReplayEvent::index) == events.indices.toList()) {
        "Replay journal has missing or duplicate indices"
    }
    if (search.candidates.isNotEmpty()) {
        OriginalTypeScriptCoverageReplayer(
            sourceRoot = manifestPath.parent.resolve(project.sourceRoot),
            function = function,
        ).use { replayer ->
            search.candidates.indices.drop(events.size).forEach { index ->
                val candidate = search.candidates[index]
                val replay = runCatching {
                    replayer.replay(inputs = candidate.inputs, timeoutMillis = REPLAY_TIMEOUT_MILLIS)
                }
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

    val checkpoints = listOf(30_000L, 60_000L, 120_000L).filter { it <= budgetMillis }.map { checkpoint ->
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
    val result = CallsCoverageCellResult(
        identity = identity,
        search = search,
        replayErrors = events.count { it.error != null },
        completionMismatches = events.indices.count { index ->
            events[index].replay?.completion != null &&
                events[index].replay?.completion != search.candidates[index].completion
        },
        checkpoints = checkpoints,
    )
    atomicWrite(output.resolve("result.json"), encode(result))
    println(encode(result))
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
    FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
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

private const val REPLAY_TIMEOUT_MILLIS = 20_000L
