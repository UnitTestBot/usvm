package org.usvm.ts.pbt.semantics.module

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.usvm.ts.pbt.capability.CapabilityLabel
import org.usvm.ts.pbt.capability.CapabilityStatus
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

internal const val MODULE_SEMANTICS_SCHEMA_VERSION: Int = 1

internal object ModuleNodeOutcome {
    const val RETURNED = "returned"
    const val LINK_ERROR = "link_error"
    const val INITIALIZATION_ERROR = "initialization_error"

    val values: Set<String> = setOf(RETURNED, LINK_ERROR, INITIALIZATION_ERROR)
}

internal object ModuleBindingResolution {
    const val VALUE = "value"
    const val CALLABLE = "callable"
    const val NAMESPACE = "namespace"
    const val ABSENT = "absent"
    const val AMBIGUOUS = "ambiguous"
    const val CYCLE_TDZ = "cycle_tdz"

    val values: Set<String> = setOf(VALUE, CALLABLE, NAMESPACE, ABSENT, AMBIGUOUS, CYCLE_TDZ)
}

internal object ModuleExpectedOutcome {
    const val REPLAY_CONFIRMED = "replay_confirmed"
    const val PROVED_INFEASIBLE = "proved_infeasible"
    const val EXACT_UNSUPPORTED = "exact_unsupported"
    const val EXACT_CAPABILITY_MISMATCH = "exact_capability_mismatch"

    val values: Set<String> = setOf(
        REPLAY_CONFIRMED,
        PROVED_INFEASIBLE,
        EXACT_UNSUPPORTED,
        EXACT_CAPABILITY_MISMATCH,
    )
}

@Serializable
internal data class ModuleSemanticsContract(
    val schemaVersion: Int,
    val contractId: String,
    val nodeProtocolVersion: Int,
    val evidence: List<ModuleFrozenEvidence>,
    val cases: List<ModuleSemanticCase>,
    val witnesses: List<ModuleSemanticWitness>,
)

@Serializable
internal data class ModuleFrozenEvidence(
    val id: String,
    val baselineId: String,
    val projectId: String,
    val repository: String,
    val projectCommit: String,
    val denominatorId: String,
    val denominatorPath: String,
    val denominatorSha256: String,
    val scenario: String,
    val targetManifestPath: String,
    val targetManifestSha256: String,
    val sourceTargetsPath: String,
    val sourceTargetsSha256: String,
    val observationPath: String,
    val observationSha256: String,
    val sourceReport: String,
    val sourceReportSha256: String,
)

@Serializable
internal data class ModuleSemanticCase(
    val id: String,
    val entryModule: String,
    val modules: List<ModuleDeclaration>,
    val bindings: List<ModuleBindingExpectation>,
    val explicitAbsentExports: List<ModuleAbsentExport> = emptyList(),
    val expected: ModuleNodeExpected,
    val capability: ModuleCapabilityExpectation,
)

@Serializable
internal data class ModuleDeclaration(
    val moduleId: String,
    val sourcePath: String,
    val exports: List<ModuleExportDeclaration> = emptyList(),
)

@Serializable
internal data class ModuleExportDeclaration(
    val name: String,
    val kind: String,
)

@Serializable
internal data class ModuleBindingExpectation(
    val importerModuleId: String,
    val localName: String,
    val sourceModuleId: String,
    val exportName: String,
    val importKind: String,
    val expectedResolution: String,
)

@Serializable
internal data class ModuleAbsentExport(
    val moduleId: String,
    val exportName: String,
)

@Serializable
internal data class ModuleNodeExpected(
    val outcome: String,
    val result: JsonElement = JsonNull,
    val trace: List<String>,
    val errorName: String? = null,
)

@Serializable
internal data class ModuleCapabilityExpectation(
    val labels: List<String>,
    val semanticTags: List<String>,
    val expectedStatus: String,
    val requiredFlags: List<String>,
    val expectedOutcome: String,
)

