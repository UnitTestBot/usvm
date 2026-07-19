package org.usvm.ts.pbt.capability

object CapabilityReportValidator {
    private val mappingStatuses = setOf("exact", "oneToMany", "ambiguous", "unmapped", "synthetic")
    private val sha256 = Regex("[0-9a-f]{64}")

    fun validate(
        report: CapabilityScanReport,
        expectedTargets: Set<CapabilityTargetKey>? = null,
    ): CapabilityValidationReport {
        val issues = mutableListOf<CapabilityValidationIssue>()
        fun error(path: String, code: String, message: String) {
            issues += CapabilityValidationIssue(path, code, message)
        }

        if (report.schemaVersion != CAPABILITY_SCHEMA_VERSION) {
            error("$.schemaVersion", "schema_version", "capability report must use schemaVersion 2")
        }
        if (!report.classifierVersion.startsWith("$CAPABILITY_CLASSIFIER_BASE_VERSION:")) {
            error("$.classifierVersion", "classifier_version", "unknown classifier version")
        }
        if (!sha256.matches(report.manifestHash)) {
            error("$.manifestHash", "manifest_hash", "manifestHash must be lowercase SHA-256")
        }
        if (report.sourceTargetCount != report.records.size) {
            error("$.sourceTargetCount", "count_mismatch", "sourceTargetCount differs from records size")
        }
        if (report.statusCounts.keys != CapabilityStatus.values) {
            error("$.statusCounts", "unknown_status", "status counts must contain the closed status taxonomy")
        }
        if (report.labelCounts.keys != CapabilityLabel.values) {
            error("$.labelCounts", "unknown_label", "label counts must contain the closed label taxonomy")
        }

        val seen = mutableSetOf<CapabilityTargetKey>()
        report.records.forEachIndexed { index, record ->
            val path = "$.records[$index]"
            if (record.schemaVersion != CAPABILITY_SCHEMA_VERSION) {
                error("$path.schemaVersion", "schema_version", "record must use schemaVersion 2")
            }
            if (record.methodId.isBlank() || record.branchId.isBlank()) {
                error(path, "blank_key", "methodId and branchId must be non-blank")
            }
            if (!seen.add(record.key())) error(path, "duplicate_target", "duplicate capability target key")
            if (record.labels.isEmpty()) error("$path.labels", "missing_label", "at least one label is required")
            if (record.labels.distinct().size != record.labels.size) {
                error("$path.labels", "duplicate_label", "labels must be unique")
            }
            record.labels.filter { it !in CapabilityLabel.values }.forEach {
                error("$path.labels", "unknown_label", "unknown capability label '$it'")
            }
            if (record.primaryLabel !in CapabilityLabel.values) {
                error("$path.primaryLabel", "unknown_label", "unknown primary capability label")
            } else if (record.primaryLabel !in record.labels) {
                error("$path.primaryLabel", "primary_not_in_labels", "primaryLabel must be in labels")
            }
            if (record.staticStatus !in CapabilityStatus.values) {
                error("$path.staticStatus", "unknown_status", "unknown static capability status")
            }
            if (record.reasonCode.isBlank()) error("$path.reasonCode", "blank_reason", "reasonCode is required")
            if (record.requiredFlags.any(String::isBlank) ||
                record.requiredFlags.distinct().size != record.requiredFlags.size
            ) {
                error("$path.requiredFlags", "invalid_flags", "required flags must be non-blank and unique")
            }
            if (record.mappingStatus !in mappingStatuses) {
                error("$path.mappingStatus", "unknown_mapping", "unknown mapping status")
            }
            if (record.conservativePrefixStmtIndices.any { it < 0 } ||
                record.mandatoryPrefixStmtIndices.any { it < 0 }
            ) {
                error(path, "negative_stmt_index", "prefix indices must be non-negative")
            }
            if (!record.conservativePrefixStmtIndices.containsAll(record.mandatoryPrefixStmtIndices)) {
                error(path, "mandatory_outside_prefix", "mandatory prefix must be a subset of the conservative prefix")
            }
            if (record.staticStatus != CapabilityStatus.NEEDS_DYNAMIC_PROBE && !record.prefixComplete) {
                error(path, "unproved_terminal_status", "incomplete prefix cannot prove a terminal status")
            }
            if (record.staticStatus != CapabilityStatus.NEEDS_DYNAMIC_PROBE && record.mappingStatus != "exact") {
                error(path, "unproved_terminal_status", "non-exact mapping cannot prove a terminal status")
            }
        }

        val actualKeys = report.records.map(StaticCapabilityRecord::key).toSet()
        if (expectedTargets != null) {
            (expectedTargets - actualKeys).sorted().forEach { key ->
                error("$.records", "missing_target", "missing capability for ${key.methodId}#${key.branchId}")
            }
            (actualKeys - expectedTargets).sorted().forEach { key ->
                error("$.records", "extra_target", "unexpected capability for ${key.methodId}#${key.branchId}")
            }
        }

        val expectedStatusCounts = CapabilityStatus.values.associateWith { status ->
            report.records.count { it.staticStatus == status }
        }
        if (report.statusCounts != expectedStatusCounts) {
            error("$.statusCounts", "count_mismatch", "status counts differ from records")
        }
        val expectedLabelCounts = CapabilityLabel.values.associateWith { label ->
            report.records.count { label in it.labels }
        }
        if (report.labelCounts != expectedLabelCounts) {
            error("$.labelCounts", "count_mismatch", "label counts differ from records")
        }
        val sorted = report.records.sortedWith(
            compareBy(StaticCapabilityRecord::methodId, StaticCapabilityRecord::branchId),
        )
        if (report.records != sorted) {
            error("$.records", "non_deterministic_order", "records must be sorted by target key")
        }

        return CapabilityValidationReport(issues.isEmpty(), issues)
    }
}
