package org.usvm.ts.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Path

@Serializable
internal enum class CallsReplayStatus {
    @SerialName("confirmed")
    CONFIRMED,

    @SerialName("rejected")
    REJECTED,

    @SerialName("unmapped")
    UNMAPPED,

    @SerialName("ambiguous")
    AMBIGUOUS,

    @SerialName("unsupported")
    UNSUPPORTED,

    @SerialName("timeout")
    TIMEOUT,

    @SerialName("tool-error")
    TOOL_ERROR,
}

@Serializable
internal data class CallsSourcePosition(
    val line: Int,
    val column: Int,
)

@Serializable
internal data class CallsSourceTarget(
    val targetId: String,
    val siteId: String,
    val sourcePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val start: CallsSourcePosition,
    val end: CallsSourcePosition,
)

@Serializable
internal data class CallsSourceReplayResult(
    val status: CallsReplayStatus,
    val invocation: CallsInvocationResult? = null,
    val reason: String? = null,
    val message: String? = null,
)

@Serializable
internal data class CallsInvocationResult(
    val invocation: String,
    val targetHit: Boolean,
    val errorName: String? = null,
    val errorMessage: String? = null,
)

@Serializable
private data class CallsSourceReplayRequest(
    val sourceRoots: List<String>,
    val entryPoint: TypeScriptEntryPoint,
    val inputs: List<JsConcreteValue>,
    val target: CallsSourceTargetWire,
    val timeoutMillis: Long,
)

@Serializable
private data class CallsSourceTargetWire(
    val sourcePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val start: CallsSourcePosition,
    val end: CallsSourcePosition,
)

@Serializable
private data class CallsSourceReplayResponse(
    val status: String,
    val replayStatus: CallsReplayStatus,
    val invocation: CallsInvocationResult? = null,
    val reason: String? = null,
    val message: String? = null,
)

internal fun interface CallsTargetReplayer {
    fun replay(
        sourceRoots: List<Path>,
        entryPoint: TypeScriptEntryPoint,
        inputs: List<JsConcreteValue>,
        target: CallsSourceTarget,
        timeoutMillis: Long,
    ): CallsSourceReplayResult
}

internal class OriginalTypeScriptTargetReplayer(
    private val nodeExecutable: String = "node",
) : CallsTargetReplayer {
    private val transport = CallsProcessTransport(
        nodeExecutable = nodeExecutable,
        maxRequestBytes = MAX_REQUEST_BYTES,
        maxStdoutBytes = MAX_STDOUT_BYTES,
        maxStderrBytes = MAX_STDERR_BYTES,
        shutdownGraceMillis = SHUTDOWN_GRACE_MILLIS,
    )

    override fun replay(
        sourceRoots: List<Path>,
        entryPoint: TypeScriptEntryPoint,
        inputs: List<JsConcreteValue>,
        target: CallsSourceTarget,
        timeoutMillis: Long,
    ): CallsSourceReplayResult {
        require(entryPoint.executionKind == ExecutionKind.SYNC) {
            "Source-target replay currently supports synchronous callables only"
        }
        val request = CallsSourceReplayRequest(
            sourceRoots = sourceRoots.map { root -> root.toRealPath().toString() },
            entryPoint = entryPoint,
            inputs = inputs,
            target = CallsSourceTargetWire(
                sourcePath = target.sourcePath,
                startOffset = target.startOffset,
                endOffset = target.endOffset,
                start = target.start,
                end = target.end,
            ),
            timeoutMillis = timeoutMillis,
        )
        val encoded = PropertyManifestJson.json.encodeToString(request)
        val replayEntryPoint = CallsReplayRuntime.sourceTargetReplayEntryPoint().toString()

        val output = try {
            transport.invoke(
                command = listOf(nodeExecutable, replayEntryPoint),
                request = encoded,
                timeoutMillis = timeoutMillis + TRANSPORT_GRACE_MILLIS,
                reportedTimeoutMillis = timeoutMillis,
                description = "original TypeScript source-target replay",
            )
        } catch (error: CallsTransportException) {
            val status = if (error.code.endsWith("timeout")) {
                CallsReplayStatus.TIMEOUT
            } else {
                CallsReplayStatus.TOOL_ERROR
            }

            return CallsSourceReplayResult(
                status = status,
                message = error.message,
            )
        }

        val stdout = output.stdout
        val response = runCatching {
            PropertyManifestJson.json.decodeFromString<CallsSourceReplayResponse>(stdout)
        }.getOrElse { error ->
            return CallsSourceReplayResult(
                status = CallsReplayStatus.TOOL_ERROR,
                message = "Invalid source-target replay response: ${error.message}",
            )
        }
        if (output.exitCode != 0 || response.status != "ok") {
            return CallsSourceReplayResult(
                status = CallsReplayStatus.TOOL_ERROR,
                reason = response.reason,
                message = response.message ?: output.stderr.trim().ifEmpty { "Source-target replay failed" },
            )
        }

        return CallsSourceReplayResult(
            status = response.replayStatus,
            invocation = response.invocation,
            reason = response.reason,
            message = response.message,
        )
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 4 * 1024 * 1024
        const val MAX_STDOUT_BYTES = 256 * 1024
        const val MAX_STDERR_BYTES = 64 * 1024
        const val SHUTDOWN_GRACE_MILLIS = 250L
        const val TRANSPORT_GRACE_MILLIS = 2_000L
    }
}
