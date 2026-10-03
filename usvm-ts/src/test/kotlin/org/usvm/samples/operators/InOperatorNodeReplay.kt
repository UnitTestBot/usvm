package org.usvm.samples.operators

import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NODE_REPLAY_TIMEOUT_SECONDS = 30L

internal fun replayInOperatorScript(
    directory: Path,
    sourcePath: String,
    scriptName: String,
    assertions: String,
) {
    val script = directory.resolve("$scriptName.ts")
    val output = directory.resolve("$scriptName.out")
    script.writeText(buildString {
        appendLine(getResourcePath(sourcePath).readText())
        append(assertions)
    })

    val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
        .redirectErrorStream(true)
        .redirectOutput(output.toFile())
        .start()

    assertTrue(process.waitFor(NODE_REPLAY_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Node replay timed out: $scriptName")
    assertEquals(0, process.exitValue(), output.readText())
}
