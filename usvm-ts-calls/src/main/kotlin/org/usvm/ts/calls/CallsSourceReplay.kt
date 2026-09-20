package org.usvm.ts.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

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
internal enum class CallsSourceTargetMode {
    ENTRY,
    COMPLETED_RETURN,
}

@Serializable
internal data class CallsSourceTarget(
    val targetId: String,
    val siteId: String,
    val sourcePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val start: CallsSourcePosition,
    val end: CallsSourcePosition,
    val mode: CallsSourceTargetMode = CallsSourceTargetMode.ENTRY,
    val returnExpressionStartOffset: Int? = null,
    val returnExpressionEndOffset: Int? = null,
) {
    init {
        require((returnExpressionStartOffset == null) == (returnExpressionEndOffset == null)) {
            "Return expression offsets must be both present or both absent"
        }
        if (mode == CallsSourceTargetMode.ENTRY) {
            require(returnExpressionStartOffset == null) {
                "Entry targets must not declare return expression offsets"
            }
        }
    }
}

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

internal fun interface CallsTargetReplayer {
    fun replay(
        sourceRoots: List<Path>,
        entryPoint: TypeScriptEntryPoint,
        inputs: List<JsConcreteValue>,
        target: CallsSourceTarget,
        timeoutMillis: Long,
    ): CallsSourceReplayResult
}

internal class OriginalTypeScriptTargetReplayer : CallsTargetReplayer {
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

