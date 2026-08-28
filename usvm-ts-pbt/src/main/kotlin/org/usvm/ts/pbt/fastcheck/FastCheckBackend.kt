package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageCollectorIdentity
import org.usvm.ts.pbt.backend.PropertyBasedTestingBackend
import org.usvm.ts.pbt.backend.PropertyCoverageCapability
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.coverage.C8_COLLECTOR_VERSION
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
    override val coverageCapability: PropertyCoverageCapability = PropertyCoverageCapability.supported(
        backendId = FAST_CHECK_BACKEND_ID,
        backendVersion = FAST_CHECK_BACKEND_VERSION,
        collector = CoverageCollectorIdentity(
            id = "c8",
            version = C8_COLLECTOR_VERSION,
        ),
    )

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
                coverageRequest = configuration.coverageRequest,
            ),
        )
    }

    private fun validateConfiguration(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ) {
        validateExamples(property, configuration)
    }

    private fun validateExamples(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ) {
        configuration.examples.forEachIndexed { index, example ->
            if (example.size != property.inputs.size) {
                throw invalidRequest(
                    code = PbtDiagnosticCode.BACKEND_EXAMPLES_ARITY,
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
                        code = PbtDiagnosticCode.BACKEND_EXAMPLES_DOMAIN,
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
                code = PbtDiagnosticCode.BACKEND_EXAMPLES_VALUE_INVALID,
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
        const val FAST_CHECK_BACKEND_ID = "fast-check"
        const val FAST_CHECK_BACKEND_VERSION = "4.9.0"

        private val FINITE_NUMBER_BITS_REGEX = Regex("[0-9a-f]{16}")

        private fun canonicalizeSourceRoots(sourceRoots: List<Path>): List<Path> {
            if (sourceRoots.isEmpty()) {
                throw PbtBackendException(
                    kind = BackendErrorKind.INVALID_REQUEST,
                    code = PbtDiagnosticCode.SOURCE_ROOT_INVALID,
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
                        code = PbtDiagnosticCode.SOURCE_ROOT_INVALID,
                        message = "TypeScript source root is not a directory: $sourceRoot",
                        path = "sourceRoots[$index]",
                    )
                }
            }
        } catch (error: IOException) {
            throw PbtBackendException(
                kind = BackendErrorKind.INVALID_REQUEST,
                code = PbtDiagnosticCode.SOURCE_ROOT_INVALID,
                message = "Cannot resolve TypeScript source root $sourceRoot: ${error.message}",
                path = "sourceRoots[$index]",
                cause = error,
            )
        }
    }
}
