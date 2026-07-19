package org.usvm.ts.pbt.replay

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.usvm.ts.pbt.external.ExternalTestCase
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.system.exitProcess

/** Service-provider integration point for A-INT scene loading. */
interface ReplayRuntimeProvider {
    val id: String
    fun create(input: ValidatedReplayInput): ReplayRuntime
}

@Serializable
private data class ReplayCliSummary(
    val valid: Boolean,
    val executorId: String,
    val productionExecutor: Boolean,
    val importedCases: Int,
    val fixedBudgetConfirmedEdges: Int,
    val denominatorEdges: Int,
    val outputDirectory: String,
)

object ReplayCli {
    private val json = Json { encodeDefaults = true }

    fun run(
        args: Array<String>,
        stdout: Appendable = System.out,
        stderr: Appendable = System.err,
        providers: List<ReplayRuntimeProvider> = ServiceLoader.load(ReplayRuntimeProvider::class.java).toList(),
    ): Int {
        if (args.isEmpty()) return usage(stderr)
        return try {
            when (args[0]) {
                "run" -> runReplay(args.drop(1), providers, stdout, stderr)
                "validate-output" -> validateOutput(args.drop(1), stdout, stderr)
                "help", "--help", "-h" -> usage(stdout, success = true)
                else -> usage(stderr, "unknown command '${args[0]}'")
            }
        } catch (cause: ReplayInputException) {
            cause.issues.forEach { issue ->
                stderr.appendLine("${issue.artifact}${issue.path}: ${issue.code}: ${issue.message}")
            }
            INVALID_ARTIFACT_EXIT_CODE
        } catch (cause: Exception) {
            stderr.appendLine(cause.message?.lineSequence()?.firstOrNull() ?: cause.toString())
            FAILURE_EXIT_CODE
        }
    }

    private fun runReplay(
        arguments: List<String>,
        providers: List<ReplayRuntimeProvider>,
        stdout: Appendable,
        stderr: Appendable,
    ): Int {
        val parsed = parseOptions(arguments, stderr) ?: return USAGE_EXIT_CODE
        val allowed = setOf(
            "raw-run",
            "run-config",
            "target-manifest",
            "source-targets",
            "method-ids",
            "out-dir",
            "executor",
            "allow-fixture-executor",
        )
        val unknown = parsed.keys - allowed
        if (unknown.isNotEmpty()) return usage(stderr, "unknown options: ${unknown.sorted().joinToString { "--$it" }}")
        val required = listOf(
            "raw-run",
            "run-config",
            "target-manifest",
            "source-targets",
            "method-ids",
            "out-dir",
            "executor",
        )
        val missing = required.filterNot(parsed::containsKey)
        if (missing.isNotEmpty()) return usage(stderr, "missing options: ${missing.joinToString { "--$it" }}")

        val executorId = parsed.getValue("executor")
        val provider = when (executorId) {
            FixtureMetadataReplayExecutor.ID -> {
                if (parsed["allow-fixture-executor"] != "true") {
                    return usage(
                        stderr,
                        "fixture-metadata is test-only; pass --allow-fixture-executor to acknowledge " +
                            "that it is not EtsIR replay",
                    )
                }
                object : ReplayRuntimeProvider {
                    override val id: String = FixtureMetadataReplayExecutor.ID
                    override fun create(input: ValidatedReplayInput): ReplayRuntime =
                        ReplayRuntime(FixtureMetadataReplayExecutor)
                }
            }

            else -> {
                providers.singleOrNull { it.id == executorId }
                    ?: return usage(stderr, "no ReplayRuntimeProvider named '$executorId'")
            }
        }

        val inputs = ReplayInputs(
            rawRunDirectory = Path.of(parsed.getValue("raw-run")),
            runConfig = Path.of(parsed.getValue("run-config")),
            targetManifest = Path.of(parsed.getValue("target-manifest")),
            sourceTargets = Path.of(parsed.getValue("source-targets")),
            methodIds = Path.of(parsed.getValue("method-ids")),
            outputDirectory = Path.of(parsed.getValue("out-dir")),
        )
        val result = ReplayPipeline().run(inputs) { input -> provider.create(input) }
        stdout.appendLine(
            json.encodeToString(
                ReplayCliSummary(
                    valid = result.validationReport.valid,
                    executorId = result.deadlineReport.executorId,
                    productionExecutor = result.deadlineReport.productionExecutor,
                    importedCases = result.deadlineReport.importedCaseCount,
                    fixedBudgetConfirmedEdges = result.deadlineReport.fixedBudgetConfirmedEdgeCount,
                    denominatorEdges = result.deadlineReport.denominatorEdgeCount,
                    outputDirectory = inputs.outputDirectory.toAbsolutePath().normalize().toString(),
                ),
            ),
        )
        return SUCCESS_EXIT_CODE
    }

