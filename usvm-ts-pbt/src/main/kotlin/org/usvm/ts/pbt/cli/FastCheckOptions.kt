package org.usvm.ts.pbt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.model.PropertyId
import java.nio.file.Path

internal sealed interface CliParseResult {
    data class Success(val options: CliOptions) : CliParseResult

    data class Help(val text: String) : CliParseResult
}

internal data class CliOptions(
    val sourceRoots: List<Path>,
    val registryIds: List<String>,
    val propertyId: PropertyId?,
    val seed: Int?,
    val replayPath: String?,
    val numRuns: Int,
    val timeoutMillis: Long,
    val examplesFile: Path?,
    val coverageRequest: PropertyCoverageRequest?,
)

internal class CliUsageException(
    val code: String,
    message: String,
    val path: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun parseCliOptions(args: Array<String>): CliParseResult {
    val parser = FastCheckOptionsParser()

    return try {
        parser.parse(args)

        CliParseResult.Success(parser.options)
    } catch (help: PrintHelpMessage) {
        CliParseResult.Help(parser.getFormattedHelp(help).orEmpty())
    } catch (error: CliktError) {
        throw CliUsageException(
            code = PbtDiagnosticCode.CLI_ARGUMENT_INVALID,
            message = error.message ?: "Invalid command line arguments",
            cause = error,
        )
    }
}

private class FastCheckOptionsParser : CliktCommand(name = "usvm-ts-pbt") {
    private val sourceRoots by option(
        "--source-root",
        help = "TypeScript source root; repeat for multiple roots",
    ).path().multiple()

    private val registryIds by option(
        "--registry",
        help = "Property registry ID; defaults to all registries",
    ).multiple()

    private val propertyIdText by option(
        "--property",
        help = "Property ID; defaults to every selected property",
    )

    private val seed by option(
        "--seed",
        help = "Signed fast-check seed",
    ).int()

    private val replayPath by option(
        "--path",
        help = "Replay path returned by an earlier failure",
    )

    private val numRuns by option(
        "--num-runs",
        help = "Number of successful runs",
    ).int().default(PropertyRunConfiguration.DEFAULT_NUM_RUNS)

    private val timeoutMillis by option(
        "--timeout-ms",
        help = "Property timeout in milliseconds",
    ).long().default(PropertyRunConfiguration.DEFAULT_TIMEOUT_MILLIS)

    private val examplesFile by option(
        "--examples",
        help = "JSON file with positional tagged examples",
    ).path()

    private val coverageEnabled by option(
        "--coverage",
        help = "Collect source-mapped coverage for every property run",
    ).flag()

    private val coverageScopes by option(
        "--coverage-scope",
        help = "Coverage scope; repeat for multiple scopes",
    ).multiple()

    private val coverageIncludePatterns by option(
        "--coverage-include",
        help = "Include glob over remapped source paths; repeat for multiple patterns",
    ).multiple()

    private val coverageExcludePatterns by option(
        "--coverage-exclude",
        help = "Exclude glob over remapped source paths; repeat for multiple patterns",
    ).multiple()

    lateinit var options: CliOptions
        private set

    override fun run() {
        requireSourceRoots()
        requirePositiveRunControls()
        requireCoverageFlag()

        options = CliOptions(
            sourceRoots = sourceRoots,
            registryIds = registryIds,
            propertyId = propertyIdText?.let(::parsePropertyId),
            seed = seed,
            replayPath = replayPath,
            numRuns = numRuns,
            timeoutMillis = timeoutMillis,
            examplesFile = examplesFile,
            coverageRequest = buildCoverageRequest(),
        )
    }

    private fun requireSourceRoots() {
        if (sourceRoots.isEmpty()) {
            throw CliUsageException(
                code = PbtDiagnosticCode.CLI_SOURCE_ROOT_REQUIRED,
                message = "At least one --source-root is required",
                path = "sourceRoot",
            )
        }
    }

    private fun requirePositiveRunControls() {
        if (numRuns <= 0) {
            throw CliUsageException(
                code = PbtDiagnosticCode.CLI_NUM_RUNS_INVALID,
                message = "--num-runs must be positive",
                path = "numRuns",
            )
        }

        if (timeoutMillis !in 1..PropertyRunConfiguration.MAX_TIMEOUT_MILLIS) {
            throw CliUsageException(
                code = PbtDiagnosticCode.CLI_TIMEOUT_INVALID,
                message = "--timeout-ms must be in 1..${PropertyRunConfiguration.MAX_TIMEOUT_MILLIS}",
                path = "timeoutMillis",
            )
        }
    }

    private fun requireCoverageFlag() {
        val hasCoverageDetails = coverageScopes.isNotEmpty() ||
            coverageIncludePatterns.isNotEmpty() ||
            coverageExcludePatterns.isNotEmpty()
        if (!coverageEnabled && hasCoverageDetails) {
            throw CliUsageException(
                code = "cli.coverage.required",
                message = "Coverage scope and path rules require --coverage",
                path = "coverage",
            )
        }
    }

    private fun buildCoverageRequest(): PropertyCoverageRequest? {
        if (!coverageEnabled) return null

        return PropertyCoverageRequest(
            scopes = coverageScopes
                .map(::parseCoverageScope)
                .toSet()
                .ifEmpty { setOf(CoverageScope.SOURCE_UNDER_TEST) },
            includePatterns = coverageIncludePatterns,
            excludePatterns = coverageExcludePatterns,
        )
    }
}

private fun parseCoverageScope(value: String): CoverageScope = when (value) {
    "source-under-test" -> CoverageScope.SOURCE_UNDER_TEST
    "property-entry-points" -> CoverageScope.PROPERTY_ENTRY_POINTS
    "generated-backend-wrappers" -> CoverageScope.GENERATED_BACKEND_WRAPPERS
    "dependencies" -> CoverageScope.DEPENDENCIES
    else -> throw CliUsageException(
        code = "cli.coverage.scope.invalid",
        message = "Unknown coverage scope $value",
        path = "coverageScope",
    )
}

private fun parsePropertyId(value: String): PropertyId = try {
    PropertyId(value)
} catch (error: IllegalArgumentException) {
    throw CliUsageException(
        code = PbtDiagnosticCode.CLI_PROPERTY_INVALID,
        message = error.message.orEmpty(),
        path = "property",
        cause = error,
    )
}
