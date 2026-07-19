package org.usvm.ts.pbt.capability

import kotlinx.serialization.Serializable

const val CAPABILITY_SCHEMA_VERSION: Int = 2
const val CAPABILITY_CLASSIFIER_BASE_VERSION: String = "usvm-ts-static-capability-v1"
const val CAPABILITY_CLASSIFIER_VERSION: String = "$CAPABILITY_CLASSIFIER_BASE_VERSION:roadmap-w1"

/** Closed, publication-facing feature taxonomy from the capability audit. */
object CapabilityLabel {
    const val PRIMITIVE_ARITHMETIC = "primitive_arithmetic"
    const val MODULE_INIT = "module_init"
    const val CALLABLE = "callable"
    const val ITERATOR = "iterator"
    const val ARRAY_OBJECT = "array_object"
    const val MAP_SET = "map_set"
    const val BUILTIN_CALL = "builtin_call"
    const val SPREAD_YIELD = "spread_yield"
    const val UNRESOLVED_POINTER_CALL = "unresolved_pointer_call"

    val values: Set<String> = linkedSetOf(
        PRIMITIVE_ARITHMETIC,
        MODULE_INIT,
        CALLABLE,
        ITERATOR,
        ARRAY_OBJECT,
        MAP_SET,
        BUILTIN_CALL,
        SPREAD_YIELD,
        UNRESOLVED_POINTER_CALL,
    )
}

/** Static status. Only [NEEDS_DYNAMIC_PROBE] is intentionally non-terminal. */
object CapabilityStatus {
    const val SUPPORTED = "supported"
    const val SUPPORTED_WITH_FLAG = "supported_with_flag"
    const val EXTERNAL_ONLY = "external_only"
    const val UNSUPPORTED = "unsupported"
    const val NEEDS_DYNAMIC_PROBE = "needs_dynamic_probe"

    val values: Set<String> = linkedSetOf(
        SUPPORTED,
        SUPPORTED_WITH_FLAG,
        EXTERNAL_ONLY,
        UNSUPPORTED,
        NEEDS_DYNAMIC_PROBE,
    )

    val terminalValues: Set<String> = values - NEEDS_DYNAMIC_PROBE
}

/**
 * Facts are extracted from an EtsIR/source AST node before classification.
 * [UNKNOWN] is internal evidence and is deliberately not a publication label.
 */
object CapabilityAstKind {
    const val PRIMITIVE_ARITHMETIC = CapabilityLabel.PRIMITIVE_ARITHMETIC
    const val MODULE_INIT = CapabilityLabel.MODULE_INIT
    const val CALLABLE = CapabilityLabel.CALLABLE
    const val ITERATOR = CapabilityLabel.ITERATOR
    const val ARRAY_OBJECT = CapabilityLabel.ARRAY_OBJECT
    const val MAP_SET = CapabilityLabel.MAP_SET
    const val BUILTIN_CALL = CapabilityLabel.BUILTIN_CALL
    const val SPREAD_YIELD = CapabilityLabel.SPREAD_YIELD
    const val UNRESOLVED_POINTER_CALL = CapabilityLabel.UNRESOLVED_POINTER_CALL
    const val UNKNOWN = "unknown_ast"

    val values: Set<String> = CapabilityLabel.values + UNKNOWN
}

@Serializable
data class CapabilityAstFact(
    val kind: String,
    /** Stable evidence, for example an EtsIR class or source node kind. */
    val evidence: String,
    /** False means that the syntax suggests a feature but does not prove it. */
    val proven: Boolean = true,
)

@Serializable
data class CapabilityCfgNode(
    val stmtIndex: Int,
    val successorStmtIndices: List<Int>,
    val facts: List<CapabilityAstFact> = emptyList(),
)

@Serializable
data class CapabilityCfg(
    val entryStmtIndex: Int,
    val nodes: List<CapabilityCfgNode>,
)

/**
 * All feasible CFG ancestors form [conservativeStmtIndices]. Statements that
 * dominate the target form [mandatoryStmtIndices]. Both exclude the selected
 * successor because capability is evaluated before the target edge is taken.
 */
@Serializable
data class CapabilityPrefixSlice(
    val methodId: String,
    val branchId: String,
    val targetStmtIndex: Int,
    val conservativeStmtIndices: List<Int>,
    val mandatoryStmtIndices: List<Int>,
    /** Facts located on statements that dominate the target. */
    val mandatoryFacts: List<CapabilityAstFact>,
    /** Facts from every feasible prefix; may include avoidable predecessors. */
    val facts: List<CapabilityAstFact>,
    val complete: Boolean,
    val uncertaintyReason: String? = null,
)

data class CapabilityTargetKey(
    val methodId: String,
    val branchId: String,
) : Comparable<CapabilityTargetKey> {
    override fun compareTo(other: CapabilityTargetKey): Int =
        compareValuesBy(this, other, CapabilityTargetKey::methodId, CapabilityTargetKey::branchId)
}

@Serializable
data class StaticCapabilityRecord(
    val schemaVersion: Int = CAPABILITY_SCHEMA_VERSION,
    val methodId: String,
    val branchId: String,
    /** Non-empty, unique, taxonomy-closed feature set. */
    val labels: List<String>,
    /** Exactly one routing label, and always a member of [labels]. */
    val primaryLabel: String,
    val staticStatus: String,
    val reasonCode: String,
    val requiredFlags: List<String> = emptyList(),
    val mappingStatus: String,
    val conservativePrefixStmtIndices: List<Int>,
    val mandatoryPrefixStmtIndices: List<Int>,
    val prefixComplete: Boolean,
)

@Serializable
data class CapabilityScanReport(
    val schemaVersion: Int = CAPABILITY_SCHEMA_VERSION,
    val classifierVersion: String = CAPABILITY_CLASSIFIER_VERSION,
    /** SHA-256 of canonical source-target records and their prefix slices. */
    val manifestHash: String,
    val sourceTargetCount: Int,
    val statusCounts: Map<String, Int>,
    val labelCounts: Map<String, Int>,
    val records: List<StaticCapabilityRecord>,
)

@Serializable
data class CapabilityValidationIssue(
    val path: String,
    val code: String,
    val message: String,
)

@Serializable
data class CapabilityValidationReport(
    val valid: Boolean,
    val issues: List<CapabilityValidationIssue>,
)

class CapabilityContractException(
    val issues: List<CapabilityValidationIssue>,
) : IllegalArgumentException(
    issues.joinToString(prefix = "invalid capability input: ", separator = "; ") {
        "${it.path}: ${it.code}: ${it.message}"
    },
)
