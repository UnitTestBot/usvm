package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.BranchArmCoverage
import org.usvm.ts.pbt.backend.BranchCoverage
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.StatementCoverage
import org.usvm.ts.pbt.model.PropertyId
import java.nio.file.Path

/** Maps Istanbul statement and branch locations to EtsIR statements and CFG edges. */
internal class EtsCoverageMapper(
    scene: EtsScene,
    private val sourceLocations: SourceLocationNormalizer,
) {
    private val sceneFileCandidates = scene.projectFiles.map { file ->
        SceneFileCandidate(
            file = file,
            canonicalPaths = sourceLocations.normalizePath(file.name).mapTo(hashSetOf(), Path::toString),
        )
    }

    fun map(
        propertyId: PropertyId,
        coverage: PropertyCoverageArtifact?,
    ): EtsCoverageMapping {
        if (coverage == null) return coverageUnavailable()

        if (coverage.propertyId != propertyId) {
            return EtsCoverageMapping(
                status = EtsMappingStatus.UNSUPPORTED,
                backendProvenance = coverage.provenance,
                diagnostics = listOf(
                    EtsMappingDiagnostic(
                        code = PbtDiagnosticCode.MAPPING_COVERAGE_PROPERTY_ID_MISMATCH,
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

    private fun coverageUnavailable(): EtsCoverageMapping = EtsCoverageMapping(
        status = EtsMappingStatus.UNSUPPORTED,
        backendProvenance = null,
        diagnostics = listOf(
            EtsMappingDiagnostic(
                code = PbtDiagnosticCode.MAPPING_COVERAGE_UNAVAILABLE,
                message = "The property backend returned no source coverage artifact",
            ),
        ),
    )

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
                code = PbtDiagnosticCode.MAPPING_BRANCH_SHAPE_UNSUPPORTED,
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

        // Preserve the EtsFile groups: two frontend files for one canonical source path are ambiguous provenance.
        val conditionGroupsInSourceFile = sceneFileCandidates
            .filter { candidate -> location.path in candidate.canonicalPaths }
            .map { candidate -> candidate.statements.filterIsInstance<EtsIfStmt>() }
        val conditionsInSourceFile = conditionGroupsInSourceFile.flatten()
        if (conditionsInSourceFile.isNotEmpty() && conditionsInSourceFile.none { it.location.origin != null }) {
            val diagnostic = EtsMappingDiagnostic(
                code = PbtDiagnosticCode.MAPPING_SOURCE_ORIGINS_UNSUPPORTED,
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
                code = PbtDiagnosticCode.MAPPING_BRANCH_CFG_UNSUPPORTED,
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
            sourceCandidateCount = conditionGroupsInSourceFile.size,
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
        conditions.isEmpty() -> EtsMappingResult(
            status = EtsMappingStatus.UNMAPPED,
            targets = emptyList(),
            diagnostics = listOf(
                EtsMappingDiagnostic(
                    code = PbtDiagnosticCode.MAPPING_BRANCH_UNMAPPED,
                    message = "No EtsIR condition belongs to the covered TypeScript branch",
                    sourcePath = location.path,
                ),
            ),
        )

        distinctOriginCount == 1 && sourceCandidateCount == 1 -> EtsMappingResult(
            status = EtsMappingStatus.EXACT,
            targets = conditions.map(::EtsBranchTarget),
        )

        distinctOriginCount > 1 || sourceCandidateCount > 1 -> EtsMappingResult(
            status = EtsMappingStatus.AMBIGUOUS,
            targets = conditions.map(::EtsBranchTarget),
            diagnostics = listOf(
                EtsMappingDiagnostic(
                    code = PbtDiagnosticCode.MAPPING_BRANCH_AMBIGUOUS,
                    message = "The covered TypeScript branch contains several EtsIR conditions",
                    sourcePath = location.path,
                ),
            ),
        )

        else -> error("EtsIR branch mapping has targets without source provenance")
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

            // Istanbul if arms and the EtsIR CFG both use true-then-false order by contract.
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
                        code = PbtDiagnosticCode.MAPPING_SOURCE_ORIGINS_UNSUPPORTED,
                        message = "EtsIR statements for the covered source file have no source origins",
                        sourcePath = location.path,
                    ),
                ),
            )
        }

        // Exact spans win. A containing coverage range is exact only when every target shares one source origin.
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
            statementGroupsInSourceFile.size > 1 && containedStatements.isNotEmpty() ->
                ambiguousStatementMapping(location, containedStatements)

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
                        code = PbtDiagnosticCode.MAPPING_STATEMENT_UNMAPPED,
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
                code = PbtDiagnosticCode.MAPPING_STATEMENT_AMBIGUOUS,
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
            PbtDiagnosticCode.MAPPING_SOURCE_LOCATION_UNSUPPORTED
        } else {
            PbtDiagnosticCode.MAPPING_SOURCE_UNAVAILABLE
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

    private fun org.jacodb.ets.model.EtsSourceSpan.hasPath(path: String): Boolean =
        sourceLocations.normalizePath(fileName).any { candidate -> candidate.toString() == path }
}

private data class SceneFileCandidate(
    val file: EtsFile,
    val canonicalPaths: Set<String>,
) {
    val statements: List<EtsStmt> = file.allClasses
        .flatMap { etsClass -> etsClass.methods }
        .flatMap { method -> method.cfg.stmts }
}

private fun EtsIfStmt.successorCount(): Int = location.method.cfg.successors(this).size

private fun aggregateStatus(statuses: List<EtsMappingStatus>): EtsMappingStatus = when {
    statuses.isEmpty() -> EtsMappingStatus.EXACT
    EtsMappingStatus.UNSUPPORTED in statuses -> EtsMappingStatus.UNSUPPORTED
    EtsMappingStatus.AMBIGUOUS in statuses -> EtsMappingStatus.AMBIGUOUS
    EtsMappingStatus.UNMAPPED in statuses -> EtsMappingStatus.UNMAPPED
    else -> EtsMappingStatus.EXACT
}

private const val BINARY_BRANCH_ARM_COUNT = 2
private const val ISTANBUL_IF_BRANCH_TYPE = "if"
private const val TRUE_BRANCH_ARM_INDEX = 0
