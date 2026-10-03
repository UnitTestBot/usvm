package org.usvm.samples.operators

import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText

internal fun replayInOperatorScript(
    directory: Path,
    sourcePath: String,
    scriptName: String,
    assertions: String,
) {
    val source = buildString {
        appendLine(getResourcePath(sourcePath).readText())
        append(assertions)
    }

    assertNodeReplay(
        source = source,
        directory = directory,
        name = scriptName,
        timeoutMessage = "Node replay timed out: $scriptName",
    )
}
