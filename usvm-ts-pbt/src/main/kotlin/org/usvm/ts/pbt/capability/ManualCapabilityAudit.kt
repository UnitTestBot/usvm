package org.usvm.ts.pbt.capability

import kotlinx.serialization.Serializable

@Serializable
data class ManualUnsupportedPrefixAnnotation(
    val methodId: String,
    val branchId: String,
    val expectedUnsupported: Boolean,
    val reviewEvidence: String,
)

@Serializable
data class UnsupportedPrefixMetrics(
    val sampleSize: Int,
    val truePositive: Int,
    val falsePositive: Int,
    val trueNegative: Int,
    val falseNegative: Int,
    val precision: Double,
    val recall: Double,
)

object ManualCapabilityAudit {
    fun evaluate(
        report: CapabilityScanReport,
        annotations: List<ManualUnsupportedPrefixAnnotation>,
    ): UnsupportedPrefixMetrics {
        val duplicateAnnotations = annotations.groupBy { CapabilityTargetKey(it.methodId, it.branchId) }
            .filterValues { it.size != 1 }
        require(duplicateAnnotations.isEmpty()) { "duplicate manual annotations: ${duplicateAnnotations.keys}" }
        require(annotations.all { it.reviewEvidence.isNotBlank() }) { "manual annotations require review evidence" }
        val records = report.records.associateBy(StaticCapabilityRecord::key)
        val outcomes = annotations.map { annotation ->
            val key = CapabilityTargetKey(annotation.methodId, annotation.branchId)
            val record = requireNotNull(records[key]) { "manual annotation $key has no capability record" }
            val predictedUnsupported = record.staticStatus in setOf(
                CapabilityStatus.UNSUPPORTED,
                CapabilityStatus.EXTERNAL_ONLY,
            )
            predictedUnsupported to annotation.expectedUnsupported
        }
        val truePositive = outcomes.count { (predicted, expected) -> predicted && expected }
        val falsePositive = outcomes.count { (predicted, expected) -> predicted && !expected }
        val trueNegative = outcomes.count { (predicted, expected) -> !predicted && !expected }
        val falseNegative = outcomes.count { (predicted, expected) -> !predicted && expected }
        val precisionDenominator = truePositive + falsePositive
        val recallDenominator = truePositive + falseNegative
        return UnsupportedPrefixMetrics(
            sampleSize = outcomes.size,
            truePositive = truePositive,
            falsePositive = falsePositive,
            trueNegative = trueNegative,
            falseNegative = falseNegative,
            precision = if (precisionDenominator == 0) 0.0 else truePositive.toDouble() / precisionDenominator,
            recall = if (recallDenominator == 0) 0.0 else truePositive.toDouble() / recallDenominator,
        )
    }
}