@Serializable
internal data class ModuleSemanticWitness(
    val projectId: String,
    val projectCommit: String,
    val evidenceId: String,
    val methodId: String,
    val branchId: String,
    val sourceCallableId: String,
    val etsIrOriginId: String,
    val sourceBindingStatus: String,
    val etsIrMappingStatus: String,
    val mappingEvidence: String,
    val provenanceScope: String,
    val ownershipClaim: String,
    val sharedWith: String,
    val unionKey: String,
    val conditionOrigin: ModuleSourceOrigin,
    val successorOrigin: ModuleSourceOrigin? = null,
    val historical: ModuleHistoricalOutcome,
    val capability: ModuleCapabilityExpectation,
)

@Serializable
internal data class ModuleSourceOrigin(
    val fileName: String,
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val nodeKind: String,
)

@Serializable
internal data class ModuleHistoricalOutcome(
    val reached: Boolean,
    val replayConfirmed: Boolean,
    val wallMs: Long,
    val steps: Int,
    val hintsUsed: Boolean,
    val fallbackUsed: Boolean,
    val pbtExecutions: Int,
    val pbtThrew: Int,
    val failureClass: String,
)

internal data class ModuleContractIssue(
    val path: String,
    val code: String,
    val message: String,
)

internal data class ModuleContractValidation(
    val valid: Boolean,
    val issues: List<ModuleContractIssue>,
)

internal object ModuleSemanticsContractCodec {
    val json: Json = Json {
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    fun decode(path: Path): ModuleSemanticsContract =
        json.decodeFromString<ModuleSemanticsContract>(path.readText())
}

internal object ModuleSemanticsContractValidator {
    private val sha256 = Regex("[0-9a-f]{64}")
    private val branchSuffix = Regex("#s(\\d+):(\\d+)->(\\d+)$")
    private val sourceCallableId = Regex("ts:.+::(free|static|instance|constructor|arrow|synthetic):.+/\\d+")
    private val allowedImportKinds = setOf("named", "default", "namespace", "namespace_member", "side_effect")
    private val allowedExportKinds = setOf("value", "callable", "default", "re_export")
    private val allowedMappingStatuses = setOf("exact", "oneToMany", "ambiguous", "unmapped", "synthetic")

