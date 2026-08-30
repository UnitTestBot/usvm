package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsExportInfo
import org.jacodb.ets.model.EtsExportType
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.usvm.ts.pbt.backend.BranchArmCoverage
import org.usvm.ts.pbt.backend.BranchCoverage
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.StatementCoverage
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import java.nio.file.Path
import java.util.IdentityHashMap

/** Maps backend-independent property manifests to EtsIR objects in one project scene. */
class PropertyEtsMapper(
    private val scene: EtsScene,
    sourceRoots: List<Path>,
) {
    private val sourceLocations = SourceLocationNormalizer(sourceRoots)
    private val sceneFileCandidates = scene.projectFiles.map { file ->
        SceneFileCandidate(
            file = file,
            canonicalPaths = sourceLocations.normalizePath(file.name).mapTo(hashSetOf(), Path::toString),
        )
    }

    /** Produces a complete mapping artifact even when individual entry points or coverage locations do not map. */
    fun map(
        manifest: PropertyManifest,
        coverage: PropertyCoverageArtifact? = null,
    ): PropertyEtsMappingArtifact {
        val propertyId = PropertyId(manifest.propertyId)
        val predicate = resolveEntryPoint(manifest.predicate, manifest)
        val precondition = manifest.precondition?.let { entryPoint ->
            resolveEntryPoint(entryPoint, manifest)
        }
        val coverageMapping = coverage?.let { artifact -> mapCoverage(propertyId, artifact) } ?: unsupportedCoverage()

        return PropertyEtsMappingArtifact(
            propertyId = propertyId,
            provenance = EtsMappingProvenance(
                sourceRoots = sourceLocations.normalizedSourceRoots.map(Path::toString),
                coordinates = EtsSourceCoordinateSystem.TYPESCRIPT_UTF16_ZERO_BASED_HALF_OPEN,
                branchSuccessorOrder = EtsBranchSuccessorOrder.TRUE_FALSE,
            ),
            predicate = predicate,
            precondition = precondition,
            coverage = coverageMapping,
        )
    }

    private fun unsupportedCoverage(): EtsCoverageMapping = EtsCoverageMapping(
        status = EtsMappingStatus.UNSUPPORTED,
        backendProvenance = null,
        diagnostics = listOf(
            EtsMappingDiagnostic(
                code = "mapping.coverage.unavailable",
                message = "The property backend returned no source coverage artifact",
            ),
        ),
    )

    private fun mapCoverage(
        propertyId: PropertyId,
        coverage: PropertyCoverageArtifact,
    ): EtsCoverageMapping {
        if (coverage.propertyId != propertyId) {
            return EtsCoverageMapping(
                status = EtsMappingStatus.UNSUPPORTED,
                backendProvenance = coverage.provenance,
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = "mapping.coverage.property-id.mismatch",
                        message = "Coverage property ${coverage.propertyId.value} does not match ${propertyId.value}",
                    ),
                ),
            )
        }

        val statements = coverage.files.flatMap { file ->
            file.statements.map { statement -> mapStatementCoverage(file.path, statement) }
        }
        val branches = coverage.files.flatMap { file ->
            file.branches.map { branch -> mapBranchCoverage(file.path, branch) }
        }
        val diagnostics = coverage.diagnostics.map { diagnostic ->
            EtsMappingDiagnostic(
                code = diagnostic.code,
                message = diagnostic.message,
                sourcePath = diagnostic.path,
            )
        }

        return EtsCoverageMapping(
            status = aggregateStatus(
                statements.map { statement -> statement.mapping.status } +
                    branches.flatMap { branch ->
                        listOf(branch.mapping.status) + branch.arms.map { arm -> arm.mapping.status }
                    },
            ),
            backendProvenance = coverage.provenance,
            statements = statements,
            branches = branches,
            diagnostics = diagnostics,
        )
    }

    private fun mapBranchCoverage(
        sourcePath: String,
        coverage: BranchCoverage,
    ): EtsBranchCoverageMapping {
        val normalization = runCatching { sourceLocations.normalizeRange(sourcePath, coverage.location) }
        val location = normalization.getOrNull()
        if (location == null) {
            return unsupportedBranchCoverage(
                sourcePath = sourcePath,
                coverage = coverage,
                location = null,
                diagnostic = sourceNormalizationDiagnostic(sourcePath, normalization.exceptionOrNull()),
                normalizeArmLocations = false,
            )
        }

        if (coverage.type != ISTANBUL_IF_BRANCH_TYPE || coverage.arms.size != BINARY_BRANCH_ARM_COUNT) {
            val diagnostic = EtsMappingDiagnostic(
                code = "mapping.branch.shape.unsupported",
                message = "EtsIR branch mapping requires an if branch with exactly two ordered coverage arms",
                sourcePath = location.path,
            )

            return unsupportedBranchCoverage(
                sourcePath = sourcePath,
                coverage = coverage,
                location = location,
                diagnostic = diagnostic,
                normalizeArmLocations = true,
            )
        }

        val conditionGroupsInSourceFile = sceneFileCandidates
            .filter { candidate -> location.path in candidate.canonicalPaths }
            .map { candidate -> candidate.statements.filterIsInstance<EtsIfStmt>() }
        val conditionsInSourceFile = conditionGroupsInSourceFile.flatten()
        if (conditionsInSourceFile.isNotEmpty() && conditionsInSourceFile.none { it.location.origin != null }) {
            val diagnostic = EtsMappingDiagnostic(
                code = "mapping.source-origins.unsupported",
                message = "EtsIR conditions for the covered source file have no source origins",
                sourcePath = location.path,
            )

            return unsupportedBranchCoverage(
                sourcePath = sourcePath,
                coverage = coverage,
                location = location,
                diagnostic = diagnostic,
                normalizeArmLocations = true,
            )
        }

        val conditionGroups = conditionGroupsInSourceFile
            .map { statements -> statements.filter { statement -> statement.hasOriginWithin(location) } }
            .filter { statements -> statements.isNotEmpty() }
        val conditions = conditionGroups.flatten()
        if (conditions.any { statement -> statement.successorCount() != BINARY_BRANCH_ARM_COUNT }) {
            val diagnostic = EtsMappingDiagnostic(
                code = "mapping.branch.cfg.unsupported",
                message = "EtsIR branch mapping requires exactly two ordered CFG successors",
                sourcePath = location.path,
            )

            return unsupportedBranchCoverage(
                sourcePath = sourcePath,
                coverage = coverage,
                location = location,
                diagnostic = diagnostic,
                normalizeArmLocations = true,
            )
        }

        val distinctOrigins = conditions
            .mapNotNull { statement -> statement.location.origin }
            .distinct()
        val mapping = branchMapping(
            location = location,
            conditions = conditions,
            distinctOriginCount = distinctOrigins.size,
            sourceCandidateCount = conditionGroups.size,
        )
        val arms = coverage.arms.mapIndexed { index, arm ->
            mapBranchArm(sourcePath, arm, index, mapping)
        }

        return EtsBranchCoverageMapping(
            coverage = coverage,
            location = location,
            mapping = mapping,
            arms = arms,
        )
    }

    private fun unsupportedBranchCoverage(
        sourcePath: String,
        coverage: BranchCoverage,
        location: NormalizedSourceRange?,
        diagnostic: EtsMappingDiagnostic,
        normalizeArmLocations: Boolean,
    ): EtsBranchCoverageMapping {
        val mapping = unsupportedMapping<EtsBranchTarget>(diagnostic)
        val arms = if (normalizeArmLocations) {
            coverage.arms.mapIndexed { index, arm ->
                mapBranchArm(sourcePath, arm, index, mapping)
            }
        } else {
            coverage.arms.map { arm ->
                EtsBranchArmCoverageMapping(
                    coverage = arm,
                    location = null,
                    mapping = unsupportedMapping(diagnostic),
                )
            }
        }

        return EtsBranchCoverageMapping(
            coverage = coverage,
            location = location,
            mapping = mapping,
            arms = arms,
        )
    }

    private fun branchMapping(
        location: NormalizedSourceRange,
        conditions: List<EtsIfStmt>,
        distinctOriginCount: Int,
        sourceCandidateCount: Int,
    ): EtsMappingResult<EtsBranchTarget> = when {
        distinctOriginCount == 1 && sourceCandidateCount == 1 -> EtsMappingResult(
            status = EtsMappingStatus.EXACT,
            targets = conditions.map(::EtsBranchTarget),
        )

        distinctOriginCount > 1 || sourceCandidateCount > 1 -> EtsMappingResult(
            status = EtsMappingStatus.AMBIGUOUS,
            targets = conditions.map(::EtsBranchTarget),
            diagnostics = listOf(
                EtsMappingDiagnostic(
                    code = "mapping.branch.ambiguous",
                    message = "The covered TypeScript branch contains several EtsIR conditions",
                    sourcePath = location.path,
                ),
            ),
        )

        else -> EtsMappingResult(
            status = EtsMappingStatus.UNMAPPED,
            targets = emptyList(),
            diagnostics = listOf(
                EtsMappingDiagnostic(
                    code = "mapping.branch.unmapped",
                    message = "No EtsIR condition belongs to the covered TypeScript branch",
                    sourcePath = location.path,
                ),
            ),
        )
    }

    private fun mapBranchArm(
        sourcePath: String,
        coverage: BranchArmCoverage,
        armIndex: Int,
        branchMapping: EtsMappingResult<EtsBranchTarget>,
    ): EtsBranchArmCoverageMapping {
        val normalization = runCatching { sourceLocations.normalizeRange(sourcePath, coverage.location) }
        val location = normalization.getOrNull()
        if (location == null) {
            return EtsBranchArmCoverageMapping(
                coverage = coverage,
                location = null,
                mapping = unsupportedMapping(
                    sourceNormalizationDiagnostic(sourcePath, normalization.exceptionOrNull()),
                ),
            )
        }

        val targets = branchMapping.targets.map { branch ->
            val graph = branch.statement.location.method.cfg
            val successors = graph.successors(branch.statement).toList()

            EtsBranchArmTarget(
                condition = branch.statement,
                outcome = armIndex == TRUE_BRANCH_ARM_INDEX,
                successor = successors[armIndex],
            )
        }

        return EtsBranchArmCoverageMapping(
            coverage = coverage,
            location = location,
            mapping = EtsMappingResult(
                status = branchMapping.status,
                targets = targets,
                diagnostics = branchMapping.diagnostics,
            ),
        )
    }

    private fun mapStatementCoverage(
        sourcePath: String,
        coverage: StatementCoverage,
    ): EtsStatementCoverageMapping {
        val normalization = runCatching { sourceLocations.normalizeRange(sourcePath, coverage.location) }
        val location = normalization.getOrNull()
        if (location == null) {
            return EtsStatementCoverageMapping(
                coverage = coverage,
                location = null,
                mapping = unsupportedMapping(
                    sourceNormalizationDiagnostic(sourcePath, normalization.exceptionOrNull()),
                ),
            )
        }

        val statementGroupsInSourceFile = sceneFileCandidates
            .filter { candidate -> location.path in candidate.canonicalPaths }
            .map { candidate -> candidate.statements }
        val statementsInSourceFile = statementGroupsInSourceFile.flatten()
        if (statementsInSourceFile.isNotEmpty() && statementsInSourceFile.none { it.location.origin != null }) {
            return EtsStatementCoverageMapping(
                coverage = coverage,
                location = location,
                mapping = unsupportedMapping(
                    EtsMappingDiagnostic(
                        code = "mapping.source-origins.unsupported",
                        message = "EtsIR statements for the covered source file have no source origins",
                        sourcePath = location.path,
                    ),
                ),
            )
        }

        val exactTargets = statementGroupsInSourceFile
            .flatMap { statements -> statements.filter { statement -> statement.hasOrigin(location) } }
            .map(::EtsStatementTarget)
        val containedStatementGroups = statementGroupsInSourceFile
            .map { statements -> statements.filter { statement -> statement.hasOriginWithin(location) } }
            .filter { statements -> statements.isNotEmpty() }
        val containedStatements = containedStatementGroups.flatten()
        val distinctContainedOrigins = containedStatements
            .mapNotNull { statement -> statement.location.origin }
            .distinct()
        val mapping = when {
            containedStatementGroups.size > 1 -> ambiguousStatementMapping(location, containedStatements)

            exactTargets.isNotEmpty() -> EtsMappingResult(
                status = EtsMappingStatus.EXACT,
                targets = exactTargets,
            )

            distinctContainedOrigins.size == 1 -> EtsMappingResult(
                status = EtsMappingStatus.EXACT,
                targets = containedStatements.map(::EtsStatementTarget),
            )

            distinctContainedOrigins.size > 1 -> ambiguousStatementMapping(location, containedStatements)

            else -> EtsMappingResult(
                status = EtsMappingStatus.UNMAPPED,
                targets = emptyList(),
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = "mapping.statement.unmapped",
                        message = "No EtsIR statement has the covered TypeScript source span",
                        sourcePath = location.path,
                    ),
                ),
            )
        }

        return EtsStatementCoverageMapping(
            coverage = coverage,
            location = location,
            mapping = mapping,
        )
    }

    private fun ambiguousStatementMapping(
        location: NormalizedSourceRange,
        statements: List<EtsStmt>,
    ): EtsMappingResult<EtsStatementTarget> = EtsMappingResult(
        status = EtsMappingStatus.AMBIGUOUS,
        targets = statements.map(::EtsStatementTarget),
        diagnostics = listOf(
            EtsMappingDiagnostic(
                code = "mapping.statement.ambiguous",
                message = "The covered TypeScript range matches several EtsIR source candidates or spans",
                sourcePath = location.path,
            ),
        ),
    )

    private fun sourceNormalizationDiagnostic(
        sourcePath: String,
        failure: Throwable?,
    ): EtsMappingDiagnostic {
        val diagnosticCode = if (failure is UnsupportedSourceLocationException) {
            "mapping.source.location.unsupported"
        } else {
            "mapping.source.unavailable"
        }

        return EtsMappingDiagnostic(
            code = diagnosticCode,
            message = "Cannot normalize covered source $sourcePath: ${failure?.message}",
            sourcePath = sourcePath,
        )
    }

    private fun <T> unsupportedMapping(diagnostic: EtsMappingDiagnostic): EtsMappingResult<T> = EtsMappingResult(
        status = EtsMappingStatus.UNSUPPORTED,
        targets = emptyList(),
        diagnostics = listOf(diagnostic),
    )

    private fun EtsStmt.hasOrigin(location: NormalizedSourceRange): Boolean {
        val origin = this.location.origin ?: return false
        if (!origin.hasPath(location.path)) return false

        return origin.startLine == location.start.line &&
            origin.startColumn == location.start.column &&
            origin.startOffset == location.start.offset &&
            origin.endLine == location.end.line &&
            origin.endColumn == location.end.column &&
            origin.endOffset == location.end.offset
    }

    private fun EtsStmt.hasOriginWithin(location: NormalizedSourceRange): Boolean {
        val origin = this.location.origin ?: return false

        return origin.hasPath(location.path) &&
            origin.startOffset >= location.start.offset &&
            origin.endOffset <= location.end.offset
    }

    private fun EtsIfStmt.successorCount(): Int = location.method.cfg.successors(this).size

    private fun org.jacodb.ets.model.EtsSourceSpan.hasPath(path: String): Boolean =
        sourceLocations.normalizePath(fileName).any { candidate -> candidate.toString() == path }

    private fun aggregateStatus(statuses: List<EtsMappingStatus>): EtsMappingStatus = when {
        statuses.isEmpty() -> EtsMappingStatus.EXACT
        EtsMappingStatus.UNSUPPORTED in statuses -> EtsMappingStatus.UNSUPPORTED
        EtsMappingStatus.AMBIGUOUS in statuses -> EtsMappingStatus.AMBIGUOUS
        EtsMappingStatus.UNMAPPED in statuses -> EtsMappingStatus.UNMAPPED
        else -> EtsMappingStatus.EXACT
    }

    private fun resolveEntryPoint(
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

        val candidateResolutions = scene.projectFiles
            .filter { candidate -> candidate.matches(entryPoint.module) }
            .map { file -> resolveExportedMethods(file, entryPoint.exportName, visited = emptySet()) }
        val methods = candidateResolutions
            .flatMap { resolution -> resolution.methods }
            .distinctByIdentity()
        val hasAmbiguousResolution = candidateResolutions.any { resolution -> resolution.isAmbiguous } ||
            candidateResolutions.count { resolution -> resolution.methods.isNotEmpty() } > 1

        if (methods.any { method -> method.parameters.size != manifest.inputs.size }) {
            return EtsMappingResult(
                status = EtsMappingStatus.UNSUPPORTED,
                targets = emptyList(),
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = "mapping.entry-point.bindings.unsupported",
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
                        code = "mapping.entry-point.ambiguous",
                        message = "Several EtsIR methods match ${entryPoint.module}#${entryPoint.exportName}",
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
                    code = "mapping.entry-point.unmapped",
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

                resolveReExportFiles(file, requireNotNull(export.from)).map { targetFile ->
                    resolveExportedMethods(targetFile, targetExportName, visited + file)
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
        val callableAssignments = file.classes
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

                        val local = assignment.rhv as? EtsLocal ?: return@mapNotNull null
                        val functionType = local.type as? EtsFunctionType ?: return@mapNotNull null

                        CallableLocalAssignment(
                            defaultClass = defaultClass,
                            functionSignature = functionType.signature,
                        )
                    }
            }
        val linkedMethods = callableAssignments.flatMap { assignment ->
            assignment.defaultClass.methods.filter { method ->
                method.name.startsWith(ANONYMOUS_METHOD_PREFIX) &&
                    method.signature == assignment.functionSignature
            }
        }
        val methods = linkedMethods.distinctByIdentity()
        val isExactLink = callableAssignments.size == 1 && linkedMethods.size == 1

        return MethodResolution(
            methods = methods,
            isAmbiguous = methods.isNotEmpty() && !isExactLink,
        )
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

    private data class SceneFileCandidate(
        val file: EtsFile,
        val canonicalPaths: Set<String>,
    ) {
        val statements: List<EtsStmt> = file.allClasses
            .flatMap { etsClass -> etsClass.methods }
            .flatMap { method -> method.cfg.stmts }
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

    private companion object {
        const val BINARY_BRANCH_ARM_COUNT = 2
        const val DEFAULT_EXPORT_NAME = "default"
        const val ISTANBUL_IF_BRANCH_TYPE = "if"
        const val RECEIVER_STACK_SLOT = 0
        const val RECEIVER_STACK_SLOTS = 1
        const val TRUE_BRANCH_ARM_INDEX = 0
    }
}