        return runCatching {
            replaySupported(
                sourceRoots = sourceRoots,
                entryPoint = entryPoint,
                inputs = inputs,
                target = target,
                timeoutMillis = timeoutMillis,
            )
        }.getOrElse { error ->
            CallsSourceReplayResult(
                status = if (error is PbtBackendException && error.code.endsWith("timeout")) {
                    CallsReplayStatus.TIMEOUT
                } else {
                    CallsReplayStatus.TOOL_ERROR
                },
                message = error.message,
            )
        }
    }

    private fun replaySupported(
        sourceRoots: List<Path>,
        entryPoint: TypeScriptEntryPoint,
        inputs: List<JsConcreteValue>,
        target: CallsSourceTarget,
        timeoutMillis: Long,
    ): CallsSourceReplayResult {
        val resolved = resolveTarget(sourceRoots = sourceRoots, sourcePath = target.sourcePath)
        val source = Files.readString(resolved.source)
        requireTargetCoordinates(source = source, target = target)
        val workspace = Files.createTempDirectory("usvm-ts-calls-replay-")

        return try {
            val overlayRoot = workspace.resolve("source-overlay")
            val marker = "__usvm_source_target_${UUID.randomUUID().toString().replace('-', '_')}"
            val instrumented = instrumentSource(source = source, target = target, marker = marker)
            createOverlay(
                sourceRoot = resolved.sourceRoot,
                overlayRoot = overlayRoot,
                relativeTarget = resolved.relativeTarget,
                instrumentedSource = instrumented,
            )
            val resultPath = workspace.resolve("invocation.json")
            val wrapperName = ".usvm-source-replay-${UUID.randomUUID()}.ts"
            Files.writeString(
                overlayRoot.resolve(wrapperName),
                replayWrapper(
                    sourcePath = target.sourcePath,
                    exportName = entryPoint.exportName,
                    marker = marker,
                    resultPath = resultPath,
                    targetMode = target.mode,
                ),
            )
            val replayRoots = sourceRoots.mapIndexed { index, root ->
                if (index == resolved.sourceRootIndex) overlayRoot else root
            }
            val replayInputs = inputs.ifEmpty { listOf(JsConcreteValue.Boolean(true)) }
            val property = PropertyDefinition(
                id = PropertyId("calls.source-target-replay"),
                inputs = replayInputs.mapIndexed { index, value ->
                    PropertyInput(name = "input$index", domain = value.exactReplayDomain())
                },
                predicate = TypeScriptEntryPoint(module = wrapperName, exportName = REPLAY_EXPORT),
            )
            val result = FastCheckBackend(sourceRoots = replayRoots).run(
                property = property,
                configuration = PropertyRunConfiguration(
                    seed = 0,
                    numRuns = 1,
                    timeoutMillis = timeoutMillis,
                ),
            )
            if (result.failure?.kind == PropertyFailureKind.TIMEOUT) {
                return CallsSourceReplayResult(status = CallsReplayStatus.TIMEOUT)
            }
            val invocation = if (Files.isRegularFile(resultPath)) {
                CallsExperimentJson.json.decodeFromString<CallsInvocationResult>(Files.readString(resultPath))
            } else {
                return CallsSourceReplayResult(
                    status = CallsReplayStatus.TOOL_ERROR,
                    message = result.failure?.message ?: "Source replay produced no invocation result",
                )
            }
            val status = when (result.status) {
                PropertyRunStatus.SUCCESS -> CallsReplayStatus.CONFIRMED
                PropertyRunStatus.FAILURE -> CallsReplayStatus.REJECTED
            }
            check(invocation.targetHit == (status == CallsReplayStatus.CONFIRMED)) {
                "Source replay result does not match the concrete property outcome"
            }

            CallsSourceReplayResult(status = status, invocation = invocation)
        } finally {
            deleteTree(workspace)
        }
    }

    private fun resolveTarget(sourceRoots: List<Path>, sourcePath: String): ResolvedTarget {
        val relativeTarget = Path.of(sourcePath)
        require(!relativeTarget.isAbsolute && relativeTarget.normalize() == relativeTarget) {
            "Target source path must be normalized and relative"
        }
        val matches = sourceRoots.mapIndexedNotNull { index, root ->
            val sourceRoot = root.toRealPath()
            val candidate = sourceRoot.resolve(relativeTarget).normalize()
            if (!candidate.startsWith(sourceRoot) || !Files.isRegularFile(candidate)) {
                null
            } else {
                ResolvedTarget(
                    sourceRootIndex = index,
                    sourceRoot = sourceRoot,
                    relativeTarget = relativeTarget,
                    source = candidate.toRealPath(),
                )
            }
        }
        require(matches.size == 1) { "Target source path resolved to ${matches.size} files" }

        return matches.single()
    }

    private fun requireTargetCoordinates(source: String, target: CallsSourceTarget) {
        val diagnostic = callsTargetCoordinateDiagnostic(source = source, target = target)
        require(diagnostic == null) { requireNotNull(diagnostic) }
    }

    private fun instrumentSource(
        source: String,
        target: CallsSourceTarget,
        marker: String,
    ): String = when (target.mode) {
        CallsSourceTargetMode.ENTRY -> {
            val markerStatement = ";(globalThis as Record<string, unknown>)[${jsString(marker)}] = true;\n"

            source.substring(0, target.startOffset) + markerStatement + source.substring(target.startOffset)
        }

        CallsSourceTargetMode.COMPLETED_RETURN -> {
            val expressionStart = target.returnExpressionStartOffset
            val expressionEnd = target.returnExpressionEndOffset
            if (expressionStart == null || expressionEnd == null) {
                val markerStatement = ";(globalThis as Record<string, unknown>)[${jsString(marker)}] = true;\n"

                source.substring(0, target.startOffset) + markerStatement + source.substring(target.startOffset)
            } else {
                val expression = source.substring(expressionStart, expressionEnd)
                val wrappedExpression = """
                    ((__usvm_completed_value: any) => {
                      (globalThis as Record<string, unknown>)[${jsString(marker)}] = true;
                      return __usvm_completed_value;
                    })($expression)
                """.trimIndent()

                source.substring(0, expressionStart) + wrappedExpression + source.substring(expressionEnd)
            }
        }
    }

    private fun createOverlay(
        sourceRoot: Path,
        overlayRoot: Path,
        relativeTarget: Path,
        instrumentedSource: String,
    ) {
        var sourceDirectory = sourceRoot
        var overlayDirectory = overlayRoot
        Files.createDirectories(overlayDirectory)
        relativeTarget.forEachIndexed { index, segment ->
            Files.newDirectoryStream(sourceDirectory).use { entries ->
                entries.filter { entry -> entry.fileName != segment }.forEach { entry ->
                    mirrorEntry(source = entry, target = overlayDirectory.resolve(entry.fileName))
                }
            }
            val last = index == relativeTarget.nameCount - 1
            if (last) {
                Files.writeString(overlayDirectory.resolve(segment), instrumentedSource)
            } else {
                sourceDirectory = sourceDirectory.resolve(segment)
                overlayDirectory = overlayDirectory.resolve(segment)
                Files.createDirectory(overlayDirectory)
            }
        }
    }

    private fun mirrorEntry(source: Path, target: Path) {
        val linked = runCatching { Files.createSymbolicLink(target, source.toAbsolutePath()) }.isSuccess
        if (!linked) {
            copyTree(source = source, target = target)
        }
    }

    private fun copyTree(source: Path, target: Path) {
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(target)
            Files.newDirectoryStream(source).use { entries ->
                entries.forEach { entry -> copyTree(source = entry, target = target.resolve(entry.fileName)) }
            }
        } else {
            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES)
        }
    }

    private fun deleteTree(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun replayWrapper(
        sourcePath: String,
        exportName: String,
        marker: String,
        resultPath: Path,
        targetMode: CallsSourceTargetMode,
    ): String = """
        import { writeFileSync } from 'node:fs';
        import * as targetModule from ${jsString("./$sourcePath")};

        const callable = targetModule[${jsString(exportName)}];
        if (typeof callable !== 'function') throw new Error('Replay export is not a function');

        export function $REPLAY_EXPORT(...args: unknown[]): boolean {
          Object.defineProperty(globalThis, ${jsString(marker)}, {
            configurable: true,
            enumerable: false,
            value: false,
            writable: true,
          });
          let invocation: 'returned' | 'threw' = 'returned';
          let caught: unknown;
          try {
            const result = callable(...args);
            if (result !== null && (typeof result === 'object' || typeof result === 'function')
              && typeof (result as { then?: unknown }).then === 'function') {
              void Promise.resolve(result).catch(() => undefined);
              throw new Error('Synchronous replay export returned an awaitable value');
            }
          } catch (error: unknown) {
            invocation = 'threw';
            caught = error;
          }
          const targetObserved = (globalThis as Record<string, unknown>)[${jsString(marker)}] === true;
          const targetHit = targetObserved &&
            (${targetMode == CallsSourceTargetMode.ENTRY} || invocation === 'returned');
          const output: Record<string, unknown> = { invocation, targetHit };
          if (invocation === 'threw') {
            output.errorName = caught instanceof Error ? caught.name : typeof caught;
            output.errorMessage = caught instanceof Error ? caught.message : String(caught);
          }
          writeFileSync(${jsString(resultPath.toString())}, `${'$'}{JSON.stringify(output)}\n`, 'utf8');

          return targetHit;
        }
    """.trimIndent() + "\n"

    private fun jsString(value: String): String = CallsExperimentJson.json.encodeToString(value)

    private data class ResolvedTarget(
        val sourceRootIndex: Int,
        val sourceRoot: Path,
        val relativeTarget: Path,
        val source: Path,
    )

    private companion object {
        const val REPLAY_EXPORT = "replaySourceTarget"
    }
}

