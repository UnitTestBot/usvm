package org.usvm.ts.pbt.capability

import org.usvm.ts.pbt.external.SourceTargetRecord
import org.usvm.ts.pbt.replay.DynamicProbeOutcome
import org.usvm.ts.pbt.replay.ReplayCapabilityAssessment
import org.usvm.ts.pbt.replay.ReplayCapabilityProvider
import org.usvm.ts.pbt.replay.StaticCapabilityLabel
import org.usvm.ts.pbt.replay.TerminalCapabilityStatus

data class CapabilityDynamicProbeResult(
    val outcome: String,
    val reasonCode: String,
)

/** Makes the five-state static audit compatible with replay's terminal contract. */
class ScannedCapabilityReplayProvider(
    report: CapabilityScanReport,
    private val enabledFlags: Set<String> = emptySet(),
    dynamicProbes: Map<CapabilityTargetKey, CapabilityDynamicProbeResult> = emptyMap(),
) : ReplayCapabilityProvider {
    private val records = report.records.associateBy(StaticCapabilityRecord::key)
    private val probes = dynamicProbes.toMap()

    init {
        val reportValidation = CapabilityReportValidator.validate(report)
        require(reportValidation.valid) { "invalid static capability report: ${reportValidation.issues}" }
        val requiredProbeKeys = report.records
            .filter { it.staticStatus == CapabilityStatus.NEEDS_DYNAMIC_PROBE }
            .map(StaticCapabilityRecord::key)
            .toSet()
        require(probes.keys == requiredProbeKeys) {
            "dynamic probes must match needs_dynamic_probe targets: missing=${requiredProbeKeys - probes.keys}, " +
                "extra=${probes.keys - requiredProbeKeys}"
        }
        probes.forEach { (key, probe) ->
            require(probe.outcome in DynamicProbeOutcome.terminalValues) { "non-terminal probe outcome for $key" }
            require(probe.reasonCode.isNotBlank()) { "blank dynamic probe reason for $key" }
        }
    }

    override fun assess(target: SourceTargetRecord): ReplayCapabilityAssessment {
        val key = target.key()
        val record = requireNotNull(records[key]) { "target $key is absent from the static capability report" }
        return when (record.staticStatus) {
            CapabilityStatus.SUPPORTED -> {
                terminalAssessment(supported = true, record.reasonCode)
            }
            CapabilityStatus.SUPPORTED_WITH_FLAG -> {
                val missingFlags = record.requiredFlags.filter { it !in enabledFlags }
                if (missingFlags.isEmpty()) {
                    terminalAssessment(supported = true, "${record.reasonCode}_enabled")
                } else {
                    terminalAssessment(supported = false, "required_flag_disabled_${missingFlags.joinToString("_")}")
                }
            }
            CapabilityStatus.EXTERNAL_ONLY,
            CapabilityStatus.UNSUPPORTED,
            -> {
                terminalAssessment(supported = false, record.reasonCode)
            }
            CapabilityStatus.NEEDS_DYNAMIC_PROBE -> {
                val probe = probes.getValue(key)
                ReplayCapabilityAssessment(
                    staticLabel = StaticCapabilityLabel.NEEDS_DYNAMIC_PROBE,
                    staticReasonCode = record.reasonCode,
                    dynamicProbeOutcome = probe.outcome,
                    dynamicProbeReasonCode = probe.reasonCode,
                    terminalStatus = probe.outcome,
                    terminalReasonCode = probe.reasonCode,
                )
            }
            else -> {
                error("validated capability report contains status '${record.staticStatus}'")
            }
        }
    }

    private fun terminalAssessment(supported: Boolean, reasonCode: String): ReplayCapabilityAssessment {
        val status = if (supported) TerminalCapabilityStatus.SUPPORTED else TerminalCapabilityStatus.UNSUPPORTED
        return ReplayCapabilityAssessment(
            staticLabel = status,
            staticReasonCode = reasonCode,
            terminalStatus = status,
            terminalReasonCode = reasonCode,
        )
    }
}
