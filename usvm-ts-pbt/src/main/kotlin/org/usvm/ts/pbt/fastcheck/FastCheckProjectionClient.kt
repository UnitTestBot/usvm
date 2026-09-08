package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.contains
import java.nio.file.Path

/** Limits for one projection request to the private Node adapter. */
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
        require(shutdownGraceMillis in 1..Int.MAX_VALUE.toLong()) {
            "Projection shutdown grace period exceeds the delay range supported by Node timers"
        }
    }
}

/** Synchronous Kotlin client for sampling concrete values from the private fast-check Node adapter. */
class FastCheckProjectionClient private constructor(
    private val adapterCommand: List<String>,
    private val transportLimits: FastCheckProjectionTransportLimits,
    private val transport: FastCheckProcessTransport,
) {
    constructor(
        nodeExecutable: String = "node",
        adapterEntryPoint: Path = FastCheckRuntime.projectionEntryPoint(),
    ) : this(
        adapterCommand = listOf(nodeExecutable, adapterEntryPoint.toString()),
        transportLimits = DEFAULT_TRANSPORT_LIMITS,
        transport = createTransport(nodeExecutable, DEFAULT_TRANSPORT_LIMITS),
    )

    internal constructor(
        nodeExecutable: String = "node",
        adapterEntryPoint: Path = FastCheckRuntime.projectionEntryPoint(),
        transportLimits: FastCheckProjectionTransportLimits,
    ) : this(
        adapterCommand = listOf(nodeExecutable, adapterEntryPoint.toString()),
        transportLimits = transportLimits,
        transport = createTransport(nodeExecutable, transportLimits),
    )

    /** Projects the requested domains to fast-check and returns validated samples. */
    fun sample(request: FastCheckProjectionRequest): FastCheckProjectionResponse {
        validateRequest(request)

        val encodedRequest = PropertyManifestJson.json.encodeToString(request)
        val output = invokeAdapter(encodedRequest)
        val response = decodeResponse(output)

        throwBackendError(response)
        validateSuccessfulResponse(request, response)

        return FastCheckProjectionResponse(samples = response.samples)
    }

    private fun invokeAdapter(encodedRequest: String): String {
        val output = try {
            transport.invoke(
                command = adapterCommand,
                request = encodedRequest,
                timeoutMillis = transportLimits.wallClockTimeoutMillis,
                reportedTimeoutMillis = transportLimits.wallClockTimeoutMillis,
                description = "fast-check projection adapter",
            )
        } catch (error: FastCheckTransportException) {
            transportFailure(error)
        }

        if (output.exitCode != 0) processFailure(output)
        if (output.stdout.isBlank()) emptyResponse()

        return output.stdout
    }

    private fun transportFailure(error: FastCheckTransportException): Nothing =
        throw FastCheckProjectionException(
            code = error.code,
            message = error.message.orEmpty(),
            cause = error,
        )

    private fun processFailure(output: FastCheckProcessOutput): Nothing =
        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_PROCESS_FAILED,
            message = "fast-check adapter exited with code ${output.exitCode}: ${output.stderr.trim()}",
        )

    private fun emptyResponse(): Nothing = throw FastCheckProjectionException(
        code = PbtDiagnosticCode.BACKEND_RESPONSE_EMPTY,
        message = "fast-check adapter returned an empty response",
    )

    private fun decodeResponse(stdout: String): FastCheckProjectionWireResponse = try {
        PropertyManifestJson.json.decodeFromString(stdout)
    } catch (error: IllegalArgumentException) {
        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
            message = "fast-check adapter returned invalid JSON: ${error.message}",
            cause = error,
        )
    }

    private fun throwBackendError(response: FastCheckProjectionWireResponse) {
        if (response.status != "error") return

        val diagnostic = response.diagnostics.firstOrNull()
            ?: invalidResponse("fast-check error response does not contain a diagnostic")

        throw FastCheckProjectionException(
            code = diagnostic.code,
            message = diagnostic.message,
            path = diagnostic.path,
        )
    }

    private fun validateSuccessfulResponse(
        request: FastCheckProjectionRequest,
        response: FastCheckProjectionWireResponse,
    ) {
        val hasOkStatus = response.status == "ok"
        val hasExpectedSampleCount = response.samples.size == request.numSamples
        val allSamplesHaveExpectedInputCount = response.samples.all { sample ->
            sample.size == request.domains.size
        }
        val validShape = hasOkStatus && hasExpectedSampleCount && allSamplesHaveExpectedInputCount
        if (!validShape) invalidResponse("fast-check adapter returned an invalid successful response")

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

    private fun validateRequest(request: FastCheckProjectionRequest) {
        if (request.numSamples !in 1..MAX_SAMPLES || request.domains.isEmpty()) {
            throw FastCheckProjectionException(
                code = PbtDiagnosticCode.PROTOCOL_REQUEST_INVALID,
                message = "Request requires domains and numSamples in 1..$MAX_SAMPLES",
                path = "request",
            )
        }
    }

    private fun invalidResponse(message: String, path: String? = null): Nothing =
        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_RESPONSE_INVALID,
            message = message,
            path = path,
        )

    private companion object {
        const val MAX_SAMPLES = 10_000
        const val DEFAULT_MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_STDOUT_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_STDERR_BYTES = 64 * 1024
        const val DEFAULT_WALL_CLOCK_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_SHUTDOWN_GRACE_MILLIS = 250L

        val DEFAULT_TRANSPORT_LIMITS = FastCheckProjectionTransportLimits(
            maxRequestBytes = DEFAULT_MAX_REQUEST_BYTES,
            maxStdoutBytes = DEFAULT_MAX_STDOUT_BYTES,
            maxStderrBytes = DEFAULT_MAX_STDERR_BYTES,
            wallClockTimeoutMillis = DEFAULT_WALL_CLOCK_TIMEOUT_MILLIS,
            shutdownGraceMillis = DEFAULT_SHUTDOWN_GRACE_MILLIS,
        )

        fun createTransport(
            nodeExecutable: String,
            limits: FastCheckProjectionTransportLimits,
        ) = FastCheckProcessTransport(
            nodeExecutable = nodeExecutable,
            maxRequestBytes = limits.maxRequestBytes,
            maxStdoutBytes = limits.maxStdoutBytes,
            maxStderrBytes = limits.maxStderrBytes,
            shutdownGraceMillis = limits.shutdownGraceMillis,
        )
    }
}
