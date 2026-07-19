package org.usvm.ts.pbt.capability

import org.usvm.ts.pbt.external.SourceTargetRecord

data class CapabilityPolicyRule(
    val status: String,
    val requiredFlag: String? = null,
)

/** Versioned so policy changes cannot silently reuse a frozen audit hash. */
data class CapabilityPolicy(
    val version: String,
    val rules: Map<String, CapabilityPolicyRule>,
) {
    init {
        require(version.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "invalid capability policy version '$version'" }
        require(rules.keys == CapabilityLabel.values) { "policy must cover the closed capability taxonomy" }
        rules.forEach { (label, rule) ->
            require(rule.status in CapabilityStatus.values) { "unknown policy status '${rule.status}' for $label" }
            require(rule.requiredFlag == null || rule.requiredFlag.isNotBlank()) { "blank feature flag for $label" }
            require((rule.status == CapabilityStatus.SUPPORTED_WITH_FLAG) == (rule.requiredFlag != null)) {
                "supported_with_flag must have exactly one flag for $label"
            }
        }
    }

    val classifierVersion: String = "$CAPABILITY_CLASSIFIER_BASE_VERSION:$version"

    internal fun canonicalString(): String = buildString {
        append(classifierVersion)
        CapabilityLabel.values.sorted().forEach { label ->
            val rule = rules.getValue(label)
            append('|').append(label).append('=').append(rule.status).append('@').append(rule.requiredFlag.orEmpty())
        }
    }
}

object RoadmapCapabilityPolicy {
    val default: CapabilityPolicy = CapabilityPolicy(
        version = "roadmap-w1",
        rules = mapOf(
            CapabilityLabel.PRIMITIVE_ARITHMETIC to CapabilityPolicyRule(CapabilityStatus.SUPPORTED),
            CapabilityLabel.MODULE_INIT to CapabilityPolicyRule(
                CapabilityStatus.SUPPORTED_WITH_FLAG,
                "moduleRuntimeModel",
            ),
            CapabilityLabel.CALLABLE to CapabilityPolicyRule(
                CapabilityStatus.SUPPORTED_WITH_FLAG,
                "callableValueModel",
            ),
            CapabilityLabel.ITERATOR to CapabilityPolicyRule(
                CapabilityStatus.SUPPORTED_WITH_FLAG,
                "iteratorModel",
            ),
            CapabilityLabel.ARRAY_OBJECT to CapabilityPolicyRule(CapabilityStatus.NEEDS_DYNAMIC_PROBE),
            CapabilityLabel.MAP_SET to CapabilityPolicyRule(
                CapabilityStatus.SUPPORTED_WITH_FLAG,
                "exactCollectionBuiltins",
            ),
            CapabilityLabel.BUILTIN_CALL to CapabilityPolicyRule(
                CapabilityStatus.SUPPORTED_WITH_FLAG,
                "exactCollectionBuiltins",
            ),
            CapabilityLabel.SPREAD_YIELD to CapabilityPolicyRule(CapabilityStatus.UNSUPPORTED),
            CapabilityLabel.UNRESOLVED_POINTER_CALL to CapabilityPolicyRule(CapabilityStatus.NEEDS_DYNAMIC_PROBE),
        ),
    )
}

object StaticCapabilityClassifier {
    private val labelOrder = CapabilityLabel.values.withIndex().associate { it.value to it.index }