    fun validate(contract: ModuleSemanticsContract, resourceRoot: Path): ModuleContractValidation {
        val issues = mutableListOf<ModuleContractIssue>()

        fun error(path: String, code: String, message: String) {
            issues += ModuleContractIssue(path, code, message)
        }

        if (contract.schemaVersion != MODULE_SEMANTICS_SCHEMA_VERSION) {
            error("schemaVersion", "unsupported_schema", "expected $MODULE_SEMANTICS_SCHEMA_VERSION")
        }
        if (contract.contractId.isBlank()) error("contractId", "blank_id", "contractId must not be blank")
        if (contract.nodeProtocolVersion != 1) {
            error("nodeProtocolVersion", "unsupported_protocol", "only Node protocol v1 is supported")
        }
        validateUnique(contract.evidence.map(ModuleFrozenEvidence::id), "evidence", issues)
        validateUnique(contract.cases.map(ModuleSemanticCase::id), "cases", issues)
        validateUnique(contract.witnesses.map(ModuleSemanticWitness::branchId), "witnesses", issues)

        val evidenceById = contract.evidence.associateBy(ModuleFrozenEvidence::id)
        contract.evidence.forEachIndexed { index, evidence ->
            val path = "evidence[$index]"
            if (!sha256.matches(evidence.targetManifestSha256)) {
                error("$path.targetManifestSha256", "invalid_sha256", "expected lowercase SHA-256")
            }
            if (!sha256.matches(evidence.sourceTargetsSha256)) {
                error("$path.sourceTargetsSha256", "invalid_sha256", "expected lowercase SHA-256")
            }
            if (!sha256.matches(evidence.observationSha256)) {
                error("$path.observationSha256", "invalid_sha256", "expected lowercase SHA-256")
            }
            if (!sha256.matches(evidence.sourceReportSha256)) {
                error("$path.sourceReportSha256", "invalid_sha256", "expected lowercase SHA-256")
            }
            if (!sha256.matches(evidence.denominatorSha256)) {
                error("$path.denominatorSha256", "invalid_sha256", "expected lowercase SHA-256")
            }
        }

        contract.cases.forEachIndexed { index, case ->
            validateCase(case, index, resourceRoot, issues)
        }
        contract.witnesses.forEachIndexed { index, witness ->
            val path = "witnesses[$index]"
            if (witness.evidenceId !in evidenceById) {
                error("$path.evidenceId", "missing_evidence", "unknown evidence '${witness.evidenceId}'")
            }
            if (witness.projectCommit != evidenceById[witness.evidenceId]?.projectCommit) {
                error("$path.projectCommit", "provenance_mismatch", "does not match frozen evidence")
            }
            if (witness.projectId != evidenceById[witness.evidenceId]?.projectId) {
                error("$path.projectId", "provenance_mismatch", "does not match frozen evidence")
            }
            if (!witness.branchId.startsWith("${witness.methodId}#")) {
                error("$path.branchId", "method_branch_mismatch", "branchId must start with methodId")
            }
            if (!branchSuffix.containsMatchIn(witness.branchId)) {
                error("$path.branchId", "invalid_branch_id", "expected stable EtsIR edge suffix")
            }
            if (witness.etsIrOriginId != witness.branchId) {
                error("$path.etsIrOriginId", "synthetic_origin", "EtsIR origin must be the frozen branch ID verbatim")
            }
            if (!sourceCallableId.matches(witness.sourceCallableId)) {
                error("$path.sourceCallableId", "invalid_source_callable_id", "expected canonical ts: callable ID")
            }
            if (witness.sourceBindingStatus != "exact") {
                error("$path.sourceBindingStatus", "inexact_source_binding", "witness source callable must be exact")
            }
            if (witness.etsIrMappingStatus !in allowedMappingStatuses) {
                error("$path.etsIrMappingStatus", "unknown_mapping", "unknown EtsIR mapping status")
            }
            if (witness.mappingEvidence.isBlank()) {
                error("$path.mappingEvidence", "blank_mapping_evidence", "mapping evidence must explain the status")
            }
            if (witness.provenanceScope != "shared_module_callable" ||
                witness.ownershipClaim != "module_init_namespace_import_binding" ||
                witness.sharedWith != "WP-SEM-CALLABLE" ||
                witness.unionKey != "branchId"
            ) {
                error("$path.ownershipClaim", "invalid_ownership", "shared witnesses must be deduplicated by branchId")
            }
            if (witness.historical.replayConfirmed || !witness.historical.reached) {
                error("$path.historical", "not_residual_witness", "expected reached=true and replayConfirmed=false")
            }
            validateCapability(witness.capability, "$path.capability", issues)
        }

        return ModuleContractValidation(issues.isEmpty(), issues)
    }

