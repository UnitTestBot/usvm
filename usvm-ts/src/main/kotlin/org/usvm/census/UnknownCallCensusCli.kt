package org.usvm.census

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

internal class UnknownCallCensusCli(
    private val output: Appendable = System.out,
    private val errors: Appendable = System.err,
) {
    @Suppress("TooGenericExceptionCaught")
    fun run(args: Array<String>): Int = try {
        when (args.firstOrNull()) {
            "census" -> runCensus(parseOptions(args.drop(1), CENSUS_OPTIONS))
            "summarize" -> runSummarize(parseOptions(args.drop(1), SUMMARY_OPTIONS))
            "--help", "-h", null -> {
                output.appendLine(usage())
                EXIT_SUCCESS
            }

            else -> cliError("Unknown command '${args.first()}'")
        }
    } catch (error: CensusCliException) {
        errors.appendLine("unknown-call-census: ${error.message}")
        errors.appendLine("Run with --help for usage.")
        EXIT_ERROR
    } catch (error: SerializationException) {
        errors.appendLine("unknown-call-census: invalid JSON: ${error.message}")
        EXIT_ERROR
    } catch (error: Exception) {
        errors.appendLine("unknown-call-census: ${error.message ?: error::class.simpleName}")
        EXIT_ERROR
    }

    private fun runCensus(options: Map<String, String>): Int {
        val manifestPath = options.requiredPath("--manifest")
        val checkoutRoot = options.requiredPath("--checkout-root")
        val outputDirectory = options.requiredPath("--output")
        val manifest = censusJson.decodeFromString<UnknownCallCensusManifest>(
            Files.readString(manifestPath, StandardCharsets.UTF_8)
        )
        val summary = UnknownCallCensusRunner(
            manifest = manifest,
            manifestPath = manifestPath,
            checkoutRoot = checkoutRoot,
            outputDirectory = outputDirectory,
        ).run()

        output.appendLine(censusJson.encodeToString(summary))
        return EXIT_SUCCESS
    }

    private fun runSummarize(options: Map<String, String>): Int {
        val input = options.requiredPath("--input")
        val outputPath = options.requiredPath("--output")
        val summary = Files.newBufferedReader(input, StandardCharsets.UTF_8).useLines { lines ->
            UnknownCallCensusAggregator.summarize(lines)
        }
        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(
            outputPath,
            censusJson.encodeToString(summary) + System.lineSeparator(),
            StandardCharsets.UTF_8,
        )
        output.appendLine(censusJson.encodeToString(summary))

        return EXIT_SUCCESS
    }

    private fun parseOptions(args: List<String>, allowedOptions: Set<String>): Map<String, String> {
        val parsed = linkedMapOf<String, String>()
        var index = 0
        while (index < args.size) {
            val option = args[index]
            if (option !in allowedOptions) {
                cliError("Unknown option '$option'")
            }
            if (index + 1 >= args.size) {
                cliError("Missing value for '$option'")
            }
            if (parsed.put(option, args[index + 1]) != null) {
                cliError("Option '$option' was specified more than once")
            }
            index += 2
        }

        return parsed
    }

    private fun usage(): String = """
        Usage:
          unknown-call-census census --manifest <file> --checkout-root <dir> --output <dir>
          unknown-call-census summarize --input <raw.jsonl> --output <summary.json>

        The census command validates pinned Git revisions, writes append-only raw.jsonl records,
        and regenerates summary.json. The summarize command deterministically rebuilds a summary
        from an existing raw artifact.
    """.trimIndent()

    private companion object {
        val CENSUS_OPTIONS = setOf("--manifest", "--checkout-root", "--output")
        val SUMMARY_OPTIONS = setOf("--input", "--output")
        const val EXIT_SUCCESS = 0
        const val EXIT_ERROR = 1
    }
}

private class CensusCliException(message: String) : IllegalArgumentException(message)

private fun cliError(message: String): Nothing = throw CensusCliException(message)

private fun Map<String, String>.requiredPath(option: String): Path =
    get(option)?.let(Path::of) ?: cliError("Missing required option '$option'")

fun main(args: Array<String>) {
    exitProcess(UnknownCallCensusCli().run(args))
}
