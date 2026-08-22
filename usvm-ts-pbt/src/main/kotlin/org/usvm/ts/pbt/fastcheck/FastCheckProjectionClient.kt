package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * Synchronous Kotlin client for the private fast-check Node adapter.
 *
 * Each request starts a fresh adapter process, writes one JSON request, and validates the single JSON response
 * before exposing sampled values to Kotlin callers.
 */
class FastCheckProjectionClient(
    private val nodeExecutable: String = "node",
    private val adapterEntryPoint: Path,
) {
    /** Projects the requested domains to fast-check and returns the generated samples. */
    fun sample(request: FastCheckProjectionRequest): FastCheckProjectionResponse {
        validateRequest(request)
        val response = decodeResponse(invokeAdapter(request))
        validateResponseIdentity(request, response)
        throwBackendError(response)
        validateSuccessfulResponse(request, response)
        return FastCheckProjectionResponse(
            protocolVersion = response.protocolVersion,
            requestId = requireNotNull(response.requestId),
            samples = response.samples,
        )
    }

    private fun validateResponseIdentity(
        request: FastCheckProjectionRequest,
        response: FastCheckProjectionWireResponse,
    ) {
        if (response.protocolVersion != FAST_CHECK_PROTOCOL_VERSION || response.requestId != request.requestId) {
            throw FastCheckProjectionException(
                code = "backend.response.mismatch",
                message = "fast-check response identity does not match the request",
            )
        }
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
        if (response.status != "ok" || response.samples.size != request.numSamples ||
            response.samples.any { it.size != request.domains.size }
        ) {
            throw FastCheckProjectionException(
                code = "backend.response.invalid",
                message = "fast-check adapter returned an invalid successful response",
            )
        }
    }

    private fun invokeAdapter(request: FastCheckProjectionRequest): String {
        val process = startAdapter()
        val errorReaderExecutor = Executors.newSingleThreadExecutor()
        val stderr = errorReaderExecutor.submit<String> {
            process.errorStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(PropertyManifestJson.json.encodeToString(request))
            }
            val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            val exitCode = process.waitFor()
            val stderrText = stderr.get()
            if (exitCode != 0) {
                throw FastCheckProjectionException(
                    code = "backend.process.failed",
                    message = "fast-check adapter exited with code $exitCode: ${stderrText.trim()}",
                )
            }
            if (stdout.isBlank()) {
                throw FastCheckProjectionException(
                    code = "backend.response.empty",
                    message = "fast-check adapter returned an empty response",
                )
            }
            return stdout
        } finally {
            errorReaderExecutor.shutdownNow()
        }
    }

    private fun startAdapter(): Process = try {
        ProcessBuilder(nodeExecutable, adapterEntryPoint.toString()).start()
    } catch (error: IOException) {
        throw FastCheckProjectionException(
            code = "backend.process.start.failed",
            message = "Failed to start fast-check adapter: ${error.message}",
            cause = error,
        )
    }

    private fun decodeResponse(stdout: String): FastCheckProjectionWireResponse = try {
        PropertyManifestJson.json.decodeFromString(stdout)
    } catch (error: IllegalArgumentException) {
        throw FastCheckProjectionException(
            code = "backend.response.invalid",
            message = "fast-check adapter returned invalid JSON: ${error.message}",
            cause = error,
        )
    }

    private fun invalidResponse(message: String): Nothing = throw FastCheckProjectionException(
        code = "backend.response.invalid",
        message = message,
    )

    private fun validateRequest(request: FastCheckProjectionRequest) {
        val valid = request.requestId.isNotEmpty() &&
            request.operation == "sample" &&
            request.numSamples in 1..MAX_SAMPLES &&
            request.domains.isNotEmpty()
        if (!valid) {
            throw FastCheckProjectionException(
                code = "protocol.request.invalid",
                message = "Request requires a non-empty ID and domains, operation sample, and numSamples in 1..10000",
                path = "request",
            )
        }
    }

    private companion object {
        const val MAX_SAMPLES = 10_000
    }
}