    fun classify(
        target: SourceTargetRecord,
        prefix: CapabilityPrefixSlice,
        policy: CapabilityPolicy = RoadmapCapabilityPolicy.default,
    ): StaticCapabilityRecord {
        val knownFacts = prefix.facts.filter { it.kind in CapabilityLabel.values }
        val labels = knownFacts.map(CapabilityAstFact::kind)
            .distinct()
            .sortedBy { labelOrder.getValue(it) }
            .ifEmpty { listOf(CapabilityLabel.PRIMITIVE_ARITHMETIC) }
        val provenMandatoryLabels = prefix.mandatoryFacts
            .filter { it.proven && it.kind in CapabilityLabel.values }
            .map(CapabilityAstFact::kind)
            .toSet()
        val mandatoryRules = provenMandatoryLabels.associateWith(policy.rules::getValue)
        val unprovedFact = prefix.facts.any {
            !it.proven || it.kind == CapabilityAstKind.UNKNOWN || it.kind !in CapabilityAstKind.values
        }
        val optionalSensitiveLabel = labels.firstOrNull { label ->
            label !in provenMandatoryLabels && policy.rules.getValue(label).status != CapabilityStatus.SUPPORTED
        }
        val explicitlyExternal = prefix.mandatoryFacts.firstOrNull { fact ->
            fact.proven &&
                fact.kind == CapabilityLabel.UNRESOLVED_POINTER_CALL &&
                fact.evidence.startsWith("external:")
        }
        val mandatoryNeedsProbe = provenMandatoryLabels.firstOrNull { label ->
            policy.rules.getValue(label).status == CapabilityStatus.NEEDS_DYNAMIC_PROBE &&
                !(label == CapabilityLabel.UNRESOLVED_POINTER_CALL && explicitlyExternal != null)
        }
        val mandatoryUnsupported = provenMandatoryLabels.firstOrNull { label ->
            policy.rules.getValue(label).status == CapabilityStatus.UNSUPPORTED
        }
        val mandatoryExternalOnly = explicitlyExternal?.kind ?: provenMandatoryLabels.firstOrNull { label ->
            policy.rules.getValue(label).status == CapabilityStatus.EXTERNAL_ONLY
        }
        val nonExactMapping = target.mappingStatus != "exact"

        val status = when {
            !prefix.complete ||
                unprovedFact ||
                nonExactMapping ||
                optionalSensitiveLabel != null ||
                mandatoryNeedsProbe != null ->
                CapabilityStatus.NEEDS_DYNAMIC_PROBE
            mandatoryUnsupported != null -> CapabilityStatus.UNSUPPORTED
            mandatoryExternalOnly != null -> CapabilityStatus.EXTERNAL_ONLY
            mandatoryRules.values.any { it.status == CapabilityStatus.SUPPORTED_WITH_FLAG } ->
                CapabilityStatus.SUPPORTED_WITH_FLAG
            else -> CapabilityStatus.SUPPORTED
        }
        val primary = when (status) {
            CapabilityStatus.UNSUPPORTED -> {
                checkNotNull(mandatoryUnsupported)
            }
            CapabilityStatus.EXTERNAL_ONLY -> {
                checkNotNull(mandatoryExternalOnly)
            }
            CapabilityStatus.SUPPORTED_WITH_FLAG -> {
                labels.first { policy.rules.getValue(it).requiredFlag != null }
            }
            CapabilityStatus.NEEDS_DYNAMIC_PROBE -> {
                optionalSensitiveLabel
                    ?: mandatoryNeedsProbe
                    ?: labels.maxBy { capabilitySeverity(policy.rules.getValue(it).status) }
            }
            else -> {
                labels.maxBy { capabilitySeverity(policy.rules.getValue(it).status) }
            }
        }
        val reason = when {
            !prefix.complete -> "incomplete_prefix_${prefix.uncertaintyReason ?: "unknown"}"
            unprovedFact -> "unproved_ast_fact_requires_probe"
            nonExactMapping -> "non_exact_mapping_${target.mappingStatus}"
            optionalSensitiveLabel != null -> "optional_prefix_${optionalSensitiveLabel}_requires_probe"
            mandatoryNeedsProbe != null -> "unresolved_prefix_${mandatoryNeedsProbe}_requires_probe"
            status == CapabilityStatus.UNSUPPORTED -> "unsupported_prefix_$primary"
            status == CapabilityStatus.EXTERNAL_ONLY -> "external_only_prefix_$primary"
            status == CapabilityStatus.SUPPORTED_WITH_FLAG -> "supported_prefix_requires_flag"
            else -> "supported_primitive_prefix"
        }
        return StaticCapabilityRecord(
            methodId = target.methodId,
            branchId = target.branchId,
            labels = labels,
            primaryLabel = primary,
            staticStatus = status,
            reasonCode = reason,
            requiredFlags = labels.mapNotNull { policy.rules.getValue(it).requiredFlag }.distinct().sorted(),
            mappingStatus = target.mappingStatus,
            conservativePrefixStmtIndices = prefix.conservativeStmtIndices.distinct().sorted(),
            mandatoryPrefixStmtIndices = prefix.mandatoryStmtIndices.distinct().sorted(),
            prefixComplete = prefix.complete,
        )
    }

    private fun capabilitySeverity(status: String): Int = STATUS_SEVERITY.indexOf(status)

    private val STATUS_SEVERITY = listOf(
        CapabilityStatus.SUPPORTED,
        CapabilityStatus.SUPPORTED_WITH_FLAG,
        CapabilityStatus.NEEDS_DYNAMIC_PROBE,
        CapabilityStatus.EXTERNAL_ONLY,
        CapabilityStatus.UNSUPPORTED,
    )
}
