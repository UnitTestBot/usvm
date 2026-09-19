package org.usvm.census

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    fun `bounded process output reports truncation`() {
        val result = boundedProcessOutput(
            command = listOf("sh", "-c", "printf 1234567890"),
            timeout = 2.seconds,
            maxOutputBytes = 5,
        )

        val completed = assertIs<BoundedProcessOutput.Completed>(result)
        assertEquals("12345", completed.output)
        assertTrue(completed.truncated)
    }

    @Test
    fun `bounded process output times out and reaps the process`() {
        val result = boundedProcessOutput(
            command = listOf("sh", "-c", "sleep 30"),
            timeout = 100.milliseconds,
            maxOutputBytes = 1_024,
        )

        assertEquals(BoundedProcessOutput.TimedOut, result)
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
