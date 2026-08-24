package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyId
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Supervised one-shot transport for the private fast-check execution bridge. */
internal class FastCheckProcessClient(
    private val nodeExecutable: String = "node",
    private val adapterEntryPoint: Path,
    private val transportGraceMillis: Long = DEFAULT_TRANSPORT_GRACE_MILLIS,
    private val shutdownGraceMillis: Long = DEFAULT_SHUTDOWN_GRACE_MILLIS,
) {
    /** Executes one request and exposes only a fully validated common result. */
    fun check(request: FastCheckExecutionRequest): PropertyRunResult {
        val encodedRequest = encodeRequest(request)

        val process = startAdapter(request)
        val executor = Executors.newCachedThreadPool()
        val stdout = executor.submit<BoundedText> { process.inputStream.readBounded(MAX_STDOUT_BYTES) }
        val stderr = executor.submit<BoundedText> { process.errorStream.readBounded(MAX_STDERR_BYTES) }
        val writer = executor.submit<Unit> {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
                output.write(encodedRequest)
            }
        }

        try {
            awaitProcess(process, request)
            await(
                future = writer,
                operation = "writing the fast-check request",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
                request = request,
            )

            val stdoutText = await(
                future = stdout,
                operation = "reading fast-check stdout",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                request = request,
            )
            val stderrText = await(
                future = stderr,
                operation = "reading fast-check stderr",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                request = request,
            )

            validateProcessExit(process, stderrText, request)
            validateStdout(stdoutText, request)

            val response = decodeResponse(stdoutText.text, request)

            return decodeSuccessfulResponse(response, request)
        } finally {
            executor.shutdownNow()
            if (process.isAlive) terminate(process)
        }
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

    private fun awaitProcess(process: Process, request: FastCheckExecutionRequest) {
        val hardDeadline = safeAdd(request.timeoutMillis, transportGraceMillis)

        if (!process.waitFor(hardDeadline, TimeUnit.MILLISECONDS)) {
            terminate(process)
            throw backendError(
                kind = BackendErrorKind.TIMEOUT,
                code = PbtDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
                message = "fast-check adapter exceeded the ${request.timeoutMillis} ms timeout",
                request = request,
            )
        }
    }

    private fun validateProcessExit(
        process: Process,
        stderr: BoundedText,
        request: FastCheckExecutionRequest,
    ) {
        if (process.exitValue() != 0) {
            val detail = stderr.text.trim().ifEmpty { "no stderr" }

            throw backendError(
                kind = BackendErrorKind.PROCESS_FAILURE,
                code = PbtDiagnosticCode.BACKEND_PROCESS_FAILED,
                message = "fast-check adapter exited with code ${process.exitValue()}: $detail",
                request = request,
            )
        }
    }

    private fun validateStdout(stdout: BoundedText, request: FastCheckExecutionRequest) {
        if (stdout.exceeded) {
            throw backendError(
                kind = BackendErrorKind.PROTOCOL_ERROR,
                code = PbtDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE,
                message = "fast-check adapter stdout exceeds $MAX_STDOUT_BYTES bytes",
                request = request,
            )
        }

        if (stdout.text.isBlank()) {
            throw backendError(
                kind = BackendErrorKind.PROTOCOL_ERROR,
                code = PbtDiagnosticCode.BACKEND_RESPONSE_EMPTY,
                message = "fast-check adapter returned an empty response",
                request = request,
            )
        }
    }

    private fun startAdapter(request: FastCheckExecutionRequest): Process = try {
        ProcessBuilder(nodeExecutable, adapterEntryPoint.toString()).start()
    } catch (error: IOException) {
        throw backendError(
            kind = BackendErrorKind.PROCESS_FAILURE,
            code = PbtDiagnosticCode.BACKEND_PROCESS_START_FAILED,
            message = "Failed to start fast-check adapter: ${error.message}",
            request = request,
            cause = error,
        )
    }

    private fun <T> await(
        future: Future<T>,
        operation: String,
        failureCode: String,
        request: FastCheckExecutionRequest,
    ): T = try {
        future.get()
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()

        throw backendError(
            kind = BackendErrorKind.PROCESS_FAILURE,
            code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
            message = "Interrupted while $operation",
            request = request,
            cause = error,
        )
    } catch (error: ExecutionException) {
        throw backendError(
            kind = BackendErrorKind.PROCESS_FAILURE,
            code = failureCode,
            message = "Failed while $operation: ${error.cause?.message}",
            request = request,
            cause = error,
        )
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

    private fun terminate(process: Process) {
        process.destroy()

        if (!process.waitFor(shutdownGraceMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val MAX_STDOUT_BYTES = 4 * 1024 * 1024
        const val MAX_STDERR_BYTES = 64 * 1024
        const val DEFAULT_TRANSPORT_GRACE_MILLIS = 2_000L
        const val DEFAULT_SHUTDOWN_GRACE_MILLIS = 250L
    }
}

private data class BoundedText(val text: String, val exceeded: Boolean)

private fun InputStream.readBounded(limit: Int): BoundedText {
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var exceeded = false

    while (true) {
        val read = read(buffer)
        if (read < 0) break

        val remaining = limit - output.size()

        if (remaining > 0) output.write(buffer, 0, minOf(read, remaining))
        if (read > remaining) exceeded = true
    }

    return BoundedText(
        text = output.toString(Charsets.UTF_8),
        exceeded = exceeded,
    )
}

private fun safeAdd(left: Long, right: Long): Long = if (left > Long.MAX_VALUE - right) {
    Long.MAX_VALUE
} else {
    left + right
}
