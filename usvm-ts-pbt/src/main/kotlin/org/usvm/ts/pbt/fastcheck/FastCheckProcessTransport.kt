package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.PbtDiagnosticCode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Completed output of one supervised request-response process. */
internal data class FastCheckProcessOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** Transport failure before a response can be interpreted by a protocol client. */
internal class FastCheckTransportException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Runs one bounded request-response exchange through the shared Node process supervisor.
 *
 * Three I/O tasks are intentional: draining stdout and stderr concurrently prevents pipe deadlocks, while writing
 * stdin separately lets the same wall-clock deadline cover a child that never reads its request.
 */
internal class FastCheckProcessTransport(
    private val nodeExecutable: String,
    private val maxRequestBytes: Int,
    private val maxStdoutBytes: Int,
    private val maxStderrBytes: Int,
    private val shutdownGraceMillis: Long,
) {
    init {
        require(maxRequestBytes > 0) { "Maximum request size must be positive" }
        require(maxStdoutBytes > 0) { "Maximum stdout size must be positive" }
        require(maxStderrBytes > 0) { "Maximum stderr size must be positive" }
        require(shutdownGraceMillis in 1..Int.MAX_VALUE.toLong()) {
            "Shutdown grace period must fit the positive delay range supported by Node timers"
        }
    }

    fun invoke(
        command: List<String>,
        request: String,
        timeoutMillis: Long,
        reportedTimeoutMillis: Long,
        description: String,
    ): FastCheckProcessOutput {
        require(timeoutMillis > 0) { "Process timeout must be positive" }
        require(command.isNotEmpty()) { "Supervised command must not be empty" }
        requireRequestWithinLimit(request, description)

        val deadlineNanos = deadlineAfter(timeoutMillis)
        val managedProcess = startProcess(command, description)
        val executor = Executors.newFixedThreadPool(IO_TASK_COUNT)
        val tasks = startIoTasks(managedProcess.process, request, description, executor)

        try {
            awaitProcess(
                process = managedProcess.process,
                tasks = tasks.all,
                deadlineNanos = deadlineNanos,
                reportedTimeoutMillis = reportedTimeoutMillis,
                description = description,
            )
            awaitIo(
                tasks = tasks,
                deadlineNanos = deadlineNanos,
                reportedTimeoutMillis = reportedTimeoutMillis,
                description = description,
            )
            val stdout = tasks.stdout.completedValue(description)
            val stderr = tasks.stderr.completedValue(description)

            return FastCheckProcessOutput(
                exitCode = managedProcess.process.exitValue(),
                stdout = stdout,
                stderr = stderr,
            )
        } finally {
            tasks.all.forEach { task -> task.cancel() }
            terminate(managedProcess, deadlineNanos)
            closeStreams(managedProcess.process)
            runCatching { Files.deleteIfExists(managedProcess.processGroupFile) }
            executor.shutdownNow()
        }
    }

    private fun requireRequestWithinLimit(request: String, description: String) {
        if (request.toByteArray(Charsets.UTF_8).size > maxRequestBytes) {
            fail(
                code = PbtDiagnosticCode.BACKEND_REQUEST_TOO_LARGE,
                message = "$description request exceeds $maxRequestBytes bytes",
            )
        }
    }

    private fun startIoTasks(
        process: Process,
        request: String,
        description: String,
        executor: ExecutorService,
    ): ProcessIoTasks {
        val stdout = ProcessIoTask(
            future = executor.submit<String> {
                process.inputStream.readBounded(maxStdoutBytes, stream = "stdout")
            },
            operation = "reading $description stdout",
            failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
        )
        val stderr = ProcessIoTask(
            future = executor.submit<String> {
                process.errorStream.readBounded(maxStderrBytes, stream = "stderr")
            },
            operation = "reading $description stderr",
            failureCode = PbtDiagnosticCode.BACKEND_PROCESS_READ_FAILED,
        )
        val writer = ProcessIoTask(
            future = executor.submit<Unit> {
                process.outputStream.bufferedWriter(Charsets.UTF_8).use { output ->
                    output.write(request)
                }
            },
            operation = "writing the $description request",
            failureCode = PbtDiagnosticCode.BACKEND_PROCESS_WRITE_FAILED,
        )

        return ProcessIoTasks(stdout = stdout, stderr = stderr, writer = writer)
    }

    private fun awaitProcess(
        process: Process,
        tasks: List<ProcessIoTask<*>>,
        deadlineNanos: Long,
        reportedTimeoutMillis: Long,
        description: String,
    ) {
        while (true) {
            tasks.forEach { task -> task.throwIfFailed(description) }

            val remainingMillis = remainingMillis(deadlineNanos)
            if (remainingMillis <= FORCED_TERMINATION_RESERVE_MILLIS) {
                timeout(description, reportedTimeoutMillis)
            }

            val completed = try {
                process.waitFor(minOf(remainingMillis, PROCESS_POLL_MILLIS), TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                fail(
                    code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
                    message = "Interrupted while waiting for the $description",
                    cause = error,
                )
            }
            if (completed) return
        }
    }

    private fun awaitIo(
        tasks: ProcessIoTasks,
        deadlineNanos: Long,
        reportedTimeoutMillis: Long,
        description: String,
    ) {
        while (true) {
            tasks.all.forEach { task -> task.throwIfFailed(description) }

            val pendingTask = tasks.all.firstOrNull { task -> !task.isDone } ?: break

            val remainingMillis = remainingMillis(deadlineNanos)
            if (remainingMillis <= FORCED_TERMINATION_RESERVE_MILLIS) {
                timeout(description, reportedTimeoutMillis)
            }

            pendingTask.await(minOf(remainingMillis, IO_POLL_MILLIS), description)
        }

        tasks.all.forEach { task -> task.completedValue(description) }
    }

    private fun startProcess(
        supervisedCommand: List<String>,
        description: String,
    ): SupervisedProcessHandle {
        val processGroupFile = try {
            Files.createTempFile(PROCESS_GROUP_FILE_PREFIX, ".pid")
        } catch (error: IOException) {
            processStartFailure(description, error)
        }
        var processStarted = false

        try {
            val command = buildList {
                add(nodeExecutable)
                add(FastCheckRuntime.processSupervisorEntryPoint().toString())
                add(PROCESS_SUPERVISOR_COMMAND)
                add(shutdownGraceMillis.toString())
                add(processGroupFile.toString())
                addAll(supervisedCommand)
            }
            val process = ProcessBuilder(command).start()
            processStarted = true

            return SupervisedProcessHandle(process = process, processGroupFile = processGroupFile)
        } catch (error: IOException) {
            processStartFailure(description, error)
        } finally {
            if (!processStarted) runCatching { Files.deleteIfExists(processGroupFile) }
        }
    }

    private fun processStartFailure(description: String, error: IOException): Nothing = fail(
        code = PbtDiagnosticCode.BACKEND_PROCESS_START_FAILED,
        message = "Failed to start $description: ${error.message}",
        cause = error,
    )

    private fun terminate(managedProcess: SupervisedProcessHandle, deadlineNanos: Long) {
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
        val processGroupText = runCatching { Files.readString(processGroupFile) }.getOrNull() ?: return
        val processGroupId = processGroupText.trim().toLongOrNull() ?: return
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
        while (process.isAlive) {
            val waitMillis = minOf(remainingMillis(deadlineNanos), PROCESS_POLL_MILLIS)
            if (waitMillis == 0L) return false

            try {
                if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS)) return true
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        return true
    }

    private fun closeStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    private fun timeout(description: String, reportedTimeoutMillis: Long): Nothing = fail(
        code = PbtDiagnosticCode.BACKEND_PROCESS_TIMEOUT,
        message = "$description exceeded the $reportedTimeoutMillis ms timeout",
    )

    private fun fail(code: String, message: String, cause: Throwable? = null): Nothing =
        throw FastCheckTransportException(code = code, message = message, cause = cause)

    private companion object {
        const val IO_TASK_COUNT = 3
        const val PROCESS_SUPERVISOR_COMMAND = "--command"
        const val PROCESS_GROUP_FILE_PREFIX = "usvm-fast-check-process-group-"
        const val PROCESS_POLL_MILLIS = 10L
        const val IO_POLL_MILLIS = 10L
        const val FORCED_TERMINATION_RESERVE_MILLIS = 25L
        const val PROCESS_GROUP_KILL_WAIT_MILLIS = 10L

        val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("windows")
    }
}

private data class SupervisedProcessHandle(
    val process: Process,
    val processGroupFile: Path,
)

private data class ProcessIoTasks(
    val stdout: ProcessIoTask<String>,
    val stderr: ProcessIoTask<String>,
    val writer: ProcessIoTask<Unit>,
) {
    val all: List<ProcessIoTask<*>> = listOf(stdout, stderr, writer)
}

private data class ProcessIoTask<T>(
    val future: Future<T>,
    val operation: String,
    val failureCode: String,
) {
    val isDone: Boolean
        get() = future.isDone

    fun cancel() {
        future.cancel(true)
    }

    fun throwIfFailed(description: String) {
        if (isDone) completedValue(description)
    }

    fun completedValue(description: String): T = requireNotNull(await(waitMillis = 0, description))

    fun await(waitMillis: Long, description: String): T? = try {
        future.get(waitMillis, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        null
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw FastCheckTransportException(
            code = PbtDiagnosticCode.BACKEND_PROCESS_INTERRUPTED,
            message = "Interrupted while $operation",
            cause = error,
        )
    } catch (error: ExecutionException) {
        val cause = error.cause ?: error
        if (cause is ProcessOutputLimitExceeded) {
            throw FastCheckTransportException(
                code = PbtDiagnosticCode.BACKEND_RESPONSE_TOO_LARGE,
                message = "$description ${cause.stream} exceeds ${cause.limit} bytes",
                cause = cause,
            )
        }

        throw FastCheckTransportException(
            code = failureCode,
            message = "Failed while $operation: ${cause.message}",
            cause = cause,
        )
    }
}

private class ProcessOutputLimitExceeded(
    val stream: String,
    val limit: Int,
) : IOException("$stream exceeds $limit bytes")

private fun InputStream.readBounded(limit: Int, stream: String): String {
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    while (true) {
        val read = read(buffer)
        if (read < 0) break

        val remaining = limit - output.size()
        if (remaining > 0) output.write(buffer, 0, minOf(read, remaining))
        if (read > remaining) throw ProcessOutputLimitExceeded(stream, limit)
    }

    return output.toString(Charsets.UTF_8)
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

internal fun saturatedAdd(left: Long, right: Long): Long = if (left > Long.MAX_VALUE - right) {
    Long.MAX_VALUE
} else {
    left + right
}
