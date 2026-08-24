package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.backend.PropertyBasedTestingBackend
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumberKind
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.validation.requireValid
import org.usvm.ts.pbt.validation.validatePropertyDefinition
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Executes Kotlin-owned property definitions with fast-check over a private TypeScript bridge. */
class FastCheckBackend(
    sourceRoots: List<Path>,
    nodeExecutable: String = "node",
    adapterEntryPoint: Path = FastCheckRuntime.executionEntryPoint(),
) : PropertyBasedTestingBackend {
    private val sourceRoots = canonicalizeSourceRoots(sourceRoots)
    private val client = FastCheckProcessClient(
        nodeExecutable = nodeExecutable,
        adapterEntryPoint = adapterEntryPoint,
    )

    override fun run(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ): PropertyRunResult {
        requireValid(validatePropertyDefinition(property))
        validateConfiguration(property, configuration)

        return client.check(
            FastCheckExecutionRequest(
                manifest = property.toManifest(),
                sourceRoots = sourceRoots.map(Path::toString),
                seed = configuration.seed,
                replayPath = configuration.replayPath,
                numRuns = configuration.numRuns,
                timeoutMillis = configuration.timeoutMillis,
                examples = configuration.examples,
            ),
        )
    }

    private fun validateConfiguration(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ) {
        validateLimits(property, configuration)
        validateReplayPath(property, configuration.replayPath)
        validateExamples(property, configuration)
    }

    private fun validateLimits(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ) {
        if (configuration.numRuns > MAX_RUNS) {
            throw invalidRequest(
                code = "backend.num-runs.invalid",
                message = "Number of runs must be in 1..$MAX_RUNS",
                property = property,
                path = "numRuns",
            )
        }

        if (configuration.timeoutMillis > MAX_TIMEOUT_MILLIS) {
            throw invalidRequest(
                code = "backend.timeout.invalid",
                message = "Timeout must be in 1..$MAX_TIMEOUT_MILLIS milliseconds",
                property = property,
                path = "timeoutMillis",
            )
        }
    }

    private fun validateReplayPath(property: PropertyDefinition, replayPath: String?) {
        if (replayPath != null && replayPath.length > MAX_REPLAY_PATH_LENGTH) {
            throw invalidRequest(
                code = "backend.replay-path.invalid",
                message = "Replay path exceeds $MAX_REPLAY_PATH_LENGTH characters",
                property = property,
                path = "replayPath",
            )
        }
    }

    private fun validateExamples(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ) {
        configuration.examples.forEachIndexed { index, example ->
            if (example.size != property.inputs.size) {
                throw invalidRequest(
                    code = "backend.examples.arity",
                    message = "Explicit example $index has ${example.size} values, expected ${property.inputs.size}",
                    property = property,
                    path = "examples[$index]",
                )
            }

            example.forEachIndexed { valueIndex, value ->
                val path = "examples[$index][$valueIndex]"

                validateExampleValue(
                    property = property,
                    value = value,
                    path = path,
                )

                if (value !in property.inputs[valueIndex].domain) {
                    throw invalidRequest(
                        code = "backend.examples.domain",
                        message = "Explicit example does not belong to the declared input domain",
                        property = property,
                        path = path,
                    )
                }
            }
        }
    }

    private fun validateExampleValue(
        property: PropertyDefinition,
        value: JsConcreteValue,
        path: String,
    ) {
        if (value is JsConcreteValue.Number && !hasValidEncoding(value)) {
            throw invalidRequest(
                code = "backend.examples.value.invalid",
                message = "Explicit example contains an invalid tagged JavaScript number",
                property = property,
                path = path,
            )
        }

        if (value is JsConcreteValue.Array) {
            value.elements.forEachIndexed { index, element ->
                validateExampleValue(
                    property = property,
                    value = element,
                    path = "$path.elements[$index]",
                )
            }
        }
    }

    private fun hasValidEncoding(value: JsConcreteValue.Number): Boolean = when (value.number.value) {
        JsNumberKind.FINITE -> value.number.bits?.matches(FINITE_NUMBER_BITS_REGEX) == true
        else -> value.number.bits == null
    }

    private fun invalidRequest(
        code: String,
        message: String,
        property: PropertyDefinition,
        path: String,
    ) = PbtBackendException(
        kind = BackendErrorKind.INVALID_REQUEST,
        code = code,
        message = message,
        propertyId = property.id.value,
        path = path,
    )

    companion object {
        private const val MAX_RUNS = 10_000
        private const val MAX_TIMEOUT_MILLIS = 86_400_000L
        private const val MAX_REPLAY_PATH_LENGTH = 4_096
        private val FINITE_NUMBER_BITS_REGEX = Regex("[0-9a-f]{16}")

        private fun canonicalizeSourceRoots(sourceRoots: List<Path>): List<Path> {
            if (sourceRoots.isEmpty()) {
                throw PbtBackendException(
                    kind = BackendErrorKind.INVALID_REQUEST,
                    code = "source-root.invalid",
                    message = "At least one TypeScript source root is required",
                    path = "sourceRoots",
                )
            }

            return sourceRoots.mapIndexed(::canonicalizeSourceRoot).distinct()
        }

        private fun canonicalizeSourceRoot(index: Int, sourceRoot: Path): Path = try {
            sourceRoot.toRealPath().also { realPath ->
                if (!Files.isDirectory(realPath)) {
                    throw PbtBackendException(
                        kind = BackendErrorKind.INVALID_REQUEST,
                        code = "source-root.invalid",
                        message = "TypeScript source root is not a directory: $sourceRoot",
                        path = "sourceRoots[$index]",
                    )
                }
            }
        } catch (error: IOException) {
            throw PbtBackendException(
                kind = BackendErrorKind.INVALID_REQUEST,
                code = "source-root.invalid",
                message = "Cannot resolve TypeScript source root $sourceRoot: ${error.message}",
                path = "sourceRoots[$index]",
                cause = error,
            )
        }
    }
}
