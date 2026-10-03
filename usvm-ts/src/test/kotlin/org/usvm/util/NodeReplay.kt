package org.usvm.util

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NODE_REPLAY_TIMEOUT_SECONDS = 10L

fun jsString(value: String): String = value.map { "\\u%04x".format(it.code) }.joinToString(
    separator = "",
    prefix = "\"",
    postfix = "\"",
)

fun assertNodeReplay(
    source: String,
    directory: Path,
    name: String,
    timeoutMessage: String,
    failureContext: String = source,
) {
    val script = directory.resolve("$name.ts")
    val output = directory.resolve("$name.out")
    script.writeText(source)

    val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
        .redirectErrorStream(true)
        .redirectOutput(output.toFile())
        .start()

    try {
        assertTrue(process.waitFor(NODE_REPLAY_TIMEOUT_SECONDS, TimeUnit.SECONDS), timeoutMessage)
        assertEquals(0, process.exitValue(), "${output.readText()}\n$failureContext")
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}
