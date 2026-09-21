package org.usvm.ts.calls

import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.jacodb.ets.utils.loadEtsFileAutoConvert
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
    if (closure.files.size == 1) {
        val entry = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        return CallsSourceProject.Loaded(
            scene = EtsScene(projectFiles = listOf(entry)),
            entryModule = source.fileName.toString(),
            sourceByFile = mapOf(entry.signature to Files.readString(source)),
        )
    }

    val sources = closure.files.associateWith { relative ->
        val path = sourceRoot.resolve(relative).normalize()
        require(path.startsWith(sourceRoot) && path.toRealPath().startsWith(sourceRoot.toRealPath())) {
            "Dependency source escapes its frozen root: $relative"
        }
        Files.readString(path)
    }
    val temporaryProject = Files.createTempDirectory("usvm-calls-source-closure-")
    try {
        for (relative in closure.files) {
            val copy = temporaryProject.resolve(relative)
            Files.createDirectories(copy.parent)
            Files.copy(sourceRoot.resolve(relative), copy)
        }
        val generatedIr = generateEtsIR(
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
        val sourceByFile = scene.projectFiles.associate { file ->
            val filePath = Path.of(file.signature.fileName)
            val relative = if (filePath.isAbsolute) {
                temporaryProject.relativize(filePath).toString()
            } else {
                filePath.toString()
            }
            file.signature to requireNotNull(sources[relative]) {
                "Frontend source identity is outside the inspected closure: ${file.signature}"
            }
        }
        require(sourceByFile.size == closure.files.size) { "Frontend omitted or duplicated a dependency source" }

        return CallsSourceProject.Loaded(scene = scene, entryModule = entryModule, sourceByFile = sourceByFile)
    } finally {
        temporaryProject.toFile().deleteRecursively()
    }
}
