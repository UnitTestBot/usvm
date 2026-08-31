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
import org.usvm.ts.pbt.coverage.CoverageArtifactException
import org.usvm.ts.pbt.coverage.IstanbulCoverageContext
import org.usvm.ts.pbt.coverage.decodeIstanbulCoverageReport
import org.usvm.ts.pbt.coverage.inspectRawV8SourceMapDiagnostics
import org.usvm.ts.pbt.coverage.mergeCoverageDiagnostics
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
    init {
        require(shutdownGraceMillis in 1..Int.MAX_VALUE.toLong()) {
            "Shutdown grace period must fit the positive delay range supported by Node timers"
        }
    }

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
        val deadlineNanos = deadlineAfter(safeAdd(request.timeoutMillis, transportGraceMillis))
        val operationDeadlineNanos = deadlineBefore(
            deadlineNanos = deadlineNanos,
            durationMillis = minOf(FORCED_TERMINATION_RESERVE_MILLIS, transportGraceMillis),
        )
        var managedProcess: ManagedFastCheckProcess? = null
        var stdout: Deferred<BoundedText>? = null
        var stderr: Deferred<BoundedText>? = null
        var writer: Deferred<Unit>? = null

        try {
            val startedProcess = startAdapter(request, coverageWorkspace)
            managedProcess = startedProcess
            val process = startedProcess.process
            val stdoutTask = async(ioDispatcher) { process.inputStream.readBounded(MAX_STDOUT_BYTES) }
            stdout = stdoutTask
            val stderrTask = async(ioDispatcher) { process.errorStream.readBounded(MAX_STDERR_BYTES) }
            stderr = stderrTask
            val writerTask = async(ioDispatcher) {
                process.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
                    output.write(encodedRequest)
                }
            }
            writer = writerTask

            awaitProcess(
                process = process,
                deadlineNanos = operationDeadlineNanos,
                request = request,
            )

            awaitIo(
                task = writerTask,
                operation = "writing the fast-check request",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
                deadlineNanos = operationDeadlineNanos,
                request = request,
            )

            val stdoutText = awaitIo(
                task = stdoutTask,
                operation = "reading fast-check stdout",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                deadlineNanos = operationDeadlineNanos,
                request = request,
            )
            val stderrText = awaitIo(
                task = stderrTask,
                operation = "reading fast-check stderr",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                deadlineNanos = operationDeadlineNanos,
                request = request,
            )

            validateProcessExit(process, stderrText, request)
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
            writer?.cancel()
            stdout?.cancel()
            stderr?.cancel()
            managedProcess?.let { startedProcess ->
                terminate(startedProcess, deadlineNanos)
                closeStreams(startedProcess.process)
                runCatching { Files.deleteIfExists(startedProcess.processGroupFile) }
            }
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

    private suspend fun awaitProcess(
        process: Process,
        deadlineNanos: Long,
        request: FastCheckExecutionRequest,
    ) {
        val completed = withTimeoutOrNull(remainingMillis(deadlineNanos)) {
            runInterruptible(ioDispatcher) { process.waitFor() }
            true
        }

        if (completed == null) executionTimeout(request)
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
    ): ManagedFastCheckProcess {
        val processGroupFile = try {
            Files.createTempFile("usvm-execution-process-group-", ".pid")
        } catch (error: IOException) {
            throw processStartFailure(request, error)
        }
        var processStarted = false

        try {
            val process = ProcessBuilder(
                supervisedAdapterCommand(
                    request = request,
                    coverageWorkspace = coverageWorkspace,
                    processGroupFile = processGroupFile,
                ),
            ).start()
            processStarted = true

            return ManagedFastCheckProcess(
                process = process,
                processGroupFile = processGroupFile,
            )
        } catch (error: IOException) {
            throw processStartFailure(request, error)
        } finally {
            if (!processStarted) runCatching { Files.deleteIfExists(processGroupFile) }
        }
    }

    private fun processStartFailure(
        request: FastCheckExecutionRequest,
        error: IOException,
    ) = backendError(
        kind = BackendErrorKind.PROCESS_FAILURE,
        code = PbtDiagnosticCode.BACKEND_PROCESS_START_FAILED,
        message = "Failed to start fast-check adapter: ${error.message}",
        request = request,
        cause = error,
    )

    private fun supervisedAdapterCommand(
        request: FastCheckExecutionRequest,
        coverageWorkspace: CoverageWorkspace?,
        processGroupFile: Path,
    ): List<String> = buildList {
        add(nodeExecutable)
        add(FastCheckRuntime.processSupervisorEntryPoint().toString())
        add(PROCESS_SUPERVISOR_COMMAND)
        add(shutdownGraceMillis.toString())
        add(processGroupFile.toString())
        addAll(adapterCommand(request, coverageWorkspace))
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
                message = "Cannot locate c8 ${FastCheckRuntimeMetadata.coverageCollector.version} " +
                    "in the fast-check adapter runtime",
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
            entryPointPaths += canonicalizeExistingEntryPoint(
                root.resolve(request.manifest.predicate.module).normalize(),
            )
            request.manifest.precondition?.let { precondition ->
                entryPointPaths += canonicalizeExistingEntryPoint(
                    root.resolve(precondition.module).normalize(),
                )
            }
        }
        val artifact = try {
            val finalArtifact = decodeIstanbulCoverageReport(
                reportPath = workspace.reportDirectory.resolve("coverage-final.json"),
                context = IstanbulCoverageContext(
                    backendId = FastCheckBackend.FAST_CHECK_BACKEND_ID,
                    backendVersion = FastCheckRuntimeMetadata.fastCheckVersion,
                    propertyId = result.propertyId,
                    sourceRoots = request.sourceRoots,
                    propertyEntryPointPaths = entryPointPaths,
                    adapterRoot = workspace.adapterRoot.toString(),
                    runtimeVersion = runtimeVersion,
                    collector = FastCheckRuntimeMetadata.coverageCollector,
                    request = coverageRequest,
                ),
            )
            val rawDiagnostics = inspectRawV8SourceMapDiagnostics(
                rawDirectory = workspace.rawDirectory,
                sourceRoots = request.sourceRoots,
            )

            finalArtifact.copy(
                diagnostics = mergeCoverageDiagnostics(
                    finalDiagnostics = finalArtifact.diagnostics,
                    rawDiagnostics = rawDiagnostics,
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

    private fun canonicalizeExistingEntryPoint(candidate: Path): String =
        if (Files.exists(candidate)) candidate.toRealPath().toString() else candidate.toString()

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
        deadlineNanos: Long,
        request: FastCheckExecutionRequest,
    ): T = try {
        withTimeoutOrNull(remainingMillis(deadlineNanos)) {
            task.await()
        } ?: executionTimeout(request)
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

    private fun executionTimeout(request: FastCheckExecutionRequest): Nothing = throw backendError(
        kind = BackendErrorKind.TIMEOUT,
        code = PbtDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
        message = "fast-check adapter exceeded the ${request.timeoutMillis} ms timeout",
        request = request,
    )

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

    private fun closeStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun terminate(managedProcess: ManagedFastCheckProcess, deadlineNanos: Long) {
        val process = managedProcess.process
        if (!process.isAlive) {
            forceTerminateOwnedProcessGroup(managedProcess.processGroupFile, deadlineNanos)

            return
        }

        process.destroy()

        val gracefulDeadlineNanos = minOf(
            deadlineBefore(
                deadlineNanos = deadlineNanos,
                durationMillis = FORCED_TERMINATION_RESERVE_MILLIS,
            ),
            deadlineAfter(shutdownGraceMillis),
        )
        if (awaitProcessExit(process, gracefulDeadlineNanos)) return

        forceTerminateOwnedProcessGroup(managedProcess.processGroupFile, deadlineNanos)
        process.destroyForcibly()
        awaitProcessExit(process, deadlineNanos)
    }

    private fun forceTerminateOwnedProcessGroup(processGroupFile: Path, deadlineNanos: Long) {
        val processGroupId = runCatching {
            Files.readString(processGroupFile).trim().toLong()
        }.getOrNull() ?: return
        val command = if (IS_WINDOWS) {
            listOf("taskkill", "/PID", processGroupId.toString(), "/T", "/F")
        } else {
            listOf("/bin/kill", "-KILL", "--", "-$processGroupId")
        }
        val killer = runCatching {
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull() ?: return
        val waitMillis = minOf(remainingMillis(deadlineNanos), PROCESS_GROUP_KILL_WAIT_MILLIS)
        if (waitMillis == 0L) return

        try {
            if (!killer.waitFor(waitMillis, TimeUnit.MILLISECONDS)) killer.destroyForcibly()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            killer.destroyForcibly()
        }
    }

    private fun awaitProcessExit(process: Process, deadlineNanos: Long): Boolean {
        while (true) {
            if (!process.isAlive) return true

            val waitMillis = minOf(remainingMillis(deadlineNanos), PROCESS_POLL_MILLIS)
            if (waitMillis == 0L) return false

            try {
                if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS)) return true
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()

                return false
            }
        }
    }

    private fun deadlineAfter(timeoutMillis: Long): Long {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val now = System.nanoTime()

        return if (now > Long.MAX_VALUE - timeoutNanos) Long.MAX_VALUE else now + timeoutNanos
    }

    private fun deadlineBefore(deadlineNanos: Long, durationMillis: Long): Long {
        if (deadlineNanos == Long.MAX_VALUE) return Long.MAX_VALUE

        val durationNanos = TimeUnit.MILLISECONDS.toNanos(durationMillis)

        return if (deadlineNanos < Long.MIN_VALUE + durationNanos) Long.MIN_VALUE else deadlineNanos - durationNanos
    }

    private fun remainingMillis(deadlineNanos: Long): Long {
        if (deadlineNanos == Long.MAX_VALUE) return Long.MAX_VALUE

        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0) return 0

        return TimeUnit.NANOSECONDS.toMillis(remainingNanos)
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val MAX_STDOUT_BYTES = 4 * 1024 * 1024
        const val MAX_STDERR_BYTES = 64 * 1024
        const val DEFAULT_TRANSPORT_GRACE_MILLIS = 2_000L
        const val DEFAULT_SHUTDOWN_GRACE_MILLIS = 250L
        const val NODE_VERSION_TIMEOUT_MILLIS = 5_000L
        const val PROCESS_POLL_MILLIS = 10L
        const val FORCED_TERMINATION_RESERVE_MILLIS = 25L
        const val PROCESS_GROUP_KILL_WAIT_MILLIS = 10L
        const val MINIMUM_NODE_MAJOR_VERSION = 18
        const val MINIMUM_NODE_MINOR_VERSION = 18
        const val PROCESS_SUPERVISOR_COMMAND = "--command"
        val NODE_VERSION_PATTERN = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
        val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("windows")
    }
}

private data class ManagedFastCheckProcess(
    val process: Process,
    val processGroupFile: Path,
)

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