private fun JsConcreteValue.exactReplayDomain(): PropertyDomain = when (this) {
    is JsConcreteValue.Array -> if (elements.isEmpty()) {
        ArrayDomain(element = BooleanDomain, minLength = 0, maxLength = 0)
    } else {
        TupleDomain(elements.map { element -> element.exactReplayDomain() })
    }

    else -> ConstantDomain(this)
}

internal fun callsTargetCoordinateDiagnostic(source: String, target: CallsSourceTarget): String? {
    if (target.startOffset !in source.indices || target.endOffset !in 1..source.length) {
        return "Target offsets are outside the frozen source"
    }
    if (target.startOffset >= target.endOffset) {
        return "Target source range must be non-empty"
    }
    if (sourcePositionAt(source = source, offset = target.startOffset) != target.start) {
        return "Target start coordinate does not match its frozen source offset"
    }
    if (sourcePositionAt(source = source, offset = target.endOffset) != target.end) {
        return "Target end coordinate does not match its frozen source offset"
    }

    val expressionStart = target.returnExpressionStartOffset
    val expressionEnd = target.returnExpressionEndOffset
    if (expressionStart != null && expressionEnd != null &&
        (expressionStart < target.startOffset || expressionStart >= expressionEnd || expressionEnd > target.endOffset)
    ) {
        return "Return expression offsets must identify a non-empty range inside the source target"
    }

    return null
}

internal fun sourcePositionAt(source: String, offset: Int): CallsSourcePosition {
    var line = 0
    var column = 0
    var index = 0
    while (index < offset) {
        when (source[index]) {
            '\r' -> {
                line += 1
                column = 0
                if (index + 1 < offset && source[index + 1] == '\n') {
                    index += 1
                }
            }

            '\n', '\u2028', '\u2029' -> {
                line += 1
                column = 0
            }

            else -> {
                column += 1
            }
        }
        index += 1
    }

    return CallsSourcePosition(line = line, column = column)
}
