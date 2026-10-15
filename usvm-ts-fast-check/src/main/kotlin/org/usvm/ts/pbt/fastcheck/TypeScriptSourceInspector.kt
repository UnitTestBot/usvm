package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Uses the native TypeScript AST when lowered source ranges need validation or replay instrumentation. */
object TypeScriptSourceInspector {
    fun instrumentClassCoverage(source: Path, className: String): TypeScriptClassCoverageInstrumentation {
        val output = inspect(
            source = source,
            operation = "instrument-class-coverage",
            arguments = listOf(className),
        )

        return PropertyManifestJson.json.decodeFromString(output)
    }

    fun localSourceClosure(sourceRoot: Path, source: Path): TypeScriptLocalSourceClosure {
        val output = inspect(
            source = sourceRoot,
            operation = "inspect-local-closure",
            arguments = listOf(source.toString()),
        )

        return PropertyManifestJson.json.decodeFromString(output)
    }

    /** Resolve the graph for direct symbolic startup; this does not attest initializer execution or replay. */
    fun startupSourceClosure(sourceRoot: Path, source: Path): TypeScriptLocalSourceClosure {
        val output = inspect(
            source = sourceRoot,
            operation = "inspect-startup-closure",
            arguments = listOf(source.toString()),
        )

        return PropertyManifestJson.json.decodeFromString(output)
    }

    fun isExportedExpressionArrowBody(
        source: Path,
        exportName: String,
        startOffset: Int,
        endOffset: Int,
        nodeExecutable: String = "node",
    ): Boolean {
        val output = inspect(
            source = source,
            operation = "expression-arrow",
            arguments = listOf(exportName, startOffset.toString(), endOffset.toString()),
            nodeExecutable = nodeExecutable,
        )

        return when (output) {
            "true" -> true
            "false" -> false
            else -> error("TypeScript source inspection returned an invalid response: $output")
        }
    }

    fun captures(
        source: Path,
        exportName: String,
        names: List<String>,
    ): List<TypeScriptSourceCapture> {
        val output = inspect(
            source = source,
            operation = "inspect-captures",
            arguments = listOf(exportName, PropertyManifestJson.json.encodeToString(names)),
        )

        return PropertyManifestJson.json.decodeFromString(output)
    }

    fun instrumentReplay(
        source: Path,
        mode: String,
        startOffset: Int,
        endOffset: Int,
        expressionStartOffset: Int?,
        expressionEndOffset: Int?,
        marker: String,
    ): TypeScriptReplayInstrumentation {
        val output = inspect(
            source = source,
            operation = "instrument-replay",
            arguments = listOf(
                mode,
                startOffset.toString(),
                endOffset.toString(),
                expressionStartOffset?.toString() ?: "-",
                expressionEndOffset?.toString() ?: "-",
                marker,
            ),
        )

        return PropertyManifestJson.json.decodeFromString(output)
    }

    private fun inspect(
        source: Path,
        operation: String,
        arguments: List<String>,
        nodeExecutable: String = "node",
    ): String {
        // Instrumented source may exceed a pipe buffer. Capture to disk while the child runs.
        val outputPath = Files.createTempFile("usvm-ts-source-inspection-", ".json")
        try {
            val command = listOf(
                nodeExecutable,
                FastCheckRuntime.sourceInspectorEntryPoint().toString(),
                operation,
                source.toString(),
            ) + arguments
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputPath.toFile())
                .start()
            if (!process.waitFor(SOURCE_INSPECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
                error("TypeScript source inspection timed out")
            }

            val output = Files.readString(outputPath).trim()
            require(process.exitValue() == 0) {
                "TypeScript source inspection failed with exit ${process.exitValue()}: $output"
            }
            return output
        } finally {
            Files.deleteIfExists(outputPath)
        }
    }

    private const val SOURCE_INSPECTION_TIMEOUT_SECONDS: Long = 10
}

@Serializable
data class TypeScriptReplayInstrumentation(
    val source: String?,
    val reason: String?,
    val origin: String? = null,
) {
    init {
        require((source == null) != (reason == null)) { "Source replay instrumentation needs source or a reason" }
    }
}

@Serializable
enum class TypeScriptCaptureKind { BUILTIN, NUMBER, STRING, BOOLEAN, NULL, UNDEFINED, FUNCTION, OBJECT, UNSUPPORTED }

@Serializable
data class TypeScriptSourceCapture(
    val name: String,
    val kind: TypeScriptCaptureKind,
    val value: String?,
    val fields: List<TypeScriptSourceCapture>,
    val reason: String?,
    val origin: String? = null,
)

@Serializable
data class TypeScriptModuleInitializationIssue(
    val sourceFile: String,
    val startOffset: Int,
    val diagnostic: String,
)

@Serializable
data class TypeScriptLocalSourceClosure(
    val files: List<String>,
    val resolution: TypeScriptFrozenModuleResolution? = null,
    val initializationIssues: List<TypeScriptModuleInitializationIssue> = emptyList(),
    val reasonCode: String? = null,
    val diagnostic: String? = null,
)

@Serializable
data class TypeScriptClassStatementDescriptor(
    val id: String,
    val memberName: String,
    val kind: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)

@Serializable
data class TypeScriptClassCoverageInstrumentation(
    val source: String?,
    val reason: String?,
    val sourceSha256: String,
    val instrumentedSha256: String?,
    val counterGlobalName: String,
    val instrumenterVersion: String,
    val statements: List<TypeScriptClassStatementDescriptor>,
    val inventory: TypeScriptClassSurfaceInventory?,
)

@Serializable
data class TypeScriptClassCallableDescriptor(
    val name: String,
    val kind: String,
    val isStatic: Boolean,
    val parameterNames: List<String>,
    val parameterTypeTexts: List<String?>,
    val resultTypeText: String?,
)

@Serializable
data class TypeScriptClassStateFieldDescriptor(
    val name: String,
    val isEcmaPrivate: Boolean,
    val typeText: String?,
)

@Serializable
data class TypeScriptClassSurfaceInventory(
    val constructorParameterNames: List<String>,
    val publicMembers: List<TypeScriptClassCallableDescriptor>,
    val instanceDataFields: List<TypeScriptClassStateFieldDescriptor>,
    val dataClasses: List<TypeScriptClassDataClassDescriptor> = emptyList(),
)

@Serializable
data class TypeScriptClassDataClassDescriptor(
    val exportName: String,
    val fields: List<String>,
)

/** Runtime closure plus configuration/package/lockfile bytes used to resolve its imports. */
@Serializable
data class TypeScriptFrozenModuleResolution(
    val inputSha256: Map<String, String>,
    val tsconfig: String,
)
