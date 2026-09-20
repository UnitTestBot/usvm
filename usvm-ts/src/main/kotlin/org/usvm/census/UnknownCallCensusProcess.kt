package org.usvm.census

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal fun canonicalProjectCheckout(checkoutRoot: Path, relativePath: String): Path {
    val canonicalCheckoutRoot = checkoutRoot.toRealPath()
    val configuredProjectRoot = canonicalCheckoutRoot.resolve(relativePath).normalize()
    require(configuredProjectRoot.startsWith(canonicalCheckoutRoot)) {
        "Project checkout escapes the configured checkout root: $configuredProjectRoot"
    }
    require(configuredProjectRoot.isDirectory()) {
        "Project checkout does not exist: $configuredProjectRoot"
    }

    return configuredProjectRoot.toRealPath()
}

internal fun canonicalExistingProjectPath(
    projectRoot: Path,
    relativePath: String,
    kind: String,
    requireDirectory: Boolean,
): Path {
    val canonicalProjectRoot = projectRoot.toRealPath()
    val configuredPath = canonicalProjectRoot.resolve(relativePath).normalize()
    require(configuredPath.startsWith(canonicalProjectRoot)) {
        "$kind escapes project checkout: $configuredPath"
    }
    require(configuredPath.exists()) { "$kind does not exist: $relativePath" }
    if (requireDirectory) {
        require(configuredPath.isDirectory()) { "$kind is not a directory: $configuredPath" }
    }

    val canonicalPath = configuredPath.toRealPath()
    require(canonicalPath.startsWith(canonicalProjectRoot)) {
        "$kind escapes project checkout through a symbolic link: $configuredPath"
    }

    return canonicalPath
}

internal sealed interface BoundedProcessOutput {
    data class Completed(
        val exitCode: Int,
        val output: String,
        val truncated: Boolean,
    ) : BoundedProcessOutput

    data object TimedOut : BoundedProcessOutput
}

internal fun boundedProcessOutput(
    command: List<String>,
    timeout: Duration,
    maxOutputBytes: Int,
): BoundedProcessOutput {
    require(timeout > Duration.ZERO) { "Process timeout must be positive" }
    require(maxOutputBytes > 0) { "Process output limit must be positive" }

    var process: Process? = null
    var outputReader: Thread? = null
    try {
        val processStart = TimeSource.Monotonic.markNow()
        val startedProcess = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        process = startedProcess
        startedProcess.outputStream.close()

        val outputCollector = BoundedOutputCollector(maxOutputBytes)
        val startedOutputReader = Thread(
            { outputCollector.drain(startedProcess.inputStream) },
            "unknown-call-census-output-reader",
        ).apply {
            isDaemon = true
            start()
        }
        outputReader = startedOutputReader

        val remaining = timeout - processStart.elapsedNow()
        val completed = remaining > Duration.ZERO && startedProcess.waitFor(
            remaining.inWholeMilliseconds.coerceAtLeast(minimumValue = 1),
            TimeUnit.MILLISECONDS,
        )
        if (!completed) {
            terminateProcessTreeBestEffort(startedProcess)
            closeProcessOutputBestEffort(startedProcess)
            joinBestEffort(startedOutputReader, PROCESS_TERMINATION_GRACE)
            return BoundedProcessOutput.TimedOut
        }

        val outputRead = joinWithinTimeout(
            thread = startedOutputReader,
            timeout = timeout - processStart.elapsedNow(),
        )
        if (!outputRead) {
            closeProcessOutputBestEffort(startedProcess)
            joinBestEffort(startedOutputReader, PROCESS_TERMINATION_GRACE)
            return BoundedProcessOutput.TimedOut
        }

        outputCollector.failure?.let { throw it }
        return BoundedProcessOutput.Completed(
            exitCode = startedProcess.exitValue(),
            output = outputCollector.output(),
            truncated = outputCollector.truncated,
        )
    } finally {
        process?.takeIf(Process::isAlive)?.let(::terminateProcessTreeBestEffort)
        process?.let(::closeProcessOutputBestEffort)
        outputReader?.takeIf(Thread::isAlive)?.let { reader ->
            joinBestEffort(reader, PROCESS_TERMINATION_GRACE)
        }
    }
}

private class BoundedOutputCollector(private val maxOutputBytes: Int) {
    private val retainedOutput = ByteArray(maxOutputBytes)
    private var retainedBytes = 0

    var truncated: Boolean = false
        private set

    var failure: Exception? = null
        private set

    fun drain(input: InputStream) {
        val buffer = ByteArray(PROCESS_OUTPUT_BUFFER_BYTES)
        try {
            input.use {
                var readBytes = it.read(buffer)
                while (readBytes >= 0) {
                    retain(buffer, readBytes)
                    readBytes = it.read(buffer)
                }
            }
        } catch (error: IOException) {
            failure = error
        }
    }

    private fun retain(buffer: ByteArray, readBytes: Int) {
        val retainedFromChunk = minOf(readBytes, maxOutputBytes - retainedBytes)
        if (retainedFromChunk > 0) {
            buffer.copyInto(
                destination = retainedOutput,
                destinationOffset = retainedBytes,
                endIndex = retainedFromChunk,
            )
            retainedBytes += retainedFromChunk
        }
        if (retainedFromChunk < readBytes) {
            truncated = true
        }
    }

    fun output(): String = retainedOutput.copyOf(retainedBytes).toString(StandardCharsets.UTF_8)
}

private fun joinWithinTimeout(thread: Thread, timeout: Duration): Boolean {
    if (timeout <= Duration.ZERO) {
        return !thread.isAlive
    }

    thread.join(timeout.inWholeMilliseconds.coerceAtLeast(minimumValue = 1))
    return !thread.isAlive
}

private fun joinBestEffort(thread: Thread, timeout: Duration) {
    try {
        joinWithinTimeout(thread, timeout)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

private fun closeProcessOutputBestEffort(process: Process) {
    runCatching { process.inputStream.close() }
}

private fun terminateProcessTree(process: Process) {
    val descendants = process.toHandle().descendants().use { handles ->
        handles.iterator().asSequence().toList()
    }
    val processTree = listOf(process.toHandle()) + descendants
    processTree.asReversed().forEach(ProcessHandle::destroy)

    if (!awaitTermination(processTree, PROCESS_TERMINATION_GRACE)) {
        processTree.asReversed()
            .filter(ProcessHandle::isAlive)
            .forEach(ProcessHandle::destroyForcibly)
        awaitTermination(processTree, PROCESS_TERMINATION_GRACE)
    }

    process.waitFor(PROCESS_TERMINATION_GRACE.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

private fun terminateProcessTreeBestEffort(process: Process) {
    try {
        terminateProcessTree(process)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        process.destroyForcibly()
    } catch (_: Exception) {
        process.destroyForcibly()
    }
}

private fun awaitTermination(processes: List<ProcessHandle>, timeout: Duration): Boolean {
    val exits = processes.map(ProcessHandle::onExit).toTypedArray()
    return try {
        CompletableFuture.allOf(*exits).get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        true
    } catch (_: TimeoutException) {
        false
    } catch (_: ExecutionException) {
        false
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
}

private val PROCESS_TERMINATION_GRACE = 500.milliseconds
private const val PROCESS_OUTPUT_BUFFER_BYTES = 8 * 1024
