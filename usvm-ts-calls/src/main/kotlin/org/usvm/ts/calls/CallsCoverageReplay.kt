package org.usvm.ts.calls

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.SourceFileCoverage
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.time.TimeSource

@Serializable
internal data class CallsCoverageReplayObservation(
    val inputs: List<JsConcreteValue>,
    val completion: CallsCoverageCompletion,
    val errorName: String? = null,
)

@Serializable
internal data class CallsCoverageReplayResult(
    val coveredStatementKeys: Set<String>,
    val coveredIfArmKeys: Set<String>,
    val supportedIfArmKeys: Set<String>,
    val completion: CallsCoverageCompletion,
    val diagnostics: List<String>,
    val replayElapsedMillis: Long,
)

@Serializable
internal data class CallsCoverageUniverse(
    val supportedStatementKeys: Set<String>,
    val sourceFiles: Set<String>,
    val diagnostics: List<String>,
    val probeElapsedMillis: Long,
)

/** Replays one generated input in a fresh Node.js process and checks its exact transport value. */
internal class OriginalTypeScriptCoverageReplayer(
    sourceRoot: Path,
    private val function: CallsFunctionCase,
) : AutoCloseable {
    private val sourceRoot = sourceRoot.toRealPath()
    private val workspace = Files.createTempDirectory("usvm-ts-calls-coverage-")
    private val overlayRoot = workspace.resolve("source-overlay")
    private val observationPath = workspace.resolve("observation.json")
    private val wrapperName = ".usvm-coverage-${UUID.randomUUID()}.ts"
    private val usesSentinel = function.inputs.isEmpty()
    private val allowedFiles: Set<String>
    private val backend: FastCheckBackend

    init {
        try {
            require(function.entryPoint.executionKind == org.usvm.ts.pbt.model.ExecutionKind.SYNC) {
                "Coverage replay requires a synchronous entry point"
            }
            val source = this.sourceRoot.resolve(function.sourceFile).normalize()
            require(source.startsWith(this.sourceRoot) && Files.isRegularFile(source)) {
                "Coverage entry source is outside its frozen root"
            }
            val closure = TypeScriptSourceInspector.localSourceClosure(sourceRoot = this.sourceRoot, source = source)
            require(closure.reasonCode == null) { closure.diagnostic ?: "Unsupported local source closure" }
            allowedFiles = closure.files.toSet()

            Files.createDirectory(overlayRoot)
            Files.newDirectoryStream(this.sourceRoot).use { entries ->
                entries.forEach { entry ->
                    Files.createSymbolicLink(overlayRoot.resolve(entry.fileName), entry.toAbsolutePath())
                }
            }
            Files.writeString(
                overlayRoot.resolve(wrapperName),
                coverageWrapper(
                    sourceFile = function.sourceFile,
                    exportName = function.entryPoint.exportName,
                    observationPath = observationPath,
                    dropSentinel = usesSentinel,
                ),
            )
            backend = FastCheckBackend(sourceRoots = listOf(overlayRoot, this.sourceRoot))
        } catch (error: Throwable) {
            deleteWorkspace()
            throw error
        }
    }

    fun probeUniverse(timeoutMillis: Long): CallsCoverageUniverse {
        require(timeoutMillis > 0)

        val started = TimeSource.Monotonic.markNow()
        val result = backend.run(
            property = PropertyDefinition(
                id = PropertyId("calls.coverage.universe"),
                inputs = listOf(PropertyInput(name = "__usvmCoverageSentinel", domain = BooleanDomain)),
                predicate = TypeScriptEntryPoint(module = wrapperName, exportName = "coverageUniverseProbe"),
            ),
            configuration = PropertyRunConfiguration(
                seed = 0,
                numRuns = 1,
                timeoutMillis = timeoutMillis,
                examples = listOf(listOf(JsConcreteValue.Boolean(true))),
                coverageRequest = PropertyCoverageRequest(
                    scopes = setOf(CoverageScope.SOURCE_UNDER_TEST),
                    includePatterns = allowedFiles.sorted(),
                    includeUnexecutedSources = true,
                ),
            ),
        )
        val elapsedMillis = started.elapsedNow().inWholeMilliseconds
        require(result.status == PropertyRunStatus.SUCCESS && result.numRuns == 1) {
            "Coverage universe probe did not complete once: $result"
        }

        val coverage = requireNotNull(result.coverage) { "Coverage universe probe produced no c8 artifact" }
        val statements = linkedSetOf<String>()
        val sourceFiles = linkedSetOf<String>()
        coverage.files.forEach { file ->
            val relative = relativeSourcePath(file.path) ?: return@forEach
            if (relative !in allowedFiles) return@forEach

            sourceFiles += relative
            file.statements.forEach { statement ->
                statements += "$relative:statement:${statement.location.stableKey()}"
            }
        }
        require(sourceFiles == allowedFiles) {
            "Coverage universe omitted local source files: ${allowedFiles - sourceFiles}"
        }
        require(statements.isNotEmpty()) { "Coverage universe has no source statements" }

        return CallsCoverageUniverse(
            supportedStatementKeys = statements,
            sourceFiles = sourceFiles,
            diagnostics = coverage.diagnostics.map { diagnostic -> "${diagnostic.code}: ${diagnostic.message}" },
            probeElapsedMillis = elapsedMillis,
        )
    }

    fun replay(inputs: List<JsConcreteValue>, timeoutMillis: Long): CallsCoverageReplayResult {
        require(timeoutMillis > 0)
        require(inputs.size == function.inputs.size)
        Files.deleteIfExists(observationPath)

        val started = TimeSource.Monotonic.markNow()
        val result = backend.run(
            property = PropertyDefinition(
                id = PropertyId("calls.coverage.replay"),
                inputs = if (usesSentinel) {
                    listOf(PropertyInput(name = "__usvmCoverageSentinel", domain = BooleanDomain))
                } else {
                    function.inputs
                },
                predicate = TypeScriptEntryPoint(module = wrapperName, exportName = "coverageProbe"),
            ),
            configuration = PropertyRunConfiguration(
                seed = 0,
                numRuns = 1,
                timeoutMillis = timeoutMillis,
                examples = listOf(if (usesSentinel) listOf(JsConcreteValue.Boolean(true)) else inputs),
                coverageRequest = PropertyCoverageRequest(scopes = setOf(CoverageScope.SOURCE_UNDER_TEST)),
            ),
        )
        val elapsedMillis = started.elapsedNow().inWholeMilliseconds
        require(result.status == PropertyRunStatus.SUCCESS && result.numRuns == 1) {
            "Exact coverage replay did not complete once: $result"
        }
        val observation = CallsExperimentJson.json.decodeFromString<CallsCoverageReplayObservation>(
            Files.readString(observationPath),
        )
        require(observation.inputs == inputs) {
            "Concrete replay received a different tagged input than the symbolic candidate"
        }
        val coverage = requireNotNull(result.coverage) { "Concrete replay produced no c8 coverage artifact" }
        val statementKeys = linkedSetOf<String>()
        val ifArmKeys = linkedSetOf<String>()
        val supportedIfArmKeys = linkedSetOf<String>()

        coverage.files.forEach { file ->
            val relative = relativeSourcePath(file.path) ?: return@forEach
            if (relative !in allowedFiles) return@forEach

            collectHits(
                relative = relative,
                file = file,
                statementKeys = statementKeys,
                ifArmKeys = ifArmKeys,
                supportedIfArmKeys = supportedIfArmKeys,
            )
        }

        return CallsCoverageReplayResult(
            coveredStatementKeys = statementKeys,
            coveredIfArmKeys = ifArmKeys,
            supportedIfArmKeys = supportedIfArmKeys,
            completion = observation.completion,
            diagnostics = coverage.diagnostics.map { diagnostic -> "${diagnostic.code}: ${diagnostic.message}" },
            replayElapsedMillis = elapsedMillis,
        )
    }

    private fun relativeSourcePath(path: String): String? {
        val sourcePath = Path.of(path).toAbsolutePath().normalize()
        val root = when {
            sourcePath.startsWith(overlayRoot) -> overlayRoot
            sourcePath.startsWith(sourceRoot) -> sourceRoot
            else -> return null
        }

        return root.relativize(sourcePath).toString().replace('\\', '/')
    }

    override fun close() = deleteWorkspace()

    private fun deleteWorkspace() {
        Files.walk(workspace).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

private fun collectHits(
    relative: String,
    file: SourceFileCoverage,
    statementKeys: MutableSet<String>,
    ifArmKeys: MutableSet<String>,
    supportedIfArmKeys: MutableSet<String>,
) {
    file.statements.filter { statement -> statement.hits > 0 }.forEach { statement ->
        statementKeys += "$relative:statement:${statement.location.stableKey()}"
    }

    file.branches.filter { branch -> branch.type == "if" && branch.arms.size == 2 }.forEach { branch ->
        branch.arms.forEachIndexed { index, arm ->
            val key = "$relative:if:${branch.location.stableKey()}:$index"
            supportedIfArmKeys += key
            if (arm.hits > 0) ifArmKeys += key
        }
    }
}

private fun org.usvm.ts.pbt.backend.SourceRange.stableKey(): String =
    "${start.line}:${start.column}-${end.line}:${end.column}"

private fun coverageWrapper(
    sourceFile: String,
    exportName: String,
    observationPath: Path,
    dropSentinel: Boolean,
): String {
    val moduleName = CallsExperimentJson.json.encodeToUtf8SafeString("./$sourceFile")
    val exportKey = CallsExperimentJson.json.encodeToUtf8SafeString(exportName)
    val outputPath = CallsExperimentJson.json.encodeToUtf8SafeString(observationPath.toString())
    val sentinelExpression = if (dropSentinel) "args.slice(1)" else "args"

    return """
        import { writeFileSync } from 'node:fs';
        import * as targetModule from $moduleName;

        const callable = targetModule[$exportKey];
        if (typeof callable !== 'function') throw new Error('Coverage export is not a function');

        function encodeValue(value: unknown): unknown {
          if (value === undefined) return { kind: 'undefined' };
          if (value === null) return { kind: 'null' };
          if (typeof value === 'boolean') return { kind: 'boolean', value };
          if (typeof value === 'string') return { kind: 'string', value };
          if (typeof value === 'number') {
            if (Number.isNaN(value)) return { kind: 'number', value: 'nan' };
            if (value === Infinity) return { kind: 'number', value: 'positive-infinity' };
            if (value === -Infinity) return { kind: 'number', value: 'negative-infinity' };
            const view = new DataView(new ArrayBuffer(8));
            view.setFloat64(0, value, false);
            const bits = view.getBigUint64(0, false).toString(16).padStart(16, '0');
            return { kind: 'number', value: 'finite', bits };
          }
          if (Array.isArray(value)) return { kind: 'array', elements: value.map(encodeValue) };
          throw new Error('Unsupported replay argument type');
        }

        export function coverageProbe(...args: unknown[]): boolean {
          const actualArgs = $sentinelExpression;
          let completion: 'RETURNED' | 'THREW' = 'RETURNED';
          let errorName: string | undefined;
          try {
            const result = callable(...actualArgs);
            if (result !== null && (typeof result === 'object' || typeof result === 'function')
              && typeof (result as { then?: unknown }).then === 'function') {
              throw new Error('Synchronous coverage export returned an awaitable value');
            }
          } catch (error: unknown) {
            completion = 'THREW';
            errorName = error instanceof Error ? error.name : typeof error;
          }
          writeFileSync($outputPath, JSON.stringify({ inputs: actualArgs.map(encodeValue), completion, errorName }), 'utf8');

          return true;
        }

        export function coverageUniverseProbe(_sentinel: boolean): boolean {
          return true;
        }
    """.trimIndent() + "\n"
}
