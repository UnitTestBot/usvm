package org.usvm.ts.pbt.fastcheck

import java.nio.file.Path

enum class TypeScriptCompletedReturnTargetKind {
    EXPRESSION_ARROW,
    RETURN_STATEMENT,
}

/** Exact TypeScript-AST validation and instrumentation for completed-return source targets. */
object TypeScriptSourceInspector {
    fun completedReturnTargetKind(
        source: Path,
        exportName: String,
        startOffset: Int,
        endOffset: Int,
        expressionStartOffset: Int?,
        expressionEndOffset: Int?,
        nodeExecutable: String = "node",
    ): TypeScriptCompletedReturnTargetKind? {
        val output = invoke(
            operation = INSPECT_COMPLETED_RETURN,
            source = source,
            exportName = exportName,
            startOffset = startOffset,
            endOffset = endOffset,
            expressionStartOffset = expressionStartOffset,
            expressionEndOffset = expressionEndOffset,
            nodeExecutable = nodeExecutable,
        )

        return when (output) {
            "expression-arrow" -> TypeScriptCompletedReturnTargetKind.EXPRESSION_ARROW
            "return-statement" -> TypeScriptCompletedReturnTargetKind.RETURN_STATEMENT
            "unsupported" -> null
            else -> error("TypeScript source inspection returned an invalid response: $output")
        }
    }

    fun instrumentCompletedReturn(
        source: Path,
        exportName: String,
        startOffset: Int,
        endOffset: Int,
        expressionStartOffset: Int?,
        expressionEndOffset: Int?,
        marker: String,
        nodeExecutable: String = "node",
    ): String? {
        val output = invoke(
            operation = INSTRUMENT_COMPLETED_RETURN,
            source = source,
            exportName = exportName,
            startOffset = startOffset,
            endOffset = endOffset,
            expressionStartOffset = expressionStartOffset,
            expressionEndOffset = expressionEndOffset,
            marker = marker,
            nodeExecutable = nodeExecutable,
        )

        if (output == UNSUPPORTED_RESPONSE) return null
        require(output.startsWith(SUCCESS_RESPONSE)) {
            "TypeScript source instrumentation returned an invalid response"
        }

        return output.removePrefix(SUCCESS_RESPONSE)
    }

    private fun invoke(
        operation: String,
        source: Path,
        exportName: String,
        startOffset: Int,
        endOffset: Int,
        expressionStartOffset: Int?,
        expressionEndOffset: Int?,
        nodeExecutable: String,
        marker: String? = null,
    ): String {
        require((expressionStartOffset == null) == (expressionEndOffset == null)) {
            "Return expression offsets must be both present or both absent"
        }
        val command = buildList {
            add(nodeExecutable)
            add(FastCheckRuntime.sourceInspectorEntryPoint().toString())
            add(operation)
            add(source.toString())
            add(exportName)
            add(startOffset.toString())
            add(endOffset.toString())
            add(expressionStartOffset?.toString() ?: MISSING_OFFSET)
            add(expressionEndOffset?.toString() ?: MISSING_OFFSET)
            if (marker != null) add(marker)
        }
        val output = FastCheckProcessTransport(
            nodeExecutable = nodeExecutable,
            maxRequestBytes = MAX_REQUEST_BYTES,
            maxStdoutBytes = MAX_STDOUT_BYTES,
            maxStderrBytes = MAX_STDERR_BYTES,
            shutdownGraceMillis = SHUTDOWN_GRACE_MILLIS,
        ).invoke(
            command = command,
            request = "",
            timeoutMillis = SOURCE_INSPECTION_TIMEOUT_MILLIS,
            reportedTimeoutMillis = SOURCE_INSPECTION_TIMEOUT_MILLIS,
            description = "TypeScript source inspection",
        )
        require(output.exitCode == 0) {
            "TypeScript source inspection failed with exit ${output.exitCode}: ${output.stderr.trim()}"
        }

        return output.stdout
    }

    private const val INSPECT_COMPLETED_RETURN = "inspect-completed-return"
    private const val INSTRUMENT_COMPLETED_RETURN = "instrument-completed-return"
    private const val MISSING_OFFSET = "-"
    private const val UNSUPPORTED_RESPONSE = "unsupported\n"
    private const val SUCCESS_RESPONSE = "ok\n"
    private const val SOURCE_INSPECTION_TIMEOUT_MILLIS = 10_000L
    private const val SHUTDOWN_GRACE_MILLIS = 250L
    private const val MAX_REQUEST_BYTES = 1
    private const val MAX_STDOUT_BYTES = 16 * 1024 * 1024
    private const val MAX_STDERR_BYTES = 64 * 1024
}
