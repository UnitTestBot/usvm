package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyId
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
    fun check(request: FastCheckExecutionRequest): PropertyRunResult {
        val encodedRequest = encodeRequest(request)
        val coverageSession = request.coverageRequest?.let {
            FastCheckCoverageSession.prepare(
                nodeExecutable = nodeExecutable,
                adapterEntryPoint = adapterEntryPoint,
                request = request,
            )
        }

        try {
            val output = invokeAdapter(
                encodedRequest = encodedRequest,
                request = request,
                command = coverageSession?.adapterCommand
                    ?: listOf(nodeExecutable, adapterEntryPoint.toString()),
            )
            validateProcessOutput(output, request)
            val response = decodeResponse(output.stdout, request)
            val result = decodeSuccessfulResponse(response, request)

            return coverageSession?.attachTo(result) ?: result
        } finally {
            coverageSession?.close()
        }
    }

    private fun invokeAdapter(
        encodedRequest: String,
        request: FastCheckExecutionRequest,
        command: List<String>,
    ): FastCheckProcessOutput = try {
        transport.invoke(
            command = command,
            request = encodedRequest,
            timeoutMillis = saturatedAdd(request.timeoutMillis, transportGraceMillis),
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
        val encodedRequest = PropertyManifestJson.json.encodeToString(request)

        if (encodedRequest.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) {
            throw backendError(
                kind = BackendErrorKind.INVALID_REQUEST,
                code = PbtDiagnosticCode.BACKEND_REQUEST_TOO_LARGE,
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
                code = PbtDiagnosticCode.BACKEND_PROCESS_FAILED,
                message = "fast-check adapter exited with code ${output.exitCode}: $detail",
                request = request,
            )
        }

        if (output.stdout.isBlank()) {
            throw backendError(
                kind = BackendErrorKind.PROTOCOL_ERROR,
                code = PbtDiagnosticCode.BACKEND_RESPONSE_EMPTY,
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
            code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
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
                code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
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

    private fun invalidResponse(
        message: String,
        request: FastCheckExecutionRequest,
    ): PbtBackendException = backendError(
        kind = BackendErrorKind.PROTOCOL_ERROR,
        code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
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
    PbtDiagnosticCode.BACKEND_REQUEST_TOO_LARGE -> BackendErrorKind.INVALID_REQUEST
    PbtDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE -> BackendErrorKind.PROTOCOL_ERROR
    PbtDiagnosticCode.BACKEND_PROCESS_TIMEOUT -> BackendErrorKind.TIMEOUT
    else -> BackendErrorKind.PROCESS_FAILURE
}
