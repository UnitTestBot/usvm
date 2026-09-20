package org.usvm.census

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNamespaceSignature
import org.jacodb.ets.model.EtsSourceSpan
import org.usvm.PathSelectionStrategy
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration

internal class RecordingCensusObserver(
    private val project: UnknownCallCensusProject,
    private val projectRoot: Path,
    private val profile: UnknownCallCensusProfile,
    private val entryFunctionId: String,
    private val pathsBySignature: Map<EtsFileSignature, String>,
    private val writer: CensusRecordWriter,
) : TsInterpreterObserver {
    var eventCount: Int = 0
        private set
    var recordingFailureCount: Int = 0
        private set
    var recordingFailureMessage: String? = null
        private set

    @Suppress("TooGenericExceptionCaught")
    override fun onUnknownCall(event: TsUnknownCallEvent) {
        try {
            val containingSignature = event.callSite.location.method.signature
            val functionId = functionId(project.id, projectRoot, containingSignature, pathsBySignature)
            val sourceSpan = event.callSite.location.origin
            val sourceFile = sourcePath(containingSignature, projectRoot, pathsBySignature)
            val siteId = siteId(functionId, sourceSpan, event.callSite.location.index)
            val decision = event.decision.serializedName
            val eventIndex = eventCount + 1

            writer.write(
                buildJsonObject {
                    putCommonProjectFields(project, profile)
                    put("kind", "unknown_call")
                    put("entryFunctionId", entryFunctionId)
                    put("functionId", functionId)
                    put("siteId", siteId)
                    put("sourceFile", sourceFile)
                    sourceSpan?.let { span ->
                        put("startLine", span.startLine)
                        put("startColumn", span.startColumn)
                        put("endLine", span.endLine)
                        put("endColumn", span.endColumn)
                    }
                    put("statementIndex", event.callSite.location.index)
                    put("calleeId", calleeId(projectRoot, event.callee, pathsBySignature))
                    put("calleeName", event.callee.name)
                    put("failureReason", event.failureReason.name)
                    put("decision", decision)
                    put("outcome", event.outcome.name)
                    put("eventIndex", eventIndex)
                }
            )
            eventCount = eventIndex
        } catch (error: Exception) {
            recordingFailureCount++
            if (recordingFailureMessage == null) {
                recordingFailureMessage = "Unknown-call event recording failed: ${boundedError(error)}"
            }
        }
    }
}

internal class CensusRecordWriter(output: Path) : AutoCloseable {
    private val writer: BufferedWriter = Files.newBufferedWriter(
        output,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )

    fun write(record: JsonObject) {
        writer.appendLine(record.toString())
        writer.flush()
    }

    override fun close() {
        writer.close()
    }
}

internal enum class MethodStatus(val serializedName: String) {
    COMPLETED("completed"),
    PARTIAL("partial"),
    TIMEOUT("timeout"),
    TOOL_ERROR("tool_error"),
}

internal data class MethodOutcome(
    val status: MethodStatus,
    val error: String?,
)

internal fun methodOutcomeAfterTimeoutCheck(
    status: MethodStatus,
    error: String?,
    elapsed: Duration,
    timeout: Duration,
): MethodOutcome = if (status == MethodStatus.COMPLETED && elapsed >= timeout) {
    MethodOutcome(
        status = MethodStatus.TIMEOUT,
        error = "Machine timeout reached",
    )
} else {
    MethodOutcome(status = status, error = error)
}

private val TsUnknownCallDecision.serializedName: String
    get() = when (this) {
        is TsUnknownCallDecision.ModelApplied -> "MODEL_APPLIED:$modelId"
        is TsUnknownCallDecision.ResidualFallback -> "RESIDUAL_FALLBACK:${policy.name}"
    }

internal fun JsonObjectBuilderScope.putCommonProjectFields(
    project: UnknownCallCensusProject,
    profile: UnknownCallCensusProfile,
) {
    put("schemaVersion", CENSUS_SCHEMA_VERSION)
    put("projectId", project.id)
    put("projectRevision", project.revision)
    put("profile", profile.name)
}

internal typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder

internal fun functionId(
    projectId: String,
    projectRoot: Path,
    signature: EtsMethodSignature,
    pathsBySignature: Map<EtsFileSignature, String>,
): String {
    val sourceFile = sourcePath(signature, projectRoot, pathsBySignature)
    return "$projectId:$sourceFile:${signatureKey(signature)}"
}

internal fun classId(
    projectId: String,
    projectRoot: Path,
    signature: EtsClassSignature,
    pathsBySignature: Map<EtsFileSignature, String>,
): String {
    val sourceFile = sourcePath(signature.file, projectRoot, pathsBySignature)
    val namespace = signature.namespace?.qualifiedName()
    val className = listOfNotNull(namespace, signature.name).joinToString(separator = "::")
    return "$projectId:$sourceFile:$className"
}