    private fun validateCase(
        case: ModuleSemanticCase,
        index: Int,
        resourceRoot: Path,
        issues: MutableList<ModuleContractIssue>,
    ) {
        val path = "cases[$index]"
        val modulesById = validateCaseModules(case, path, resourceRoot, issues)
        validateCaseBindings(case, path, modulesById, issues)
        if (case.expected.outcome !in ModuleNodeOutcome.values) {
            issues += ModuleContractIssue("$path.expected.outcome", "unknown_outcome", "unknown Node outcome")
        }
        val entry = resourceRoot.resolve(case.entryModule).normalize()
        if (!entry.startsWith(resourceRoot) || !entry.exists() || !entry.isRegularFile()) {
            issues += ModuleContractIssue("$path.entryModule", "missing_source", "entry module is not a fixture file")
        }
        if (case.expected.outcome == ModuleNodeOutcome.RETURNED && case.expected.errorName != null) {
            issues += ModuleContractIssue(
                "$path.expected.errorName",
                "unexpected_error",
                "returned case cannot expect an error",
            )
        }
        if (case.expected.outcome != ModuleNodeOutcome.RETURNED && case.expected.errorName == null) {
            issues += ModuleContractIssue("$path.expected.errorName", "missing_error", "error case requires errorName")
        }
        if (containsUndefinedSentinel(case.expected.result) && case.explicitAbsentExports.isEmpty()) {
            issues += ModuleContractIssue(
                "$path.expected.result",
                "implicit_undefined_fallback",
                "undefined is allowed only for an explicitly absent export",
            )
        }
        validateCapability(case.capability, "$path.capability", issues)
    }

    private fun validateCaseModules(
        case: ModuleSemanticCase,
        path: String,
        resourceRoot: Path,
        issues: MutableList<ModuleContractIssue>,
    ): Map<String, ModuleDeclaration> {
        val modulesById = case.modules.associateBy(ModuleDeclaration::moduleId)
        validateUnique(case.modules.map(ModuleDeclaration::moduleId), "$path.modules", issues)
        case.modules.forEachIndexed { moduleIndex, module ->
            val modulePath = "$path.modules[$moduleIndex]"
            val source = resourceRoot.resolve(module.sourcePath).normalize()
            if (!source.startsWith(resourceRoot) || !source.exists() || !source.isRegularFile()) {
                issues += ModuleContractIssue(
                    "$modulePath.sourcePath",
                    "missing_source",
                    "module source is not a fixture file",
                )
            }
            validateUnique(module.exports.map(ModuleExportDeclaration::name), "$modulePath.exports", issues)
            module.exports.forEachIndexed { exportIndex, export ->
                if (export.kind !in allowedExportKinds) {
                    issues += ModuleContractIssue(
                        "$modulePath.exports[$exportIndex].kind",
                        "unknown_export_kind",
                        "unknown export kind '${export.kind}'",
                    )
                }
            }
        }
        return modulesById
    }

    private fun validateCaseBindings(
        case: ModuleSemanticCase,
        path: String,
        modulesById: Map<String, ModuleDeclaration>,
        issues: MutableList<ModuleContractIssue>,
    ) {
        val absent = case.explicitAbsentExports.map { it.moduleId to it.exportName }.toSet()
        if (absent.size != case.explicitAbsentExports.size) {
            issues += ModuleContractIssue(
                "$path.explicitAbsentExports",
                "duplicate_absence",
                "duplicates are forbidden",
            )
        }
        case.bindings.forEachIndexed { bindingIndex, binding ->
            val bindingPath = "$path.bindings[$bindingIndex]"
            if (binding.importerModuleId !in modulesById) {
                issues += ModuleContractIssue("$bindingPath.importerModuleId", "unknown_module", "unknown importer")
            }
            val source = modulesById[binding.sourceModuleId]
            if (source == null) {
                issues += ModuleContractIssue("$bindingPath.sourceModuleId", "unknown_module", "unknown source module")
            }
            if (binding.importKind !in allowedImportKinds) {
                issues += ModuleContractIssue("$bindingPath.importKind", "unknown_import_kind", "unknown import kind")
            }
            if (binding.expectedResolution !in ModuleBindingResolution.values) {
                issues += ModuleContractIssue(
                    "$bindingPath.expectedResolution",
                    "unknown_resolution",
                    "unknown binding resolution",
                )
            }
            val absenceKey = binding.sourceModuleId to binding.exportName
            if (binding.expectedResolution == ModuleBindingResolution.ABSENT && absenceKey !in absent) {
                issues += ModuleContractIssue(
                    "$bindingPath.expectedResolution",
                    "implicit_undefined_fallback",
                    "absent binding requires an explicitAbsentExports declaration",
                )
            }
            if (binding.expectedResolution !in setOf(
                    ModuleBindingResolution.ABSENT,
                    ModuleBindingResolution.AMBIGUOUS,
                    ModuleBindingResolution.CYCLE_TDZ,
                    ModuleBindingResolution.NAMESPACE,
                ) && source?.exports?.none { it.name == binding.exportName } == true
            ) {
                issues += ModuleContractIssue(
                    "$bindingPath.exportName",
                    "undeclared_export",
                    "resolved binding is absent from the source module declaration",
                )
            }
        }
        absent.forEach { (moduleId, exportName) ->
            val module = modulesById[moduleId]
            if (module == null) {
                issues += ModuleContractIssue(
                    "$path.explicitAbsentExports",
                    "unknown_module",
                    "unknown absent-export module",
                )
            } else if (module.exports.any { it.name == exportName }) {
                issues += ModuleContractIssue(
                    "$path.explicitAbsentExports",
                    "contradictory_export",
                    "export is both present and absent",
                )
            }
        }
    }

