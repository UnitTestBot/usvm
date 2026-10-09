package org.usvm.samples

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

internal fun replayWithNativeGo(
    executable: File,
    testName: String,
    request: JsonElement,
    timeout: Duration,
    environment: Map<String, String> = emptyMap(),
): JsonElement {
    val replayFile = Files.createTempFile("usvm-go-replay-", ".json").toFile()
    val logFile = Files.createTempFile("usvm-go-replay-", ".log").toFile()
    try {
        replayFile.writeText(request.toString())
        val builder = ProcessBuilder(executable.path, "-test.run=^$testName$")
            .redirectErrorStream(true)
            .redirectOutput(logFile)
        builder.environment().putAll(environment)
        builder.environment()["USVM_GO_REPLAY_FILE"] = replayFile.path
        val process = builder.start()
        try {
            val completed = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            assertTrue(completed, message = "Native replay timed out: $testName")
            assertEquals(expected = 0, actual = process.exitValue(), message = logFile.readText())
        } finally {
            process.destroyForcibly()
        }

        return Json.parseToJsonElement(replayFile.readText())
    } finally {
        replayFile.delete()
        logFile.delete()
    }
}
