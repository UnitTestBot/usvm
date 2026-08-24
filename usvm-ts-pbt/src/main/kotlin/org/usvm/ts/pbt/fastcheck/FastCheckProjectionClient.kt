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
    private val adapterEntryPoint: Path = FastCheckRuntime.projectionEntryPoint(),
) {
    /** Projects the requested domains to fast-check and returns the generated samples. */
    fun sample(request: FastCheckProjectionRequest): FastCheckProjectionResponse {
        validateRequest(request)

        val response = decodeResponse(invokeAdapter(request))

        validateProtocolVersion(response)
        throwBackendError(response)
        validateSuccessfulResponse(request, response)

        return FastCheckProjectionResponse(
            samples = response.samples,
        )
    }

    private fun validateProtocolVersion(response: FastCheckProjectionWireResponse) {
        if (response.protocolVersion != FAST_CHECK_PROTOCOL_VERSION) {
            throw FastCheckProjectionException(
                code = "backend.response.mismatch",
                message = "fast-check response protocol version is incompatible",
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
        val hasExpectedStatus = response.status == "ok"
        val hasExpectedSampleCount = response.samples.size == request.numSamples
        val hasExpectedArity = response.samples.all { it.size == request.domains.size }

        if (!hasExpectedStatus || !hasExpectedSampleCount || !hasExpectedArity) {
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
        val hasValidSampleCount = request.numSamples in 1..MAX_SAMPLES
        val hasDomains = request.domains.isNotEmpty()

        if (!hasValidSampleCount || !hasDomains) {
            throw FastCheckProjectionException(
                code = "protocol.request.invalid",
                message = "Request requires domains and numSamples in 1..10000",
                path = "request",
            )
        }
    }

    private companion object {
        const val MAX_SAMPLES = 10_000
    }
}
