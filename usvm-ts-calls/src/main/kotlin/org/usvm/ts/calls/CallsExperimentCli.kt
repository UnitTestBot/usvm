package org.usvm.ts.calls

import kotlinx.serialization.json.Json
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { usage() }

    when (args.first()) {
        "class-catalog" -> runClassCatalog(args.drop(1))
        "class-start-smoke" -> runClassStartSmoke(args.drop(1))
        "class-symbolic-probe" -> runClassSymbolicProbe(args.drop(1))
        "class-replay-input" -> runSavedInputReplay(args.drop(1))
        "class-prepare" -> runClassPrepare(args.drop(1))
        "class-preflight" -> runClassPreflight(args.drop(1))
        "class-cell" -> runClassCell(args.drop(1))
        "run" -> runExperiment(args.drop(1))
        "coverage-universe" -> runCoverageUniverse(args.drop(1))
        "coverage-cell" -> runCoverageCell(args.drop(1))
        "replay-witness" -> replayWitness(args.drop(1))
        "summarize" -> summarize(args.drop(1))
        "frontend-identity" -> System.out.appendLine(frontendIdentity(args.drop(1)))
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

    val encoded = CallsExperimentJson.json.encodeToUtf8SafeString(result)
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
    val outputIsRawInput = rawInput == output || (Files.exists(output) && Files.isSameFile(rawInput, output))
    require(!outputIsRawInput) { "Summary output must differ from raw input" }

    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }
    output.parent?.let(Files::createDirectories)
    Files.writeString(output, json.encodeToUtf8SafeString(summary) + "\n")
}

internal fun frontendIdentity(args: List<String>): String {
    require(args.size == 1) { usage() }
    val frontendDirectory = Path.of(args.single()).toRealPath()
    return callsExternalFrontendIdentity(frontendDirectory)
}

private fun usage(): String = """
    Usage:
      calls class-catalog
      calls class-start-smoke <startup-manifest.json> <class-id> <output-directory>
      calls class-symbolic-probe <startup-manifest.json> <class-id> <output-directory>
      calls class-replay-input <startup-manifest.json> <class-id> <probe-directory> <state-id> <timeout-ms> <output.json>
      calls class-prepare <class-manifest.json> <prepared-directory>
      calls class-preflight <prepared-directory> <class-id> <preflight-ms>
      calls class-cell <prepared-directory> <class-id> <profile> <seed> <budget-ms> <output-directory>
      calls run <frozen-manifest.json> <raw-directory>
      calls coverage-universe <source-manifest.json> <project-index> <function-index> <output-directory>
      calls coverage-cell <source-manifest.json> <project-index> <function-index> <profile> <seed> <output-directory> <budget-ms> <solver-limit-ms> <candidate-cap> <universe.json>
      calls replay-witness <frozen-manifest.json> <results.jsonl> <project-id> <function-id> <target-id> <profile> <seed>
      calls summarize <results.jsonl> <summary.json>
      calls frontend-identity <external-frontend-directory>
""".trimIndent()

private const val REPLAY_WITNESS_ARGUMENT_COUNT = 7
