package org.usvm.ts.pbt.fastcheck

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Exact TypeScript-AST validation used when EtsIR omits an expression-bodied arrow's source origin. */
object TypeScriptSourceInspector {
    fun isExportedExpressionArrowBody(
        source: Path,
        exportName: String,
        startOffset: Int,
        endOffset: Int,
        nodeExecutable: String = "node",
    ): Boolean {
        val process = ProcessBuilder(
            nodeExecutable,
            FastCheckRuntime.sourceInspectorEntryPoint().toString(),
            source.toString(),
            exportName,
            startOffset.toString(),
            endOffset.toString(),
        ).redirectErrorStream(true).start()
        if (!process.waitFor(SOURCE_INSPECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("TypeScript source inspection timed out")
        }

        val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }.trim()
        require(process.exitValue() == 0) {
            "TypeScript source inspection failed with exit ${process.exitValue()}: $output"
        }
        return when (output) {
            "true" -> true
            "false" -> false
            else -> error("TypeScript source inspection returned an invalid response: $output")
        }
    }

    private const val SOURCE_INSPECTION_TIMEOUT_SECONDS: Long = 10
}
