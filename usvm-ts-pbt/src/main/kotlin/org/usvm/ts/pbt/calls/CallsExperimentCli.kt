package org.usvm.ts.pbt.calls

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { usage() }

    when (args.first()) {
        "run" -> runExperiment(args.drop(1))
        "summarize" -> summarize(args.drop(1))
        else -> error(usage())
    }
}

private fun runExperiment(args: List<String>) {
    require(args.size == 2) { usage() }
    val manifestPath = Path.of(args[0]).toAbsolutePath().normalize()
    val rawDirectory = Path.of(args[1]).toAbsolutePath().normalize()
    val manifest = CallsExperimentJson.decodeManifest(Files.readString(manifestPath))
    Files.createDirectories(rawDirectory)
    Files.writeString(
        rawDirectory.resolve("manifest.json"),
        CallsExperimentJson.encodeManifest(manifest) + "\n",
    )

    CallsExperimentRunner(
        symbolicEngine = CurrentTsCallsSymbolicEngine(),
        targetReplayer = OriginalTypeScriptTargetReplayer(),
    ).run(
        manifest = manifest,
        manifestDirectory = requireNotNull(manifestPath.parent),
        rawOutput = rawDirectory.resolve("results.jsonl"),
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
      calls summarize <results.jsonl> <summary.json>
""".trimIndent()
