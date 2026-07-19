package org.usvm.ts.pbt.capability

import org.usvm.ts.pbt.external.ArtifactContractCodec
import org.usvm.ts.pbt.external.ArtifactSourceRange
import org.usvm.ts.pbt.external.ArtifactValidator
import org.usvm.ts.pbt.external.SourceTargetRecord
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest

object CapabilityScanner {
    fun scan(
        sourceTargetsPath: Path,
        policy: CapabilityPolicy = RoadmapCapabilityPolicy.default,
        prefixProvider: (SourceTargetRecord) -> CapabilityPrefixSlice?,
    ): CapabilityScanReport {
        val contract = ArtifactValidator.validateSourceTargets(sourceTargetsPath)
        if (!contract.valid) {
            throw CapabilityContractException(
                contract.issues.map { issue ->
                    CapabilityValidationIssue(issue.path, "source_targets_${issue.code}", issue.message)
                },
            )
        }
        val records = ArtifactContractCodec.decodeSourceTargets(
            sourceTargetsPath.toFile().readText(),
            sourceTargetsPath.toString(),
        )
        return scan(records, records.mapNotNull(prefixProvider), policy)
    }

    fun scan(
        sourceTargets: List<SourceTargetRecord>,
        prefixes: List<CapabilityPrefixSlice>,
        policy: CapabilityPolicy = RoadmapCapabilityPolicy.default,
    ): CapabilityScanReport {
        val inputIssues = validateInputs(sourceTargets, prefixes)
        if (inputIssues.isNotEmpty()) throw CapabilityContractException(inputIssues)

        val prefixByKey = prefixes.associateBy { CapabilityTargetKey(it.methodId, it.branchId) }
        val sortedTargets = sourceTargets.sortedWith(
            compareBy(SourceTargetRecord::methodId, SourceTargetRecord::branchId),
        )
        val records = sortedTargets.map { target ->
            val key = CapabilityTargetKey(target.methodId, target.branchId)
            val prefix = prefixByKey[key] ?: missingPrefix(target)
            StaticCapabilityClassifier.classify(target, prefix, policy)
        }
        val report = CapabilityScanReport(
            classifierVersion = policy.classifierVersion,
            manifestHash = manifestHash(
                sortedTargets,
                records.map { record -> prefixByKey[record.key()] ?: missingPrefix(record) },
                policy,
            ),
            sourceTargetCount = sortedTargets.size,
            statusCounts = CapabilityStatus.values.associateWithTo(linkedMapOf()) { status ->
                records.count { it.staticStatus == status }
            },
            labelCounts = CapabilityLabel.values.associateWithTo(linkedMapOf()) { label ->
                records.count { label in it.labels }
            },
            records = records,
        )
        val validation = CapabilityReportValidator.validate(report, sortedTargets.map(SourceTargetRecord::key).toSet())
        if (!validation.valid) throw CapabilityContractException(validation.issues)
        return report
    }

    private fun validateInputs(
        targets: List<SourceTargetRecord>,
        prefixes: List<CapabilityPrefixSlice>,
    ): List<CapabilityValidationIssue> {
        val issues = mutableListOf<CapabilityValidationIssue>()
        val mappingStatuses = setOf("exact", "oneToMany", "ambiguous", "unmapped", "synthetic")
        val targetKeys = mutableSetOf<CapabilityTargetKey>()
        targets.forEachIndexed { index, target ->
            val path = "sourceTargets[$index]"
            if (target.schemaVersion != CAPABILITY_SCHEMA_VERSION) {
                issues += CapabilityValidationIssue(path, "schema_version", "source-target must use schemaVersion 2")
            }
            if (target.methodId.isBlank() || target.branchId.isBlank()) {
                issues += CapabilityValidationIssue(path, "blank_key", "methodId and branchId must be non-blank")
            }
            if (!targetKeys.add(target.key())) {
                issues += CapabilityValidationIssue(path, "duplicate_target", "duplicate source-target key")
            }
            if (target.mappingStatus !in mappingStatuses) {
                issues += CapabilityValidationIssue("$path.mappingStatus", "unknown_mapping", "unknown mapping status")
            }
        }
        val prefixKeys = mutableSetOf<CapabilityTargetKey>()
        prefixes.forEachIndexed { index, prefix ->
            val path = "prefixes[$index]"
            val key = prefix.key()
            if (!prefixKeys.add(key)) {
                issues += CapabilityValidationIssue(path, "duplicate_prefix", "duplicate prefix key")
            }
            if (key !in targetKeys) {
                issues += CapabilityValidationIssue(path, "extra_prefix", "prefix has no source-target")
            }
            if (prefix.facts.any { it.kind !in CapabilityAstKind.values }) {
                // An additive/new AST kind must be probed, not rejected. This
                // issue is intentionally absent; the classifier sees it as uncertainty.
            }
        }
        return issues
    }

