package org.usvm.census

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import java.nio.file.Path

internal fun selectCensusMethods(
    manifest: UnknownCallCensusManifest,
    project: UnknownCallCensusProject,
    projectRoot: Path,
    loadedFiles: LoadedProjectFiles,
): CensusMethodSelection {
    val candidateFiles = loadedFiles.files.asSequence()
        .map { file -> file to requireNotNull(loadedFiles.pathsBySignature[file.signature]) }
        .filter { (_, fileName) -> project.includeSuffixes.any(fileName::endsWith) }
        .filterNot { (_, fileName) -> project.excludeSuffixes.any(fileName::endsWith) }
        .distinctBy { (_, fileName) -> fileName }
        .sortedBy { (_, fileName) -> fileName }
        .map { (file, _) -> file }
        .toList()

    val eligibleClasses = candidateFiles
        .flatMap { file -> file.allClasses }
        .map { clazz ->
            val classId = classId(project.id, projectRoot, clazz.signature, loadedFiles.pathsBySignature)
            val methods = clazz.methods
                .asSequence()
                .filterNot { method -> method.cfg.stmts.isEmpty() }
                .filterNot { method -> method.name.startsWith(ANONYMOUS_METHOD_PREFIX) }
                .filterNot { method -> method.name == DEFAULT_ARK_METHOD_NAME }
                .filterNot { method -> method.name == INSTANCE_INIT_METHOD_NAME }
                .filterNot { method -> method.name == STATIC_INIT_METHOD_NAME }
                .sortedWith(
                    compareBy(
                        { method ->
                            stableSelectionRank(
                                seed = manifest.randomSeed,
                                identity = functionId(
                                    project.id,
                                    projectRoot,
                                    method.signature,
                                    loadedFiles.pathsBySignature,
                                ),
                            )
                        },
                        { method ->
                            functionId(
                                project.id,
                                projectRoot,
                                method.signature,
                                loadedFiles.pathsBySignature,
                            )
                        },
                    )
                )
                .toList()

            SelectedClass(classId = classId, methods = methods)
        }
        .filter { selectedClass ->
            selectedClass.methods.count { method ->
                method.cfg.stmts.size >= manifest.limits.minStatementsPerMethod
            } >= manifest.limits.minMethodsPerClass
        }

    val selectedClasses = eligibleClasses
        .sortedWith(
            compareBy(
                { selectedClass -> stableSelectionRank(manifest.randomSeed, selectedClass.classId) },
                SelectedClass::classId,
            )
        )
        .take(manifest.limits.maxClasses)
    val selectedMethods = roundRobin(selectedClasses.map(SelectedClass::methods))
        .take(manifest.limits.maxMethods)

    return CensusMethodSelection(
        candidateFiles = candidateFiles.size,
        eligibleClasses = eligibleClasses.size,
        selectedClasses = selectedClasses.size,
        methods = selectedMethods,
    )
}

private data class SelectedClass(
    val classId: String,
    val methods: List<EtsMethod>,
)

internal data class CensusMethodSelection(
    val candidateFiles: Int,
    val eligibleClasses: Int,
    val selectedClasses: Int,
    val methods: List<EtsMethod>,
)
