package org.usvm.ts.calls

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { usage() }

    when (args.first()) {
        "run" -> runExperiment(args.drop(1))
        "replay-witness" -> replayWitness(args.drop(1))
        "summarize" -> summarize(args.drop(1))
        else -> error(usage())
    }
}

internal fun replayWitness(args: List<String>) {
    require(args.size == REPLAY_WITNESS_ARGUMENT_COUNT) { usage() }
    val arguments = args.iterator()
    val manifestArgument = arguments.next()
    val rawArgument = arguments.next()
    val projectId = arguments.next()
    val functionId = arguments.next()
    val targetId = arguments.next()
    val profileName = arguments.next()
    val seedText = arguments.next()
    val manifestPath = Path.of(manifestArgument).toAbsolutePath().normalize()
    val rawInput = Path.of(rawArgument).toAbsolutePath().normalize()
    val selector = CallsWitnessSelector(
        projectId = projectId,
        functionId = functionId,
        targetId = targetId,
        profile = CallsExperimentProfile.valueOf(profileName),
        seed = seedText.toLong(),
    )
    preflightCallsWitness(rawInput = rawInput, selector = selector)

    val manifest = CallsExperimentJson.decodeManifest(Files.readString(manifestPath))
    val result = CallsWitnessReplayer(
        targetReplayer = OriginalTypeScriptTargetReplayer(),
    ).replay(
        manifest = manifest,
        manifestDirectory = requireNotNull(manifestPath.parent),
        rawInput = rawInput,
        selector = selector,
    )

    val encoded = CallsExperimentJson.json.encodeToString(result)
    System.out.appendLine(encoded)
}

private fun runExperiment(args: List<String>) {
    require(args.size == 2) { usage() }
    val manifestPath = Path.of(args[0]).toAbsolutePath().normalize()
    val rawDirectory = Path.of(args[1]).toAbsolutePath().normalize()
    val manifest = CallsExperimentJson.decodeManifest(Files.readString(manifestPath))

    CallsExperimentRunner(
        symbolicEngine = CurrentTsCallsSymbolicEngine(),
        targetReplayer = OriginalTypeScriptTargetReplayer(),
    ).run(
        manifest = manifest,
        manifestDirectory = requireNotNull(manifestPath.parent),
        rawOutput = rawDirectory.resolve("results.jsonl"),
    )
    Files.writeString(
        rawDirectory.resolve("manifest.json"),
        CallsExperimentJson.encodeManifest(manifest) + "\n",
    )
}

private fun summarize(args: List<String>) {
    require(args.size == 2) { usage() }
    val rawInput = Path.of(args[0]).toAbsolutePath().normalize()
    val output = Path.of(args[1]).toAbsolutePath().normalize()
    val summary = CallsExperimentAggregator.summarize(rawInput)
    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }
    output.parent?.let(Files::createDirectories)
    Files.writeString(output, json.encodeToString(summary) + "\n")
}

private fun usage(): String = """
    Usage:
      calls run <frozen-manifest.json> <raw-directory>
      calls replay-witness <frozen-manifest.json> <results.jsonl> <project-id> <function-id> <target-id> <profile> <seed>
      calls summarize <results.jsonl> <summary.json>
""".trimIndent()

private const val REPLAY_WITNESS_ARGUMENT_COUNT = 7
