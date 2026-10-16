package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.backend.ExternalWitnessBackend
import org.usvm.ts.pbt.backend.ExternalWitnessCandidate
import org.usvm.ts.pbt.backend.PropertyBasedTestingBackend
import org.usvm.ts.pbt.backend.PropertyCoverageCapability
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.WitnessEvidenceDelta
import org.usvm.ts.pbt.backend.WitnessNativeReplay
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.coverage.captureCoverageIdentity
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.accepts
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.observation.PropertyObservationRequest
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
    private val tsconfig: Path? = null,
) : PropertyBasedTestingBackend, ExternalWitnessBackend {
    override val coverageCapability: PropertyCoverageCapability = PropertyCoverageCapability.supported(
        backendId = FAST_CHECK_BACKEND_ID,
        backendVersion = FastCheckRuntimeMetadata.fastCheckVersion,
        collector = FastCheckRuntimeMetadata.coverageCollector,
    )

    private val sourceRoots = canonicalizeSourceRoots(sourceRoots)
    private val client = FastCheckProcessClient(
        nodeExecutable = nodeExecutable,
        adapterEntryPoint = adapterEntryPoint,
    )

    override fun run(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ): PropertyRunResult = execute(property, configuration, deadline = null)

    /** Charges preparation and the supervised process to the caller's existing deadline. */
    fun run(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
        deadline: WitnessReplayDeadline,
    ): PropertyRunResult = execute(property, configuration, deadline)

    private fun execute(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
        deadline: WitnessReplayDeadline?,
    ): PropertyRunResult {
        requireValid(validatePropertyDefinition(property))
        validateConfiguration(property, configuration)

        val manifest = property.toManifest()
        val admittedCoverage = configuration.coverageRequest?.admittedOnly == true
        val identity = if (admittedCoverage) captureCoverageIdentity(manifest, sourceRoots) else null
        val sourceRootPaths = sourceRoots.map(Path::toString)
        val request = FastCheckExecutionRequest(
            manifest = manifest,
            sourceRoots = sourceRootPaths,
            tsconfig = tsconfig?.toRealPath()?.toString(),
            seed = configuration.seed,
            replayPath = configuration.replayPath,
            numRuns = configuration.numRuns,
            timeoutMillis = configuration.timeoutMillis,
            examples = configuration.examples,
            observationRequest = configuration.observationRequest,
            coverageRequest = configuration.coverageRequest,
            admittedCoverage = admittedCoverage,
            exactReplay = configuration.exactReplay,
        )

        val result = client.check(request, remainingMillis = deadline?.let { it::remainingMillis })
        val coverage = result.coverage
        if (identity == null || coverage == null) return result

        val unchanged = identity == captureCoverageIdentity(manifest, sourceRoots)
        val diagnostics = if (unchanged) {
            coverage.diagnostics
        } else {
            coverage.diagnostics + CoverageDiagnostic(
                code = "coverage.source.stale",
                message = "TypeScript sources changed during the concrete run",
            )
        }
        return result.copy(coverage = coverage.copy(identity = identity, diagnostics = diagnostics))
    }

    override fun confirmWitness(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        configuration: WitnessReplayConfiguration,
        deadline: WitnessReplayDeadline,
    ): WitnessEvidenceDelta = FastCheckWitnessCoordinator(
        client = client,
        sourceRoots = sourceRoots,
        tsconfig = tsconfig?.toRealPath()?.toString(),
    ).confirm(
        property = property,
        candidate = candidate,
        configuration = configuration,
        deadline = deadline,
    )

    /** Native verification of a fixed candidate; this operation never starts PBT generation. */
    fun replayInput(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        coverageRequest: PropertyCoverageRequest,
        deadline: WitnessReplayDeadline,
    ): WitnessNativeReplay = FastCheckWitnessCoordinator(
        client = client,
        sourceRoots = sourceRoots,
        tsconfig = tsconfig?.toRealPath()?.toString(),
    ).replayInput(property, candidate, coverageRequest, deadline)

    internal fun confirmOriginalWitness(
        property: PropertyDefinition,
        candidate: ExternalWitnessCandidate,
        configuration: WitnessReplayConfiguration,
        deadline: WitnessReplayDeadline,
    ): WitnessEvidenceDelta = FastCheckWitnessCoordinator(
        client = client,
        sourceRoots = sourceRoots,
        tsconfig = tsconfig?.toRealPath()?.toString(),
    ).confirmOriginal(property, candidate, configuration, deadline)

    private fun validateConfiguration(
        property: PropertyDefinition,
        configuration: PropertyRunConfiguration,
    ) {
        validateExamples(property, configuration)
        configuration.observationRequest?.let { validateObservationRequest(property, it) }
    }

    private fun validateObservationRequest(property: PropertyDefinition, request: PropertyObservationRequest) {
        val validLimits = request.maxInvocations in 1..MAX_OBSERVED_INVOCATIONS &&
            request.maxPointsPerInvocation in 1..MAX_OBSERVED_POINTS &&
            request.maxArrayElements in 1..MAX_OBSERVED_ARRAY_ELEMENTS &&
            request.maxBytes in MIN_OBSERVED_BYTES..MAX_OBSERVED_BYTES
        val sourceByModule = request.sources.associateBy { it.module }
        val allSourcesPresent = property.predicate.module in sourceByModule &&
            request.points.all { point -> point.source.module in sourceByModule }
        val allPointsBound = request.points.all { point ->
            property.assertions.any { assertion ->
                assertion.id == point.assertionId && assertion.testedCall == point.callSite &&
                    assertion.operands.any { operand ->
                        operand.id == point.operandId && operand.source == point.source
                    }
            }
        }
        val allInputIndexesValid = request.points.all { point ->
            if (point.kind == org.usvm.ts.pbt.observation.ObservationPointKind.ARGUMENT) {
                point.inputIndex in property.inputs.indices
            } else {
                point.inputIndex == null
            }
        }
        val hashesValid = request.sources.all { source -> source.sha256.matches(SHA256_REGEX) }

        val validRequest = validLimits && request.points.isNotEmpty() && allSourcesPresent &&
            allPointsBound && allInputIndexesValid && hashesValid

        if (!validRequest) {
            throw invalidRequest(
                code = FastCheckDiagnosticCode.BACKEND_OBSERVATION_INVALID,
                message = "Observation request has invalid limits, source hashes, or assertion bindings",
                property = property,
                path = "observationRequest",
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
                    code = FastCheckDiagnosticCode.BACKEND_EXAMPLES_ARITY,
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
                        code = FastCheckDiagnosticCode.BACKEND_EXAMPLES_DOMAIN,
                        message = "Explicit example does not belong to the declared input domain",
                        property = property,
                        path = path,
                    )
                }
            }

            validateJointExample(property, example, index)
        }
    }

    private fun validateJointExample(property: PropertyDefinition, example: List<JsConcreteValue>, index: Int) {
        if (property.generator?.accepts(example) == false) {
            throw invalidRequest(
                code = FastCheckDiagnosticCode.BACKEND_EXAMPLES_DOMAIN,
                message = "Explicit example violates the declared joint generator support",
                property = property,
                path = "examples[$index]",
            )
        }
    }

    private fun validateExampleValue(
        property: PropertyDefinition,
        value: JsConcreteValue,
        path: String,
    ) {
        if (value is JsConcreteValue.Number && !value.number.hasValidEncoding()) {
            throw invalidRequest(
                code = FastCheckDiagnosticCode.BACKEND_EXAMPLES_VALUE_INVALID,
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

        private val SHA256_REGEX = Regex("[0-9a-f]{64}")
        private const val MAX_OBSERVED_INVOCATIONS = 64
        private const val MAX_OBSERVED_POINTS = 8
        private const val MAX_OBSERVED_ARRAY_ELEMENTS = 64
        private const val MAX_OBSERVED_BYTES = 65_536
        private const val MIN_OBSERVED_BYTES = 1024

        private fun canonicalizeSourceRoots(sourceRoots: List<Path>): List<Path> {
            if (sourceRoots.isEmpty()) {
                throw PbtBackendException(
                    kind = BackendErrorKind.INVALID_REQUEST,
                    code = FastCheckDiagnosticCode.SOURCE_ROOT_INVALID,
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
                        code = FastCheckDiagnosticCode.SOURCE_ROOT_INVALID,
                        message = "TypeScript source root is not a directory: $sourceRoot",
                        path = "sourceRoots[$index]",
                    )
                }
            }
        } catch (error: IOException) {
            throw PbtBackendException(
                kind = BackendErrorKind.INVALID_REQUEST,
                code = FastCheckDiagnosticCode.SOURCE_ROOT_INVALID,
                message = "Cannot resolve TypeScript source root $sourceRoot: ${error.message}",
                path = "sourceRoots[$index]",
                cause = error,
            )
        }
    }
}
