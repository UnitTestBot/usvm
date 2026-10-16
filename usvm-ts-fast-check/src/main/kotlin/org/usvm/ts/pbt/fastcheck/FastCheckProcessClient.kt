package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationValueStatus
import java.nio.file.Path

/** Encodes one execution request and validates the private fast-check adapter response. */
internal class FastCheckProcessClient(
    private val nodeExecutable: String = "node",
    private val adapterEntryPoint: Path,
    private val transportGraceMillis: Long = DEFAULT_TRANSPORT_GRACE_MILLIS,
    private val shutdownGraceMillis: Long = DEFAULT_SHUTDOWN_GRACE_MILLIS,
) {
    private val transport = FastCheckProcessTransport(
        nodeExecutable = nodeExecutable,
        maxRequestBytes = MAX_REQUEST_BYTES,
        maxStdoutBytes = MAX_STDOUT_BYTES,
        maxStderrBytes = MAX_STDERR_BYTES,
        shutdownGraceMillis = shutdownGraceMillis,
    )

    /** Executes one request and exposes only a fully validated common result. */
    fun check(
        request: FastCheckExecutionRequest,
        remainingMillis: (() -> Long)? = null,
    ): PropertyRunResult {
        val coverageSession = request.coverageRequest?.let {
            if (remainingMillis == null) {
                FastCheckCoverageSession.prepare(
                    nodeExecutable = nodeExecutable,
                    adapterEntryPoint = adapterEntryPoint,
                    request = request,
                )
            } else {
                val prepared = FastCheckCoverageSession.prepareWithinBudget(
                    nodeExecutable = nodeExecutable,
                    adapterEntryPoint = adapterEntryPoint,
                    request = request,
                    remainingMillis = remainingMillis,
                )
                when (prepared) {
                    is FastCheckOperationResult.Completed -> prepared.value
                    is FastCheckOperationResult.TimedOut -> throw deadlineExpired(request, prepared.message)
                }
            }
        }

        try {
            val phaseLimit = saturatedAdd(request.timeoutMillis, transportGraceMillis)
            val processBudget = remainingMillis?.invoke()?.coerceAtMost(phaseLimit) ?: phaseLimit
            if (processBudget <= 0) throw deadlineExpired(request, "Property campaign deadline expired")

            // The existing transport grace must fit inside a caller-owned deadline, including startup and response I/O.
            val generationRequest = if (remainingMillis == null) {
                request
            } else {
                val generationBudgetMillis = (processBudget - transportGraceMillis).coerceAtLeast(minimumValue = 1)
                request.copy(
                    timeoutMillis = minOf(request.timeoutMillis, processBudget),
                    generationTimeoutMillis = minOf(request.timeoutMillis, generationBudgetMillis),
                )
            }
            val output = invokeAdapter(
                encodedRequest = encodeRequest(generationRequest),
                request = request,
                command = coverageSession?.adapterCommand
                    ?: listOf(nodeExecutable, adapterEntryPoint.toString()),
                timeoutMillis = processBudget,
            )
            validateProcessOutput(output, request)
            val response = decodeResponse(output.stdout, request)
            val result = decodeSuccessfulResponse(response, request)

            if (coverageSession == null) return result
            if (remainingMillis == null) return coverageSession.attachTo(result)

            return when (val artifact = coverageSession.readArtifact()) {
                is FastCheckOperationResult.Completed -> result.copy(coverage = artifact.value)
                is FastCheckOperationResult.TimedOut -> result.copy(
                    coverage = coverageSession.incompleteArtifact(message = artifact.message),
                )
            }
        } finally {
            coverageSession?.close()
        }
    }

    private fun deadlineExpired(
        request: FastCheckExecutionRequest,
        message: String,
    ): PbtBackendException = backendError(
        kind = BackendErrorKind.TIMEOUT,
        code = FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
        message = message,
        request = request,
    )

    /** One external operation, using the same supervised transport and optional original-source collector. */
    fun witness(
        request: FastCheckExecutionRequest,
        remainingMillis: () -> Long,
    ): FastCheckOperationResult<FastCheckExecutionWireResponse> {
        val initialBudget = remainingMillis()
        if (initialBudget <= 0) {
            return FastCheckOperationResult.TimedOut(message = "External witness deadline expired")
        }

        val encodedRequest = encodeRequest(request)
        val coverageSession = request.coverageRequest?.let {
            val preparation = FastCheckCoverageSession.prepareWithinBudget(
                nodeExecutable = nodeExecutable,
                adapterEntryPoint = adapterEntryPoint,
                request = request,
                remainingMillis = remainingMillis,
            )
            when (preparation) {
                is FastCheckOperationResult.Completed -> preparation.value
                is FastCheckOperationResult.TimedOut -> return preparation
            }
        }

        try {
            val remaining = remainingMillis()
            if (remaining <= 0) {
                return FastCheckOperationResult.TimedOut(message = "External witness deadline expired")
            }

            val outputResult = invokeWitnessAdapter(
                encodedRequest = encodedRequest,
                request = request,
                command = coverageSession?.adapterCommand ?: listOf(nodeExecutable, adapterEntryPoint.toString()),
                timeoutMillis = remaining,
            )
            val output = when (outputResult) {
                is FastCheckOperationResult.Completed -> outputResult.value
                is FastCheckOperationResult.TimedOut -> return outputResult
            }
            validateProcessOutput(output, request)
            val response = decodeResponse(output.stdout, request)
            if (response.status == "error") throwNodeDiagnostic(response, request)

            val isReplayResponse = request.witness?.mode == "replay" && response.witnessReplay != null &&
                response.witnessReduction == null
            val isReductionResponse = request.witness?.mode == "reduce" && response.witnessReduction != null &&
                response.witnessReplay == null
            val validResponse = response.status == "ok" && response.result == null &&
                response.witnessPropertyId == request.manifest.propertyId &&
                response.witnessTarget == request.witness?.target && (isReplayResponse || isReductionResponse)
            if (!validResponse) throw invalidResponse(message = "Invalid witness response", request = request)

            val replay = response.witnessReplay
            if (replay != null && coverageSession != null) {
                val artifactResult = coverageSession.readArtifact()
                return when (artifactResult) {
                    is FastCheckOperationResult.Completed -> FastCheckOperationResult.Completed(
                        value = response.copy(witnessReplay = replay.copy(coverage = artifactResult.value)),
                    )
                    is FastCheckOperationResult.TimedOut -> artifactResult
                }
            }

            return FastCheckOperationResult.Completed(value = response)
        } finally {
            coverageSession?.close()
        }
    }

    private fun invokeWitnessAdapter(
        encodedRequest: String,
        request: FastCheckExecutionRequest,
        command: List<String>,
        timeoutMillis: Long,
    ): FastCheckOperationResult<FastCheckProcessOutput> = try {
        transport.invokeWithResult(
            command = command,
            request = encodedRequest,
            timeoutMillis = timeoutMillis,
            reportedTimeoutMillis = timeoutMillis,
            description = "fast-check adapter",
        )
    } catch (error: FastCheckTransportException) {
        throw backendError(
            kind = error.backendErrorKind(),
            code = error.code,
            message = error.message.orEmpty(),
            request = request,
            cause = error,
        )
    }

    private fun invokeAdapter(
        encodedRequest: String,
        request: FastCheckExecutionRequest,
        command: List<String>,
        timeoutMillis: Long = saturatedAdd(request.timeoutMillis, transportGraceMillis),
    ): FastCheckProcessOutput = try {
        transport.invoke(
            command = command,
            request = encodedRequest,
            timeoutMillis = timeoutMillis,
            reportedTimeoutMillis = request.timeoutMillis,
            description = "fast-check adapter",
        )
    } catch (error: FastCheckTransportException) {
        throw backendError(
            kind = error.backendErrorKind(),
            code = error.code,
            message = error.message.orEmpty(),
            request = request,
            cause = error,
        )
    }

    private fun encodeRequest(request: FastCheckExecutionRequest): String {
        val encodedRequest = PropertyManifestJson.json.encodeToUtf8SafeString(request)

        if (encodedRequest.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) {
            throw backendError(
                kind = BackendErrorKind.INVALID_REQUEST,
                code = FastCheckDiagnosticCode.BACKEND_REQUEST_TOO_LARGE,
                message = "fast-check request exceeds $MAX_REQUEST_BYTES bytes",
                request = request,
            )
        }

        return encodedRequest
    }

    private fun validateProcessOutput(
        output: FastCheckProcessOutput,
        request: FastCheckExecutionRequest,
    ) {
        if (output.exitCode != 0) {
            val detail = output.stderr.trim().ifEmpty { "no stderr" }

            throw backendError(
                kind = BackendErrorKind.PROCESS_FAILURE,
                code = FastCheckDiagnosticCode.BACKEND_PROCESS_FAILED,
                message = "fast-check adapter exited with code ${output.exitCode}: $detail",
                request = request,
            )
        }

        if (output.stdout.isBlank()) {
            throw backendError(
                kind = BackendErrorKind.PROTOCOL_ERROR,
                code = FastCheckDiagnosticCode.BACKEND_RESPONSE_EMPTY,
                message = "fast-check adapter returned an empty response",
                request = request,
            )
        }
    }

    private fun decodeResponse(
        stdout: String,
        request: FastCheckExecutionRequest,
    ): FastCheckExecutionWireResponse = try {
        PropertyManifestJson.json.decodeFromString(stdout)
    } catch (error: IllegalArgumentException) {
        throw backendError(
            kind = BackendErrorKind.PROTOCOL_ERROR,
            code = FastCheckDiagnosticCode.BACKEND_RESPONSE_INVALID,
            message = "fast-check adapter returned invalid JSON: ${error.message}",
            request = request,
            cause = error,
        )
    }

    private fun throwNodeDiagnostic(
        response: FastCheckExecutionWireResponse,
        request: FastCheckExecutionRequest,
    ): Nothing {
        val diagnostic = response.diagnostics.firstOrNull()
            ?: throw invalidResponse(
                message = "fast-check error response has no diagnostic",
                request = request,
            )

        throw backendError(
            kind = diagnostic.kind,
            code = diagnostic.code,
            message = diagnostic.message,
            request = request,
            path = diagnostic.path,
        )
    }

    private fun decodeSuccessfulResponse(
        response: FastCheckExecutionWireResponse,
        request: FastCheckExecutionRequest,
    ): PropertyRunResult {
        if (response.status == "error") throwNodeDiagnostic(response, request)

        if (response.status != "ok") {
            throw invalidResponse(
                message = "Unknown fast-check response status: ${response.status}",
                request = request,
            )
        }

        val result = response.result ?: throw invalidResponse(
            message = "Successful response has no result",
            request = request,
        )

        validateResultIdentity(result, request)
        validateInputExport(result, request)

        return result
    }

    private fun validateResultIdentity(
        result: PropertyRunResult,
        request: FastCheckExecutionRequest,
    ) {
        try {
            PropertyId(result.propertyId.value)
        } catch (error: IllegalArgumentException) {
            throw backendError(
                kind = BackendErrorKind.PROTOCOL_ERROR,
                code = FastCheckDiagnosticCode.BACKEND_RESPONSE_INVALID,
                message = "fast-check result property ID is invalid: ${error.message}",
                request = request,
                cause = error,
            )
        }

        if (result.propertyId.value != request.manifest.propertyId) {
            throw invalidResponse(
                message = "fast-check result property does not match the request",
                request = request,
            )
        }
    }

    private fun validateInputExport(result: PropertyRunResult, request: FastCheckExecutionRequest) {
        val controls = request.inputExportRequest ?: return
        val export = result.inputExport ?: throw invalidResponse(
            message = "Requested input export is absent from the response",
            request = request,
        )
        val unsupportedInputs = export.records.count { it.input.status != ObservationValueStatus.CAPTURED }
        val pendingInvocations = export.records.count {
            it.admission == ObservationAdmission.PENDING || it.outcome == ObservationOutcome.PENDING
        }
        val validInputs = export.records.all { record ->
            val input = record.input.value
            record.input.status != ObservationValueStatus.CAPTURED ||
                (input is JsConcreteValue.Array && input.elements.size == request.manifest.inputs.size)
        }
        val bytes = PropertyManifestJson.json.encodeToUtf8SafeString(export).toByteArray(Charsets.UTF_8).size
        val valid = export.verifiedSources == controls.sources && export.records.size <= controls.maxInvocations &&
            export.unsupportedInputs == unsupportedInputs && export.pendingInvocations == pendingInvocations &&
            validInputs && bytes <= controls.maxBytes
        if (!valid) {
            throw invalidResponse(
                message = "Input export violates its declared identity or capture bounds",
                request = request,
            )
        }
    }

    private fun invalidResponse(
        message: String,
        request: FastCheckExecutionRequest,
    ): PbtBackendException = backendError(
        kind = BackendErrorKind.PROTOCOL_ERROR,
        code = FastCheckDiagnosticCode.BACKEND_RESPONSE_INVALID,
        message = message,
        request = request,
    )

    private fun backendError(
        kind: BackendErrorKind,
        code: String,
        message: String,
        request: FastCheckExecutionRequest,
        path: String? = null,
        cause: Throwable? = null,
    ) = PbtBackendException(
        kind = kind,
        code = code,
        message = message,
        propertyId = request.manifest.propertyId,
        path = path,
        cause = cause,
    )

    private companion object {
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val MAX_STDOUT_BYTES = 4 * 1024 * 1024
        const val MAX_STDERR_BYTES = 64 * 1024
        const val DEFAULT_TRANSPORT_GRACE_MILLIS = 2_000L
        const val DEFAULT_SHUTDOWN_GRACE_MILLIS = 250L
    }
}

private fun FastCheckTransportException.backendErrorKind(): BackendErrorKind = when (code) {
    FastCheckDiagnosticCode.BACKEND_REQUEST_TOO_LARGE -> BackendErrorKind.INVALID_REQUEST
    FastCheckDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE -> BackendErrorKind.PROTOCOL_ERROR
    FastCheckDiagnosticCode.BACKEND_PROCESS_TIMEOUT -> BackendErrorKind.TIMEOUT
    else -> BackendErrorKind.PROCESS_FAILURE
}