    private fun missingPrefix(target: SourceTargetRecord): CapabilityPrefixSlice = CapabilityPrefixSlice(
        methodId = target.methodId,
        branchId = target.branchId,
        targetStmtIndex = target.stmtIndex,
        conservativeStmtIndices = listOf(target.stmtIndex),
        mandatoryStmtIndices = listOf(target.stmtIndex),
        mandatoryFacts = emptyList(),
        facts = emptyList(),
        complete = false,
        uncertaintyReason = "prefix_not_provided",
    )

    private fun missingPrefix(record: StaticCapabilityRecord): CapabilityPrefixSlice = CapabilityPrefixSlice(
        methodId = record.methodId,
        branchId = record.branchId,
        targetStmtIndex = record.conservativePrefixStmtIndices.lastOrNull() ?: 0,
        conservativeStmtIndices = record.conservativePrefixStmtIndices,
        mandatoryStmtIndices = record.mandatoryPrefixStmtIndices,
        mandatoryFacts = emptyList(),
        facts = emptyList(),
        complete = record.prefixComplete,
        uncertaintyReason = if (record.prefixComplete) null else "prefix_not_provided",
    )

    private fun manifestHash(
        targets: List<SourceTargetRecord>,
        prefixes: List<CapabilityPrefixSlice>,
        policy: CapabilityPolicy,
    ): String {
        val prefixesByKey = prefixes.associateBy(CapabilityPrefixSlice::key)
        val canonical = buildString {
            appendField(policy.canonicalString())
            append('\n')
            targets.forEach { target ->
                val prefix = prefixesByKey.getValue(target.key())
                appendField(target.methodId)
                appendField(target.branchId)
                appendField(target.stmtIndex.toString())
                appendField(target.successorStmtIndex.toString())
                appendField(target.successorOrdinal.toString())
                appendField(target.mappingStatus)
                appendRange(target.tsSourceRange)
                val emittedJsRange = target.emittedJsRange
                if (emittedJsRange == null) {
                    appendField("no-emitted-js-range")
                } else {
                    appendRange(emittedJsRange)
                }
                appendField(target.sourceOrigin.modulePath)
                appendField(target.sourceOrigin.callableName)
                appendField(target.sourceOrigin.callableKind)
                appendField(prefix.complete.toString())
                appendField(prefix.uncertaintyReason.orEmpty())
                appendField(prefix.conservativeStmtIndices.distinct().sorted().joinToString(","))
                appendField(prefix.mandatoryStmtIndices.distinct().sorted().joinToString(","))
                prefix.mandatoryFacts.canonicalFacts().forEach { fact ->
                    appendField("mandatory")
                    appendField(fact.kind)
                    appendField(fact.evidence)
                    appendField(fact.proven.toString())
                }
                prefix.facts.canonicalFacts().forEach { fact ->
                    appendField("conservative")
                    appendField(fact.kind)
                    appendField(fact.evidence)
                    appendField(fact.proven.toString())
                }
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.length).append(':').append(value).append('|')
    }

    private fun StringBuilder.appendRange(range: ArtifactSourceRange) {
        appendField(range.fileName)
        appendField(range.startOffset.toString())
        appendField(range.endOffset.toString())
        appendField(range.startLine.toString())
        appendField(range.startColumn.toString())
        appendField(range.endLine.toString())
        appendField(range.endColumn.toString())
    }
}

internal fun SourceTargetRecord.key(): CapabilityTargetKey = CapabilityTargetKey(methodId, branchId)
internal fun CapabilityPrefixSlice.key(): CapabilityTargetKey = CapabilityTargetKey(methodId, branchId)
internal fun StaticCapabilityRecord.key(): CapabilityTargetKey = CapabilityTargetKey(methodId, branchId)
