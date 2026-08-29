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
import java.util.concurrent.TimeoutException

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
        val processTree = ProjectionProcessTree(process.toHandle())
        val deadlineNanos = deadlineAfter(transportLimits.wallClockTimeoutMillis)
        val ioExecutor = Executors.newFixedThreadPool(IO_TASKS)
        var stdout: Future<ProjectionBoundedText>? = null
        var stderr: Future<ProjectionBoundedText>? = null
        var writer: Future<*>? = null

        try {
            stdout = ioExecutor.submit<ProjectionBoundedText> {
                process.inputStream.readProjectionBounded(
                    limit = transportLimits.maxStdoutBytes,
                    stream = "stdout",
                )
            }
            stderr = ioExecutor.submit<ProjectionBoundedText> {
                process.errorStream.readProjectionBounded(
                    limit = transportLimits.maxStderrBytes,
                    stream = "stderr",
                )
            }
            val writerTask = ioExecutor.submit {
                process.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
                    output.write(encodedRequest)
                }
            }
            writer = writerTask

            val output = awaitAdapter(
                process = process,
                processTree = processTree,
                writer = writerTask,
                stdout = requireNotNull(stdout),
                stderr = requireNotNull(stderr),
                deadlineNanos = deadlineNanos,
            )

            if (process.exitValue() != 0) {
                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_PROCESS_FAILED,
                    message = "fast-check adapter exited with code ${process.exitValue()}: ${output.stderr.text.trim()}",
                )
            }

            if (output.stdout.text.isBlank()) {
                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_RESPONSE_EMPTY,
                    message = "fast-check adapter returned an empty response",
                )
            }

            return output.stdout.text
        } finally {
            processTree.observe()
            stdout?.cancel(true)
            stderr?.cancel(true)
            writer?.cancel(true)

            closeStreams(process)
            terminate(processTree = processTree, deadlineNanos = deadlineNanos)
            ioExecutor.shutdownNow()
        }
    }

    private fun awaitAdapter(
        process: Process,
        processTree: ProjectionProcessTree,
        writer: Future<*>,
        stdout: Future<ProjectionBoundedText>,
        stderr: Future<ProjectionBoundedText>,
        deadlineNanos: Long,
    ): ProjectionAdapterOutput {
        while (true) {
            processTree.observe()
            checkCompletedIo(
                task = stdout,
                operation = "reading fast-check projection stdout",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
            )
            checkCompletedIo(
                task = stderr,
                operation = "reading fast-check projection stderr",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
            )
            checkCompletedIo(
                task = writer,
                operation = "writing the fast-check projection request",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
            )

            val remainingMillis = remainingMillis(deadlineNanos)
            if (remainingMillis <= FORCED_TERMINATION_RESERVE_MILLIS) projectionTimeout()

            val waitMillis = minOf(remainingMillis, PROCESS_POLL_MILLIS)

            val completed = try {
                process.waitFor(waitMillis, TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()

                throw FastCheckProjectionException(
                    code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
                    message = "Interrupted while waiting for the fast-check projection adapter",
                    cause = error,
                )
            }

            if (completed) {
                processTree.observe()

                return awaitIoAfterProcessExit(
                    writer = writer,
                    stdout = stdout,
                    stderr = stderr,
                    deadlineNanos = deadlineNanos,
                )
            }
        }
    }

    private fun awaitIoAfterProcessExit(
        writer: Future<*>,
        stdout: Future<ProjectionBoundedText>,
        stderr: Future<ProjectionBoundedText>,
        deadlineNanos: Long,
    ): ProjectionAdapterOutput {
        while (!writer.isDone || !stdout.isDone || !stderr.isDone) {
            checkCompletedIo(
                task = stdout,
                operation = "reading fast-check projection stdout",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
            )
            checkCompletedIo(
                task = stderr,
                operation = "reading fast-check projection stderr",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
            )
            checkCompletedIo(
                task = writer,
                operation = "writing the fast-check projection request",
                failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
            )

            val remainingMillis = remainingMillis(deadlineNanos)
            if (remainingMillis <= FORCED_TERMINATION_RESERVE_MILLIS) projectionTimeout()

            val waitMillis = minOf(remainingMillis, IO_POLL_MILLIS)

            when {
                !stdout.isDone -> awaitIo(
                    task = stdout,
                    operation = "reading fast-check projection stdout",
                    failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                    waitMillis = waitMillis,
                )

                !stderr.isDone -> awaitIo(
                    task = stderr,
                    operation = "reading fast-check projection stderr",
                    failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                    waitMillis = waitMillis,
                )

                else -> awaitIo(
                    task = writer,
                    operation = "writing the fast-check projection request",
                    failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
                    waitMillis = waitMillis,
                )
            }
        }

        awaitIo(
            task = writer,
            operation = "writing the fast-check projection request",
            failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
            waitMillis = 0,
        )

        return ProjectionAdapterOutput(
            stdout = requireNotNull(
                awaitIo(
                    task = stdout,
                    operation = "reading fast-check projection stdout",
                    failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                    waitMillis = 0,
                ),
            ),
            stderr = requireNotNull(
                awaitIo(
                    task = stderr,
                    operation = "reading fast-check projection stderr",
                    failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
                    waitMillis = 0,
                ),
            ),
        )
    }

    private fun <T> checkCompletedIo(
        task: Future<T>,
        operation: String,
        failureCode: String,
    ) {
        if (task.isDone) {
            awaitIo(
                task = task,
                operation = operation,
                failureCode = failureCode,
                waitMillis = 0,
            )
        }
    }

    private fun <T> awaitIo(
        task: Future<T>,
        operation: String,
        failureCode: String,
        waitMillis: Long,
    ): T? = try {
        task.get(waitMillis, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        null
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()

        throw FastCheckProjectionException(
            code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
            message = "Interrupted while $operation",
            cause = error,
        )
    } catch (error: ExecutionException) {
        val cause = error.cause
        if (cause is ProjectionOutputLimitExceeded) {
            throw FastCheckProjectionException(
                code = PbtDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE,
                message = "fast-check projection ${cause.stream} exceeds ${cause.limit} bytes",
                cause = cause,
            )
        }

        throw FastCheckProjectionException(
            code = failureCode,
            message = "Failed while $operation: ${cause?.message}",
            cause = cause,
        )
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

    private fun projectionTimeout(): Nothing = throw FastCheckProjectionException(
        code = PbtDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
        message = "fast-check projection adapter exceeded the ${transportLimits.wallClockTimeoutMillis} ms timeout",
    )

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

    private fun terminate(processTree: ProjectionProcessTree, deadlineNanos: Long) {
        processTree.observe()
        val processes = processTree.processesInTerminationOrder()
        if (processes.none(ProcessHandle::isAlive)) return

        if (remainingMillis(deadlineNanos) <= FORCED_TERMINATION_RESERVE_MILLIS) {
            processes.destroyForcibly()
            awaitProcessTreeExit(processes, deadlineNanos)

            return
        }

        processes.destroy()

        val gracefulDeadlineNanos = minOf(
            deadlineBefore(
                deadlineNanos = deadlineNanos,
                durationMillis = FORCED_TERMINATION_RESERVE_MILLIS,
            ),
            deadlineAfter(transportLimits.shutdownGraceMillis),
        )
        if (awaitProcessTreeExit(processes, gracefulDeadlineNanos)) return

        processes.destroyForcibly()
        awaitProcessTreeExit(processes, deadlineNanos)
    }

    private fun awaitProcessTreeExit(processes: List<ProcessHandle>, deadlineNanos: Long): Boolean {
        while (true) {
            val liveProcess = processes.firstOrNull(ProcessHandle::isAlive) ?: return true
            val waitMillis = minOf(remainingMillis(deadlineNanos), PROCESS_POLL_MILLIS)
            if (waitMillis == 0L) return false

            try {
                liveProcess.onExit().get(waitMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                continue
            } catch (_: ExecutionException) {
                continue
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()

                return false
            }
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
        const val PROCESS_POLL_MILLIS = 10L
        const val IO_POLL_MILLIS = 10L
        const val FORCED_TERMINATION_RESERVE_MILLIS = 25L

        val DEFAULT_TRANSPORT_LIMITS = FastCheckProjectionTransportLimits(
            maxRequestBytes = DEFAULT_MAX_REQUEST_BYTES,
            maxStdoutBytes = DEFAULT_MAX_STDOUT_BYTES,
            maxStderrBytes = DEFAULT_MAX_STDERR_BYTES,
            wallClockTimeoutMillis = DEFAULT_WALL_CLOCK_TIMEOUT_MILLIS,
            shutdownGraceMillis = DEFAULT_SHUTDOWN_GRACE_MILLIS,
        )
    }
}

private data class ProjectionAdapterOutput(
    val stdout: ProjectionBoundedText,
    val stderr: ProjectionBoundedText,
)

private data class ProjectionBoundedText(val text: String)

private class ProjectionOutputLimitExceeded(
    val stream: String,
    val limit: Int,
) : IOException("fast-check projection $stream exceeds $limit bytes")

private class ProjectionProcessTree(private val root: ProcessHandle) {
    private val processes = linkedMapOf(root.pid() to root)

    fun observe() {
        processes.values.toList().forEach { process ->
            runCatching {
                process.descendants().use { descendants ->
                    descendants.forEach { descendant ->
                        processes.putIfAbsent(descendant.pid(), descendant)
                    }
                }
            }
        }
    }

    fun processesInTerminationOrder(): List<ProcessHandle> = processes.values.sortedBy { process ->
        if (process.pid() == root.pid()) 1 else 0
    }
}

private fun List<ProcessHandle>.destroy() {
    forEach { process ->
        runCatching { process.destroy() }
    }
}

private fun List<ProcessHandle>.destroyForcibly() {
    forEach { process ->
        runCatching { process.destroyForcibly() }
    }
}

private fun InputStream.readProjectionBounded(limit: Int, stream: String): ProjectionBoundedText {
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        val read = read(buffer)
        if (read < 0) break

        val remaining = limit - output.size()

        if (remaining > 0) output.write(buffer, 0, minOf(read, remaining))
        if (read > remaining) throw ProjectionOutputLimitExceeded(stream, limit)
    }

    return ProjectionBoundedText(text = output.toString(Charsets.UTF_8))
}
