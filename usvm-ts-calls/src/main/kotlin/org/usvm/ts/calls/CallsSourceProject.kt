package org.usvm.ts.calls

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.ts.pbt.fastcheck.TypeScriptSourceInspector
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal sealed interface CallsSourceProject {
    data class Loaded(
        val scene: EtsScene,
        val entryModule: String,
        val sourceByFile: Map<EtsFileSignature, String>,
        val sourcePaths: Map<EtsFileSignature, String>,
    ) : CallsSourceProject

    data class Unsupported(val issue: CallsIrReadinessIssue) : CallsSourceProject
}

internal fun loadCallsSourceProject(sourceRoot: Path, source: Path): CallsSourceProject {
    val closure = TypeScriptSourceInspector.localSourceClosure(sourceRoot = sourceRoot, source = source)
    if (closure.reasonCode != null) {
        return CallsSourceProject.Unsupported(
            issue = CallsIrReadinessIssue(
                reasonCode = CallsSymbolicPreflightReasonCode.valueOf(requireNotNull(closure.reasonCode)),
                diagnostic = requireNotNull(closure.diagnostic),
            ),
        )
    }

    val entryModule = sourceRoot.relativize(source).toString()
    require(entryModule in closure.files) { "Dependency closure does not contain its entry source" }
    if (closure.files.size == 1 && closure.resolution == null) {
        val entry = loadSingleCallsSource(source)
        return CallsSourceProject.Loaded(
            scene = EtsScene(projectFiles = listOf(entry)),
            entryModule = source.fileName.toString(),
            sourceByFile = mapOf(entry.signature to Files.readString(source)),
            sourcePaths = mapOf(entry.signature to entryModule.replace('\\', '/')),
        )
    }

    val sources = closure.files.associateWith { relative ->
        val path = sourceRoot.resolve(relative).normalize()
        require(path.startsWith(sourceRoot) && path.toRealPath().startsWith(sourceRoot.toRealPath())) {
            "Dependency source escapes its frozen root: $relative"
        }
        Files.readString(path)
    }
    val temporaryProject = Files.createTempDirectory("usvm-calls-source-closure-").toRealPath()
    try {
        copyCallsSourceClosure(root = sourceRoot, destination = temporaryProject, closure = closure)
        val resolution = closure.resolution
        if (resolution != null) {
            Files.writeString(temporaryProject.resolve("tsconfig.json"), resolution.tsconfig)
        }
        val generatedIr = generateEtsIR(
            keepPartialOutputOnFailure = false,
            projectPath = temporaryProject,
            isProject = true,
            loadEntrypoints = false,
            timeout = 30.seconds,
            provider = EtsIrProvider.TS_FRONTEND,
        )
        val scene = try {
            loadEtsProjectFromIR(projectFilesPath = generatedIr, sdkFilesPath = null)
        } finally {
            generatedIr.toFile().deleteRecursively()
        }
        val sourcePaths = scene.projectFiles.associate { file ->
            val filePath = Path.of(file.signature.fileName)
            val relative = if (filePath.isAbsolute) {
                temporaryProject.relativize(filePath).toString()
            } else {
                filePath.toString()
            }
            require(relative in sources) {
                "Frontend source identity is outside the inspected closure: ${file.signature}"
            }
            file.signature to relative
        }
        require(sourcePaths.size == closure.files.size) { "Frontend omitted or duplicated a dependency source" }

        return CallsSourceProject.Loaded(
            scene = scene,
            entryModule = entryModule,
            sourceByFile = sourcePaths.mapValues { (_, relative) -> sources.getValue(relative) },
            sourcePaths = sourcePaths.mapValues { (_, relative) -> relative.replace('\\', '/') },
        )
    } finally {
        temporaryProject.toFile().deleteRecursively()
    }
}

private fun loadSingleCallsSource(source: Path): EtsFile {
    val generatedIr = generateEtsIR(
        keepPartialOutputOnFailure = false,
        projectPath = source,
        useArkAnalyzerTypeInference = 1,
        provider = EtsIrProvider.TS_FRONTEND,
    )

    return try {
        Files.newInputStream(generatedIr).use { stream -> EtsFileDto.loadFromJson(stream).toEtsFile() }
    } finally {
        Files.deleteIfExists(generatedIr)
    }
}
