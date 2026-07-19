package org.usvm.ts.pbt.external

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.system.exitProcess

/** Standalone entry point; Gradle/unified-launcher wiring is owned by A-INT. */
object ArtifactContractCli {
    private val json = Json { prettyPrint = false }

    fun run(
        args: Array<String>,
        stdout: Appendable = System.out,
        stderr: Appendable = System.err,
    ): Int {
        if (args.isEmpty()) return usage(stderr)
        return runCatching {
            when (args[0]) {
                "validate" -> validate(args, stdout, stderr)
                "convert-v1-etc" -> convertV1(args, stdout, stderr)
                "--help", "-h", "help" -> usage(stdout, success = true)
                else -> usage(stderr, "unknown command '${args[0]}'")
            }
        }.getOrElse { cause ->
            stderr.appendLine(cause.message?.lineSequence()?.firstOrNull() ?: cause.toString())
            1
        }
    }

    private fun validate(args: Array<String>, stdout: Appendable, stderr: Appendable): Int {
        if (args.size != 3) return usage(stderr, "validate requires <artifact> <path>")
        val path = Path.of(args[2])
        val report = when (args[1]) {
            "target-manifest" -> ArtifactValidator.validateTargetManifest(path)
            "source-targets" -> ArtifactValidator.validateSourceTargets(path)
            "method-ids" -> ArtifactValidator.validateMethodIds(path)
            "run-config" -> ArtifactValidator.validateRunConfig(path)
            "etc" -> ArtifactValidator.validateExternalTestCorpus(path)
            "native-coverage" -> ArtifactValidator.validateNativeCoverage(path)
            "run-meta" -> ArtifactValidator.validateRunMeta(path)
            "raw-run" -> ArtifactValidator.validateRawRunDirectory(path)
            else -> return usage(stderr, "unknown artifact '${args[1]}'")
        }
        stdout.appendLine(json.encodeToString(report))
        return if (report.valid) 0 else 2
    }

    private fun convertV1(args: Array<String>, stdout: Appendable, stderr: Appendable): Int {
        if (args.size != 3) return usage(stderr, "convert-v1-etc requires <input> <output>")
        val input = Path.of(args[1])
        val output = Path.of(args[2])
        ExternalTestCorpusV1Converter.convertFile(input, output)
        val report = ArtifactValidator.validateExternalTestCorpus(output)
        stdout.appendLine(json.encodeToString(report))
        return if (report.valid) 0 else 2
    }

    private fun usage(stderr: Appendable, error: String? = null, success: Boolean = false): Int {
        if (error != null) stderr.appendLine(error)
        stderr.appendLine(
            "usage: artifact-contract validate " +
                "<target-manifest|source-targets|method-ids|run-config|etc|native-coverage|run-meta|raw-run> <path>",
        )
        stderr.appendLine("       artifact-contract convert-v1-etc <input> <output>")
        return if (success) 0 else 64
    }
}

fun main(args: Array<String>) {
    exitProcess(ArtifactContractCli.run(args))
}
