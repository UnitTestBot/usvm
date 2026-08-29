package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.contains
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Internal transport limits for bounded projection-process communication. */
internal data class FastCheckProjectionTransportLimits(
    val maxRequestBytes: Int,
    val maxStdoutBytes: Int,
    val maxStderrBytes: Int,
    val wallClockTimeoutMillis: Long,
    val shutdownGraceMillis: Long,
) {
    init {
        require(maxRequestBytes > 0) { "Maximum request size must be positive" }
        require(maxStdoutBytes > 0) { "Maximum stdout size must be positive" }
        require(maxStderrBytes > 0) { "Maximum stderr size must be positive" }
        require(wallClockTimeoutMillis > 0) { "Projection wall-clock timeout must be positive" }
        require(shutdownGraceMillis > 0) { "Projection shutdown grace period must be positive" }
    }
}

/**
 * Synchronous Kotlin client for the private fast-check Node adapter.
 *
 * Each request starts a fresh adapter process, writes one JSON request, and validates the single JSON response
 * before exposing sampled values to Kotlin callers.
 */
class FastCheckProjectionClient private constructor(
    private val nodeExecutable: String,
    private val adapterEntryPoint: Path,
    private val transportLimits: FastCheckProjectionTransportLimits,
    @Suppress("UNUSED_PARAMETER") internalConstructorMarker: Unit,
) {
    constructor(
        nodeExecutable: String = "node",
        adapterEntryPoint: Path = FastCheckRuntime.projectionEntryPoint(),
    ) : this(
        nodeExecutable = nodeExecutable,
        adapterEntryPoint = adapterEntryPoint,
        transportLimits = DEFAULT_TRANSPORT_LIMITS,
        internalConstructorMarker = Unit,
    )

    internal constructor(
        nodeExecutable: String = "node",
        adapterEntryPoint: Path = FastCheckRuntime.projectionEntryPoint(),
        transportLimits: FastCheckProjectionTransportLimits,
    ) : this(
        nodeExecutable = nodeExecutable,
        adapterEntryPoint = adapterEntryPoint,
        transportLimits = transportLimits,
        internalConstructorMarker = Unit,
    )

    /** Projects the requested domains to fast-check and returns the generated samples. */
    fun sample(request: FastCheckProjectionRequest): FastCheckProjectionResponse {
        validateRequest(request)

        val encodedRequest = encodeRequest(request)
        val response = decodeResponse(invokeAdapter(encodedRequest))

        throwBackendError(response)
        validateSuccessfulResponse(request, response)

        return FastCheckProjectionResponse(
            samples = response.samples,
        )
    }

    private fun throwBackendError(response: FastCheckProjectionWireResponse) {
        if (response.status == "error") {
            val diagnostic = response.diagnostics.firstOrNull()
                ?: invalidResponse("fast-check error response does not contain a diagnostic")

            throw FastCheckProjectionException(
                code = diagnostic.code,
                message = diagnostic.message,
                path = diagnostic.path,
            )
        }
    }

    private fun validateSuccessfulResponse(
        request: FastCheckProjectionRequest,
        response: FastCheckProjectionWireResponse,
    ) {
        val hasExpectedStatus = response.status == "ok"
        val hasExpectedSampleCount = response.samples.size == request.numSamples
        val hasExpectedArity = response.samples.all { it.size == request.domains.size }

        if (!hasExpectedStatus || !hasExpectedSampleCount || !hasExpectedArity) {
            invalidResponse("fast-check adapter returned an invalid successful response")
        }

        response.samples.forEachIndexed { sampleIndex, sample ->
            sample.forEachIndexed { inputIndex, value ->
                if (value !in request.domains[inputIndex]) {
                    invalidResponse(
                        message = "fast-check adapter returned a value outside its requested domain",
                        path = "samples[$sampleIndex][$inputIndex]",
                    )
                }
            }
        }
    }

    private fun encodeRequest(request: FastCheckProjectionRequest): String {
        val encodedRequest = PropertyManifestJson.json.encodeToString(request)
        if (encodedRequest.toByteArray(Charsets.UTF_8).size > transportLimits.maxRequestBytes) {
            throw FastCheckProjectionException(
                code = PbtDiagnosticCode.BACKEND_REQUEST_TOO_LARGE,
                message = "fast-check projection request exceeds ${transportLimits.maxRequestBytes} bytes",
            )
        }

        return encodedRequest
    }

    private fun invokeAdapter(encodedRequest: String): String {
        val process = startAdapter()
        val ioExecutor = Executors.newFixedThreadPool(IO_TASKS)

        try {
            val stdout = ioExecutor.submit<ProjectionBoundedText> {
                process.inputStream.readProjectionBounded(transportLimits.maxStdoutBytes)
            }
            val stderr = ioExecutor.submit<ProjectionBoundedText> {
                process.errorStream.readProjectionBounded(transportLimits.maxStderrBytes)
            }
            val writer = ioExecutor.submit {
                process.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
                    output.write(encodedRequest)
                }
            }

            awaitProcess(process)

            awaitIo(
                task = writer,
                operation = "writing the fast-check projection request",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
            )

            val stdoutText = awaitIo(
                task = stdout,
                operation = "reading fast-check projection stdout",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
            )
            val stderrText = awaitIo(
                task = stderr,
                operation = "reading fast-check projection stderr",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
            )

            if (process.exitValue() != 0) {
                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_PROCESS_FAILED,
                    message = "fast-check adapter exited with code ${process.exitValue()}: ${stderrText.text.trim()}",
                )
            }

            if (stdoutText.exceeded) {
                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE,
                    message = "fast-check projection stdout exceeds ${transportLimits.maxStdoutBytes} bytes",
                )
            }

            if (stderrText.exceeded) {
                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE,
                    message = "fast-check projection stderr exceeds ${transportLimits.maxStderrBytes} bytes",
                )
            }

            if (stdoutText.text.isBlank()) {
                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_RESPONSE_EMPTY,
                    message = "fast-check adapter returned an empty response",
                )
            }

            return stdoutText.text
        } finally {
            closeStreams(process)
            if (process.isAlive) {
                terminate(process)
            }
            ioExecutor.shutdownNow()
        }
    }

    private fun awaitProcess(process: Process) {
        val completed = try {
            process.waitFor(transportLimits.wallClockTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()

            throw FastCheckProjectionException(
                code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
                message = "Interrupted while waiting for the fast-check projection adapter",
                cause = error,
            )
        }

        if (!completed) {
            terminate(process)

            throw FastCheckProjectionException(
                code = PbtDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
                message = "fast-check projection adapter exceeded the ${transportLimits.wallClockTimeoutMillis} ms timeout",
            )
        }
    }

    private fun <T> awaitIo(
        task: Future<T>,
        operation: String,
        failureCode: String,
    ): T = try {
        task.get()
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()

        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
            message = "Interrupted while $operation",
            cause = error,
        )
    } catch (error: ExecutionException) {
        throw FastCheckProjectionException(
            code = failureCode,
            message = "Failed while $operation: ${error.cause?.message}",
            cause = error.cause,
        )
    }

    private fun startAdapter(): Process = try {
        ProcessBuilder(nodeExecutable, adapterEntryPoint.toString()).start()
    } catch (error: IOException) {
        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_PROCESS_START_FAILED,
            message = "Failed to start fast-check adapter: ${error.message}",
            cause = error,
        )
    }

    private fun decodeResponse(stdout: String): FastCheckProjectionWireResponse = try {
        PropertyManifestJson.json.decodeFromString(stdout)
    } catch (error: IllegalArgumentException) {
        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
            message = "fast-check adapter returned invalid JSON: ${error.message}",
            cause = error,
        )
    }

    private fun invalidResponse(message: String, path: String? = null): Nothing = throw FastCheckProjectionException(
        code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
        message = message,
        path = path,
    )

    private fun validateRequest(request: FastCheckProjectionRequest) {
        val hasValidSampleCount = request.numSamples in 1..MAX_SAMPLES
        val hasDomains = request.domains.isNotEmpty()

        if (!hasValidSampleCount || !hasDomains) {
            throw FastCheckProjectionException(
                code = PbtDiagnosticCode.PROTOCOL_REQUEST_INVALID,
                message = "Request requires domains and numSamples in 1..$MAX_SAMPLES",
                path = "request",
            )
        }
    }

    private fun closeStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun terminate(process: Process) {
        process.destroy()

        try {
            if (!process.waitFor(transportLimits.shutdownGraceMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(transportLimits.shutdownGraceMillis, TimeUnit.MILLISECONDS)
            }
        } catch (error: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val MAX_SAMPLES = 10_000
        const val DEFAULT_MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_STDOUT_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_STDERR_BYTES = 64 * 1024
        const val DEFAULT_WALL_CLOCK_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_SHUTDOWN_GRACE_MILLIS = 250L
        const val IO_TASKS = 3

        val DEFAULT_TRANSPORT_LIMITS = FastCheckProjectionTransportLimits(
            maxRequestBytes = DEFAULT_MAX_REQUEST_BYTES,
            maxStdoutBytes = DEFAULT_MAX_STDOUT_BYTES,
            maxStderrBytes = DEFAULT_MAX_STDERR_BYTES,
            wallClockTimeoutMillis = DEFAULT_WALL_CLOCK_TIMEOUT_MILLIS,
            shutdownGraceMillis = DEFAULT_SHUTDOWN_GRACE_MILLIS,
        )
    }
}

private data class ProjectionBoundedText(val text: String, val exceeded: Boolean)

private fun InputStream.readProjectionBounded(limit: Int): ProjectionBoundedText {
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

    return ProjectionBoundedText(
        text = output.toString(Charsets.UTF_8),
        exceeded = exceeded,
    )
}
