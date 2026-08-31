package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.util.IdentityHashMap

/** Resolves TypeScript runtime exports to callable EtsIR methods and their stack bindings. */
internal class EtsEntryPointResolver(
    private val scene: EtsScene,
    private val sourceLocations: SourceLocationNormalizer,
) {
    fun resolve(
        entryPoint: TypeScriptEntryPoint,
        manifest: PropertyManifest,
    ): EtsMappingResult<EtsEntryPointTarget> {
        if (sourceLocations.sourceRootDiagnostics.isNotEmpty()) {
            return EtsMappingResult(
                status = EtsMappingStatus.UNSUPPORTED,
                targets = emptyList(),
                diagnostics = sourceLocations.sourceRootDiagnostics,
            )
        }

        val sourceCandidates = scene.projectFiles.filter { candidate -> candidate.matches(entryPoint.module) }
        val candidateResolutions = sourceCandidates.map { file ->
            resolveExportedMethods(file, entryPoint.exportName, visited = emptySet())
        }
        val methods = candidateResolutions
            .flatMap { resolution -> resolution.methods }
            .distinctByIdentity()
        val hasAmbiguousResolution = candidateResolutions.any { resolution -> resolution.isAmbiguous } ||
            sourceCandidates.size > 1

        if (methods.any { method -> method.parameters.size != manifest.inputs.size }) {
            return EtsMappingResult(
                status = EtsMappingStatus.UNSUPPORTED,
                targets = emptyList(),
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = PbtDiagnosticCode.MAPPING_ENTRY_POINT_BINDINGS_UNSUPPORTED,
                        message = "Property inputs do not match EtsIR parameters for ${entryPoint.exportName}",
                        sourcePath = entryPoint.module,
                    ),
                ),
            )
        }

        val targets = methods.map { method ->
            EtsEntryPointTarget(
                method = method,
                bindings = method.bindingsFor(manifest),
            )
        }

        if (targets.size == 1 && !hasAmbiguousResolution) {
            return EtsMappingResult(
                status = EtsMappingStatus.EXACT,
                targets = targets,
            )
        }
        if (targets.isNotEmpty()) {
            return EtsMappingResult(
                status = EtsMappingStatus.AMBIGUOUS,
                targets = targets,
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = PbtDiagnosticCode.MAPPING_ENTRY_POINT_AMBIGUOUS,
                        message = "Several EtsIR methods, source candidates, or export links match " +
                            "${entryPoint.module}#${entryPoint.exportName}",
                        sourcePath = entryPoint.module,
                    ),
                ),
            )
        }

        return EtsMappingResult(
            status = EtsMappingStatus.UNMAPPED,
            targets = emptyList(),
            diagnostics = listOf(
                EtsMappingDiagnostic(
                    code = PbtDiagnosticCode.MAPPING_ENTRY_POINT_UNMAPPED,
                    message = "No EtsIR method matches ${entryPoint.module}#${entryPoint.exportName}",
                    sourcePath = entryPoint.module,
                ),
            ),
        )
    }

    private fun resolveExportedMethods(
        file: EtsFile,
        exportName: String,
        visited: Set<EtsFile>,
    ): MethodResolution {
        if (file in visited) return MethodResolution.EMPTY

        val runtimeExports = file.exportInfos.filter { export ->
            !export.isTypeOnly && export.type != EtsExportType.TYPE
        }
        val namedRuntimeExports = runtimeExports.filter { export ->
            export.runtimeName == exportName
        }
        // TypeScript gives an explicit named export precedence over fallback exports from `export *`.
        val matchingExports = namedRuntimeExports.ifEmpty {
            runtimeExports.filter { export ->
                export.isBareStarReExport && exportName != DEFAULT_EXPORT_NAME
            }
        }
        val directMethodNames = matchingExports
            .filter { export -> export.type == EtsExportType.METHOD && !export.isReExport }
            .map { export -> export.originalName }
        val directMethods = file.classes
            .filter { etsClass -> etsClass.name == DEFAULT_ARK_CLASS_NAME }
            .flatMap { etsClass -> etsClass.methods }
            .filter { method -> method.name in directMethodNames }
        val localResolutions = matchingExports
            .filter { export -> export.type == EtsExportType.LOCAL && !export.isReExport }
            .map { export -> resolveCallableLocal(file, export.originalName) }
        val reExportedResolutions = matchingExports
            .filter { export -> export.isReExport && !export.isNamespaceStarReExport }
            .flatMap { export ->
                val targetExportName = if (export.isBareStarReExport) exportName else export.originalName
                val targetFiles = resolveReExportFiles(file, requireNotNull(export.from))

                targetFiles.map { targetFile ->
                    val resolution = resolveExportedMethods(targetFile, targetExportName, visited + file)

                    resolution.copy(isAmbiguous = resolution.isAmbiguous || targetFiles.size > 1)
                }
            }
        val methods = (
            directMethods +
                localResolutions.flatMap { resolution -> resolution.methods } +
                reExportedResolutions.flatMap { resolution -> resolution.methods }
            ).distinctByIdentity()

        return MethodResolution(
            methods = methods,
            isAmbiguous = localResolutions.any { resolution -> resolution.isAmbiguous } ||
                reExportedResolutions.any { resolution -> resolution.isAmbiguous },
        )
    }

    private fun resolveCallableLocal(file: EtsFile, localName: String): MethodResolution {
        // The frontend lowers a callable local to a static-field assignment whose function signature identifies
        // the lifted anonymous method. Keep every link so repeated or partial lowering remains visibly ambiguous.
        val assignments = file.classes
            .filter { etsClass -> etsClass.name == DEFAULT_ARK_CLASS_NAME }
            .flatMap { defaultClass ->
                defaultClass.methods
                    .filter { method -> method.name == DEFAULT_ARK_METHOD_NAME }
                    .flatMap { method -> method.cfg.stmts }
                    .filterIsInstance<EtsAssignStmt>()
                    .mapNotNull { assignment ->
                        val field = assignment.lhv as? EtsStaticFieldRef ?: return@mapNotNull null
                        if (field.field.enclosingClass != defaultClass.signature || field.field.name != localName) {
                            return@mapNotNull null
                        }

                        defaultClass to assignment
                    }
            }
        val callableAssignments = assignments.mapNotNull { (defaultClass, assignment) ->
            val local = assignment.rhv as? EtsLocal ?: return@mapNotNull null
            val functionType = local.type as? EtsFunctionType ?: return@mapNotNull null

            CallableLocalAssignment(
                defaultClass = defaultClass,
                functionSignature = functionType.signature,
            )
        }
        val linkedMethods = callableAssignments.flatMap { assignment ->
            assignment.defaultClass.methods.filter { method ->
                method.name.startsWith(ANONYMOUS_METHOD_PREFIX) &&
                    method.signature == assignment.functionSignature
            }
        }
        val methods = linkedMethods.distinctByIdentity()
        val isExactLink = assignments.size == 1 && callableAssignments.size == 1 && linkedMethods.size == 1

        return MethodResolution(
            methods = methods,
            isAmbiguous = methods.isNotEmpty() && !isExactLink,
        )
    }

    private fun resolveReExportFiles(file: EtsFile, module: String): List<EtsFile> {
        val targetPaths = sourceLocations.normalizePath(file.name).flatMapTo(linkedSetOf()) { sourcePath ->
            val targetPath = requireNotNull(sourcePath.parent).resolve(module).normalize()

            sourceLocations.modulePathCandidates(targetPath)
        }

        return scene.projectFiles.filter { candidate ->
            sourceLocations.normalizePath(candidate.name).any(targetPaths::contains)
        }
    }

    private fun EtsFile.matches(module: String): Boolean {
        val modulePaths = sourceLocations.normalizePath(module).flatMapTo(linkedSetOf()) { path ->
            sourceLocations.modulePathCandidates(path)
        }
        val filePaths = sourceLocations.normalizePath(name)

        return modulePaths.any(filePaths::contains)
    }

    private fun EtsMethod.bindingsFor(manifest: PropertyManifest): EtsEntryPointBindings {
        val receiverType = EtsClassType(
            signature = signature.enclosingClass,
            typeParameters = requireNotNull(enclosingClass).typeParameters,
        )
        val inputBindings = manifest.inputs.zip(parameters).mapIndexed { index, (input, parameter) ->
            EtsInputBinding(
                propertyInputName = input.name,
                parameter = parameter,
                stackSlot = index + RECEIVER_STACK_SLOTS,
            )
        }

        return EtsEntryPointBindings(
            receiver = EtsReceiverBinding(
                stackSlot = RECEIVER_STACK_SLOT,
                type = receiverType,
            ),
            inputs = inputBindings,
            result = EtsResultBinding(type = returnType),
        )
    }
}

private data class CallableLocalAssignment(
    val defaultClass: EtsClass,
    val functionSignature: EtsMethodSignature,
)

private data class MethodResolution(
    val methods: List<EtsMethod>,
    val isAmbiguous: Boolean = false,
) {
    companion object {
        val EMPTY = MethodResolution(methods = emptyList())
    }
}

private fun List<EtsMethod>.distinctByIdentity(): List<EtsMethod> {
    val seen = IdentityHashMap<EtsMethod, Unit>()

    return filter { method -> seen.put(method, Unit) == null }
}

private val EtsExportInfo.isBareStarReExport: Boolean
    get() = isStarReExport && !isAliased

private val EtsExportInfo.isNamespaceStarReExport: Boolean
    get() = isStarReExport && isAliased

private val EtsExportInfo.runtimeName: String
    get() = if (!isReExport && isDefaultExport) DEFAULT_EXPORT_NAME else name

private const val DEFAULT_EXPORT_NAME = "default"
private const val RECEIVER_STACK_SLOT = 0
private const val RECEIVER_STACK_SLOTS = 1