    private fun containsUndefinedSentinel(element: JsonElement): Boolean = when (element) {
        is JsonObject -> {
            (element["kind"] as? JsonPrimitive)?.content == "undefined" ||
                element.values.any(::containsUndefinedSentinel)
        }
        is JsonArray -> {
            element.any(::containsUndefinedSentinel)
        }
        else -> {
            false
        }
    }

    private fun validateCapability(
        capability: ModuleCapabilityExpectation,
        path: String,
        issues: MutableList<ModuleContractIssue>,
    ) {
        if (capability.labels.isEmpty()) {
            issues += ModuleContractIssue("$path.labels", "empty_labels", "at least one capability label is required")
        }
        if (capability.labels.distinct().size != capability.labels.size) {
            issues += ModuleContractIssue("$path.labels", "duplicate_labels", "capability labels must be unique")
        }
        capability.labels.forEachIndexed { index, label ->
            if (label !in CapabilityLabel.values) {
                issues += ModuleContractIssue(
                    "$path.labels[$index]",
                    "unknown_label",
                    "'$label' is not a closed CAP label",
                )
            }
        }
        if (capability.semanticTags.isEmpty() || capability.semanticTags.any(String::isBlank)) {
            issues += ModuleContractIssue(
                "$path.semanticTags",
                "invalid_semantic_tags",
                "semantic tags must be non-blank",
            )
        }
        if (capability.expectedStatus !in CapabilityStatus.values) {
            issues += ModuleContractIssue("$path.expectedStatus", "unknown_status", "unknown CAP status")
        }
        if (capability.expectedStatus == CapabilityStatus.SUPPORTED_WITH_FLAG && capability.requiredFlags.isEmpty()) {
            issues += ModuleContractIssue(
                "$path.requiredFlags",
                "missing_flags",
                "flagged capability must name its flags",
            )
        }
        val requiredByLabel = buildSet {
            if (CapabilityLabel.MODULE_INIT in capability.labels) add("moduleRuntimeModel")
            if (CapabilityLabel.CALLABLE in capability.labels) add("callableValueModel")
        }
        if (!capability.requiredFlags.containsAll(requiredByLabel)) {
            issues += ModuleContractIssue(
                "$path.requiredFlags",
                "incomplete_flags",
                "module/callable labels require their roadmap feature flags",
            )
        }
        if (capability.expectedOutcome !in ModuleExpectedOutcome.values) {
            issues += ModuleContractIssue(
                "$path.expectedOutcome",
                "unknown_outcome",
                "outcome is not publication-terminal",
            )
        }
    }

    private fun validateUnique(
        values: List<String>,
        path: String,
        issues: MutableList<ModuleContractIssue>,
    ) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { duplicate ->
            issues += ModuleContractIssue(path, "duplicate_id", "duplicate '$duplicate'")
        }
    }
}