    private fun validateOutput(arguments: List<String>, stdout: Appendable, stderr: Appendable): Int {
        val parsed = parseOptions(arguments, stderr) ?: return USAGE_EXIT_CODE
        val unknown = parsed.keys - setOf("out-dir")
        if (unknown.isNotEmpty()) return usage(stderr, "unknown options: ${unknown.sorted().joinToString { "--$it" }}")
        val directory = parsed["out-dir"] ?: return usage(stderr, "validate-output requires --out-dir")
        val report = ReplayArtifactValidator.validateOutputDirectory(Path.of(directory))
        stdout.appendLine(json.encodeToString(report))
        return if (report.valid) SUCCESS_EXIT_CODE else INVALID_ARTIFACT_EXIT_CODE
    }

    private fun parseOptions(arguments: List<String>, stderr: Appendable): Map<String, String>? {
        val values = linkedMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val token = arguments[index]
            if (!token.startsWith("--")) {
                stderr.appendLine("unexpected positional argument '$token'")
                return null
            }
            val key = token.removePrefix("--")
            if (key == "allow-fixture-executor") {
                values[key] = "true"
                index++
                continue
            }
            if (index + 1 >= arguments.size || arguments[index + 1].startsWith("--")) {
                stderr.appendLine("option '$token' requires a value")
                return null
            }
            if (values.put(key, arguments[index + 1]) != null) {
                stderr.appendLine("option '$token' was supplied more than once")
                return null
            }
            index += 2
        }
        return values
    }

    private fun usage(output: Appendable, error: String? = null, success: Boolean = false): Int {
        if (error != null) output.appendLine(error)
        output.appendLine(
            "usage: replay run --raw-run <dir> --run-config <json> --target-manifest <json> " +
                "--source-targets <jsonl> --method-ids <txt> --out-dir <dir> --executor <provider-id>",
        )
        output.appendLine("       replay validate-output --out-dir <dir>")
        output.appendLine(
            "       replay run ... --executor fixture-metadata --allow-fixture-executor  # frozen tests only",
        )
        return if (success) SUCCESS_EXIT_CODE else USAGE_EXIT_CODE
    }

    private const val SUCCESS_EXIT_CODE: Int = 0
    private const val FAILURE_EXIT_CODE: Int = 1
    private const val INVALID_ARTIFACT_EXIT_CODE: Int = 2
    private const val USAGE_EXIT_CODE: Int = 64
}

/**
 * Frozen-fixture transport exerciser. It trusts metadata and is intentionally
 * marked non-production in deadline-report; it must never be used for paper
 * coverage. Metadata key: fixtureCoveredBranchIds, comma-separated.
 */
object FixtureMetadataReplayExecutor : ReplayCaseExecutor {
    const val ID: String = "fixture-metadata"
    override val id: String = ID
    override val isProduction: Boolean = false

    override fun execute(case: ExternalTestCase): ReplayCaseExecution {
        if (case.metadata["fixtureReject"] == "true") {
            return ReplayCaseExecution.Rejected(
                ReplayReasonCode.EXECUTOR_REJECTED,
                "fixture requested an explicit reject",
            )
        }
        val branches = case.metadata["fixtureCoveredBranchIds"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()
        return ReplayCaseExecution.Executed(branches, ReplayReasonCode.REPLAY_RETURNED)
    }
}

fun main(args: Array<String>) {
    exitProcess(ReplayCli.run(args))
}