private fun calleeId(
    projectRoot: Path,
    signature: EtsMethodSignature,
    pathsBySignature: Map<EtsFileSignature, String>,
): String {
    val sourceFile = sourcePath(signature, projectRoot, pathsBySignature)
    return "$sourceFile:${signatureKey(signature)}"
}

private fun sourcePath(
    signature: EtsMethodSignature,
    projectRoot: Path,
    pathsBySignature: Map<EtsFileSignature, String>,
): String = sourcePath(signature.enclosingClass.file, projectRoot, pathsBySignature)

private fun sourcePath(
    signature: EtsFileSignature,
    projectRoot: Path,
    pathsBySignature: Map<EtsFileSignature, String>,
): String = pathsBySignature[signature]
    ?: normalizedSourcePath(signature.fileName, projectRoot)

private fun signatureKey(signature: EtsMethodSignature): String {
    val parameters = signature.parameters.joinToString(separator = ",") { parameter -> parameter.type.toString() }
    val namespace = signature.enclosingClass.namespace?.qualifiedName()
    val className = listOfNotNull(namespace, signature.enclosingClass.name).joinToString(separator = "::")
    return "$className:${signature.name}($parameters)->${signature.returnType}"
}

private fun EtsNamespaceSignature.qualifiedName(): String =
    listOfNotNull(namespace?.qualifiedName(), name).joinToString(separator = "::")

private fun siteId(functionId: String, sourceSpan: EtsSourceSpan?, statementIndex: Int): String =
    if (sourceSpan == null) {
        "$functionId:ir-index:$statementIndex"
    } else {
        "$functionId:${sourceSpan.startLine}:${sourceSpan.startColumn}:${sourceSpan.endLine}:${sourceSpan.endColumn}"
    }

private fun normalizedSourcePath(rawPath: String, projectRoot: Path): String {
    val normalizedRawPath = rawPath.normalizedSeparators().removePrefix("@")
    val sourcePath = runCatching { Path.of(rawPath).normalize().absolute() }.getOrNull()
    val normalizedRoot = projectRoot.normalize().absolute()

    return when {
        sourcePath != null && sourcePath.startsWith(normalizedRoot) -> {
            normalizedRoot.relativize(sourcePath).pathString.normalizedSeparators()
        }

        Path.of(rawPath).isAbsolute -> "external/${Path.of(rawPath).name}"
        else -> normalizedRawPath.removePrefix("./")
    }
}

internal fun repositoryRelativeSourcePath(
    rawPath: String,
    sourceRoot: Path,
    sourceRootRelativePath: String,
    projectRoot: Path,
): String {
    val raw = Path.of(rawPath)
    if (raw.isAbsolute) {
        return normalizedSourcePath(rawPath, projectRoot)
    }

    val sourceRelativePath = normalizedSourcePath(rawPath, sourceRoot)
    val normalizedSourceRoot = sourceRootRelativePath.normalizedSeparators().trim('/')
    return listOf(normalizedSourceRoot, sourceRelativePath)
        .filter(String::isNotEmpty)
        .joinToString(separator = "/")
}

private fun String.normalizedSeparators(): String = replace('\\', '/')

internal fun stableSelectionRank(seed: Long, identity: String): String = sha256(
    "$seed\u0000$identity".toByteArray(StandardCharsets.UTF_8)
)

internal fun <T> roundRobin(groups: List<List<T>>): List<T> {
    val maxGroupSize = groups.maxOfOrNull(List<T>::size) ?: return emptyList()
    return buildList {
        repeat(maxGroupSize) { index ->
            groups.forEach { group -> group.getOrNull(index)?.let(::add) }
        }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal fun boundedError(error: Throwable): String {
    val type = error::class.qualifiedName ?: error::class.simpleName ?: "Throwable"
    val message = error.message
        ?.replace('\n', ' ')
        ?.replace(JVM_IDENTITY_SUFFIX, "@<identity>")
        ?.take(MAX_ERROR_LENGTH)
    return if (message.isNullOrBlank()) type else "$type: $message"
}

internal fun combineErrors(primary: String?, additional: String?): String? = when {
    primary == null -> additional
    additional == null -> primary
    else -> "$primary; $additional".take(MAX_ERROR_LENGTH)
}

internal class ProjectTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private const val MAX_ERROR_LENGTH = 1_000
internal const val CENSUS_STOP_ON_COVERAGE = 0
internal val CENSUS_PATH_SELECTION_STRATEGY = PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM
private val JVM_IDENTITY_SUFFIX = Regex("@[0-9a-fA-F]{6,16}")
