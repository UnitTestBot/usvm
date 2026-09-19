package org.usvm.census

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class UnknownCallCensusRunnerTest {
    @Test
    fun `canonical project checkout accepts a checkout symlink`() {
        val temporaryRoot = createTempDirectory("census-path-test-")
        try {
            val checkoutRoot = temporaryRoot.resolve("checkouts").createDirectories()
            val actualProjectRoot = temporaryRoot.resolve("project").createDirectories()
            Files.createSymbolicLink(checkoutRoot.resolve("project"), actualProjectRoot)

            val canonicalPath = canonicalProjectCheckout(
                checkoutRoot = checkoutRoot,
                relativePath = "project",
            )

            assertEquals(actualProjectRoot.toRealPath(), canonicalPath)
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `canonical project path accepts an in-root directory`() {
        val temporaryRoot = createTempDirectory("census-path-test-")
        try {
            val projectRoot = temporaryRoot.resolve("project").createDirectories()
            val sourceRoot = projectRoot.resolve("src").createDirectories()

            val canonicalPath = canonicalExistingProjectPath(
                projectRoot = projectRoot,
                relativePath = "src",
                kind = "Configured source root",
                requireDirectory = true,
            )

            assertEquals(sourceRoot.toRealPath(), canonicalPath)
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `canonical project path rejects a symbolic-link escape`() {
        val temporaryRoot = createTempDirectory("census-path-test-")
        try {
            val projectRoot = temporaryRoot.resolve("project").createDirectories()
            val externalRoot = temporaryRoot.resolve("external").createDirectories()
            Files.createSymbolicLink(projectRoot.resolve("escaped"), externalRoot)

            val error = assertFailsWith<IllegalArgumentException> {
                canonicalExistingProjectPath(
                    projectRoot = projectRoot,
                    relativePath = "escaped",
                    kind = "Configured source root",
                    requireDirectory = true,
                )
            }

            assertTrue(error.message.orEmpty().contains("symbolic link"))
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `bounded process output drains excess while producer remains alive`() {
        val result = boundedProcessOutput(
            command = listOf(
                "sh",
                "-c",
                "yes 1234567890 | head -n 20000; sleep 0.1; printf done",
            ),
            timeout = 5.seconds,
            maxOutputBytes = 5,
        )

        val completed = assertIs<BoundedProcessOutput.Completed>(result)
        assertEquals(0, completed.exitCode)
        assertEquals("12345", completed.output)
        assertTrue(completed.truncated)
    }

    @Test
    fun `bounded process output times out and reaps the process`() {
        val temporaryRoot = createTempDirectory("census-process-test-")
        try {
            val childPidFile = temporaryRoot.resolve("child.pid")
            val start = TimeSource.Monotonic.markNow()
            val result = boundedProcessOutput(
                command = listOf(
                    "sh",
                    "-c",
                    "sleep 30 & child=\$!; printf %s \"\$child\" > \"\$1\"; wait",
                    "census-timeout-test",
                    childPidFile.toString(),
                ),
                timeout = 500.milliseconds,
                maxOutputBytes = 1_024,
            )

            assertEquals(BoundedProcessOutput.TimedOut, result)
            assertTrue(start.elapsedNow() < 3.seconds)

            val childPid = Files.readString(childPidFile).toLong()
            val childIsAlive = ProcessHandle.of(childPid)
                .map(ProcessHandle::isAlive)
                .orElse(false)
            assertFalse(childIsAlive)
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `elapsed timeout preserves a partial failure`() {
        val outcome = methodOutcomeAfterTimeoutCheck(
            status = MethodStatus.PARTIAL,
            error = "Interpreter step failed",
            elapsed = 6.seconds,
            timeout = 5.seconds,
        )

        assertEquals(MethodStatus.PARTIAL, outcome.status)
        assertEquals("Interpreter step failed", outcome.error)
    }

    @Test
    fun `elapsed timeout marks a completed analysis as timed out`() {
        val outcome = methodOutcomeAfterTimeoutCheck(
            status = MethodStatus.COMPLETED,
            error = null,
            elapsed = 6.seconds,
            timeout = 5.seconds,
        )

        assertEquals(MethodStatus.TIMEOUT, outcome.status)
        assertEquals("Machine timeout reached", outcome.error)
    }
}
