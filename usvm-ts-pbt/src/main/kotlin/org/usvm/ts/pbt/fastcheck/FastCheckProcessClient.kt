package org.usvm.ts.pbt.fastcheck

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.coverage.C8_COLLECTOR_VERSION
import org.usvm.ts.pbt.coverage.CoverageArtifactException
import org.usvm.ts.pbt.coverage.IstanbulCoverageContext
import org.usvm.ts.pbt.coverage.decodeIstanbulCoverageReport
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyId
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Supervised one-shot transport for the private fast-check execution bridge. */
internal class FastCheckProcessClient(
    private val nodeExecutable: String = "node",
    private val adapterEntryPoint: Path,
    private val transportGraceMillis: Long = DEFAULT_TRANSPORT_GRACE_MILLIS,
    private val shutdownGraceMillis: Long = DEFAULT_SHUTDOWN_GRACE_MILLIS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Executes one request and exposes only a fully validated common result. */
    fun check(request: FastCheckExecutionRequest): PropertyRunResult = try {
        runBlocking { checkSuspending(request) }
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()

        throw backendError(
            kind = BackendErrorKind.PROCESS_FAILURE,
            code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
            message = "Interrupted while waiting for the fast-check adapter",
            request = request,
            cause = error,
        )
    }

    private suspend fun checkSuspending(request: FastCheckExecutionRequest): PropertyRunResult = supervisorScope {
        val encodedRequest = encodeRequest(request)

        val coverageRuntimeVersion = request.coverageRequest?.let { nodeVersion(request) }
        val coverageWorkspace = request.coverageRequest?.let { createCoverageWorkspace(request) }
        var process: Process? = null

        try {
            val startedProcess = startAdapter(request, coverageWorkspace)
            process = startedProcess
            val stdout = async(ioDispatcher) { startedProcess.inputStream.readBounded(MAX_STDOUT_BYTES) }
            val stderr = async(ioDispatcher) { startedProcess.errorStream.readBounded(MAX_STDERR_BYTES) }
            val writer = async(ioDispatcher) {
                startedProcess.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
                    output.write(encodedRequest)
                }
            }

            awaitProcess(startedProcess, request)

            awaitIo(
                task = writer,
                operation = "writing the fast-check request",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
                request = request,
            )

            val stdoutText = awaitIo(
                task = stdout,
                operation = "reading fast-check stdout",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                request = request,
            )
            val stderrText = awaitIo(
                task = stderr,
                operation = "reading fast-check stderr",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                request = request,
            )

            validateProcessExit(startedProcess, stderrText, request)
            validateStdout(stdoutText, request)

            val response = decodeResponse(stdoutText.text, request)

            val result = decodeSuccessfulResponse(response, request)

            coverageWorkspace?.let { workspace ->
                collectCoverage(
                    result = result,
                    request = request,
                    workspace = workspace,
                    runtimeVersion = requireNotNull(coverageRuntimeVersion),
                )
            } ?: result
        } finally {
            process?.takeIf(Process::isAlive)?.let(::terminate)
            coverageWorkspace?.root?.toFile()?.deleteRecursively()
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

    private suspend fun awaitProcess(process: Process, request: FastCheckExecutionRequest) {
        val hardTimeoutMillis = safeAdd(request.timeoutMillis, transportGraceMillis)
        val exitCode = withTimeoutOrNull(hardTimeoutMillis) {
            runInterruptible(ioDispatcher) { process.waitFor() }
        }

        if (exitCode == null) {
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

    private fun startAdapter(
        request: FastCheckExecutionRequest,
        coverageWorkspace: CoverageWorkspace?,
    ): Process = try {
        ProcessBuilder(adapterCommand(request, coverageWorkspace)).start()
    } catch (error: IOException) {
        throw backendError(
            kind = BackendErrorKind.PROCESS_FAILURE,
            code = PbtDiagnosticCode.BACKEND_PROCESS_START_FAILED,
            message = "Failed to start fast-check adapter: ${error.message}",
            request = request,
            cause = error,
        )
    }

    private fun adapterCommand(
        request: FastCheckExecutionRequest,
        coverageWorkspace: CoverageWorkspace?,
    ): List<String> {
        if (coverageWorkspace == null) return listOf(nodeExecutable, adapterEntryPoint.toString())

        val command = mutableListOf(
            nodeExecutable,
            coverageWorkspace.c8EntryPoint.toString(),
            "--config=${coverageWorkspace.configPath}",
            "--reporter=json",
            "--reports-dir=${coverageWorkspace.reportDirectory}",
            "--temp-directory=${coverageWorkspace.rawDirectory}",
            "--exclude-after-remap",
            "--allowExternal",
            "--exclude=__usvm_no_default_excludes__",
        )
        if (CoverageScope.DEPENDENCIES in requireNotNull(request.coverageRequest).scopes) {
            command += "--exclude-node-modules=false"
        }
        command += nodeExecutable
        command += adapterEntryPoint.toString()

        return command
    }

    private fun createCoverageWorkspace(request: FastCheckExecutionRequest): CoverageWorkspace {
        val adapterRoot = adapterRoot()
        val c8EntryPoint = adapterRoot.resolve("node_modules/c8/bin/c8.js")
        if (!Files.isRegularFile(c8EntryPoint)) {
            throw backendError(
                kind = BackendErrorKind.COVERAGE,
                code = PbtDiagnosticCode.COVERAGE_COLLECTOR_NOT_FOUND,
                message = "Cannot locate c8 $C8_COLLECTOR_VERSION in the fast-check adapter runtime",
                request = request,
                path = c8EntryPoint.toString(),
            )
        }

        val root = Files.createTempDirectory("usvm-ts-pbt-coverage-")
        val configPath = Files.writeString(root.resolve("c8-config.json"), "{}")
        return CoverageWorkspace(
            root = root,
            configPath = configPath,
            rawDirectory = root.resolve("raw"),
            reportDirectory = root.resolve("report"),
            c8EntryPoint = c8EntryPoint,
            adapterRoot = adapterRoot,
        )
    }

    private fun collectCoverage(
        result: PropertyRunResult,
        request: FastCheckExecutionRequest,
        workspace: CoverageWorkspace,
        runtimeVersion: String,
    ): PropertyRunResult {
        val coverageRequest = requireNotNull(request.coverageRequest)
        val entryPointPaths = hashSetOf<String>()
        request.sourceRoots.forEach { sourceRoot ->
            val root = Path.of(sourceRoot)
            entryPointPaths += root.resolve(request.manifest.predicate.module).normalize().toString()
            request.manifest.precondition?.let { precondition ->
                entryPointPaths += root.resolve(precondition.module).normalize().toString()
            }
        }
        val artifact = try {
            decodeIstanbulCoverageReport(
                reportPath = workspace.reportDirectory.resolve("coverage-final.json"),
                context = IstanbulCoverageContext(
                    backendId = FastCheckBackend.FAST_CHECK_BACKEND_ID,
                    backendVersion = FastCheckBackend.FAST_CHECK_BACKEND_VERSION,
                    propertyId = result.propertyId,
                    sourceRoots = request.sourceRoots,
                    propertyEntryPointPaths = entryPointPaths,
                    adapterRoot = workspace.adapterRoot.toString(),
                    runtimeVersion = runtimeVersion,
                    request = coverageRequest,
                ),
            )
        } catch (error: CoverageArtifactException) {
            throw backendError(
                kind = BackendErrorKind.COVERAGE,
                code = error.diagnostic.code,
                message = error.diagnostic.message,
                request = request,
                path = error.diagnostic.path,
                cause = error,
            )
        }

        return result.copy(coverage = artifact)
    }

    private suspend fun nodeVersion(request: FastCheckExecutionRequest): String {
        val process = startNodeVersionProcess(request)

        awaitNodeVersionProcess(process, request)

        return readNodeVersion(process, request)
    }

    private fun startNodeVersionProcess(request: FastCheckExecutionRequest): Process = try {
        ProcessBuilder(nodeExecutable, "--version").start()
    } catch (error: IOException) {
        throw coverageRuntimeVersionError(request, error)
    }

    private suspend fun awaitNodeVersionProcess(
        process: Process,
        request: FastCheckExecutionRequest,
    ) {
        val completed = runInterruptible(ioDispatcher) {
            process.waitFor(NODE_VERSION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }
        if (!completed) {
            process.destroyForcibly()

            throw backendError(
                kind = BackendErrorKind.COVERAGE,
                code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                message = "Timed out while querying the Node.js runtime version",
                request = request,
            )
        }
    }

    private fun readNodeVersion(
        process: Process,
        request: FastCheckExecutionRequest,
    ): String {
        val version = process.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
            input.readLine().orEmpty().trim()
        }
        if (process.exitValue() != 0 || version.isBlank()) {
            throw backendError(
                kind = BackendErrorKind.COVERAGE,
                code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                message = "Cannot query the Node.js runtime version",
                request = request,
            )
        }

        return requireSupportedNodeVersion(version, request)
    }

    private fun requireSupportedNodeVersion(
        version: String,
        request: FastCheckExecutionRequest,
    ): String {
        val match = NODE_VERSION_PATTERN.matchEntire(version)
        val major = match?.groupValues?.get(1)?.toIntOrNull()
        val minor = match?.groupValues?.get(2)?.toIntOrNull()
        if (major == null || minor == null) {
            throw backendError(
                kind = BackendErrorKind.COVERAGE,
                code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
                message = "Cannot parse the Node.js runtime version: $version",
                request = request,
            )
        }

        val isSupported = major > MINIMUM_NODE_MAJOR_VERSION ||
            major == MINIMUM_NODE_MAJOR_VERSION && minor >= MINIMUM_NODE_MINOR_VERSION
        if (!isSupported) {
            throw backendError(
                kind = BackendErrorKind.COVERAGE,
                code = PbtDiagnosticCode.COVERAGE_RUNTIME_UNSUPPORTED,
                message = "Coverage requires Node.js 18.18 or newer; found $version",
                request = request,
            )
        }

        return version
    }

    private fun coverageRuntimeVersionError(
        request: FastCheckExecutionRequest,
        error: IOException,
    ) = backendError(
        kind = BackendErrorKind.COVERAGE,
        code = PbtDiagnosticCode.COVERAGE_RUNTIME_VERSION_UNAVAILABLE,
        message = "Cannot query the Node.js runtime version: ${error.message}",
        request = request,
        cause = error,
    )

    private fun adapterRoot(): Path = adapterEntryPoint.parent?.parent?.parent
        ?: throw IllegalArgumentException("Adapter entry point has no runtime root: $adapterEntryPoint")

    private suspend fun <T> awaitIo(
        task: Deferred<T>,
        operation: String,
        failureCode: String,
        request: FastCheckExecutionRequest,
    ): T = try {
        task.await()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        throw backendError(
            kind = BackendErrorKind.PROCESS_FAILURE,
            code = failureCode,
            message = "Failed while $operation: ${error.message}",
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
        const val NODE_VERSION_TIMEOUT_MILLIS = 5_000L
        const val MINIMUM_NODE_MAJOR_VERSION = 18
        const val MINIMUM_NODE_MINOR_VERSION = 18
        val NODE_VERSION_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
    }
}

private data class CoverageWorkspace(
    val root: Path,
    val configPath: Path,
    val rawDirectory: Path,
    val reportDirectory: Path,
    val c8EntryPoint: Path,
    val adapterRoot: Path,
)

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
