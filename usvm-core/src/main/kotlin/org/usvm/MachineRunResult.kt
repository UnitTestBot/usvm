package org.usvm

/** Why a completed machine run stopped; exceptions continue to propagate separately. */
enum class UMachineStopReason {
    EXHAUSTED,
    STOPPED,
    FORK_LIMIT,
}

/**
 * Completion metadata for one run. [generatedForks] counts emitted additional successors, including terminal
 * alternatives, without issuing solver queries. Providers may emit alternatives whose feasibility is not established.
 * A whole interpreter step finishes before checking [forkLimit], so the observed count may exceed that threshold.
 */
data class UMachineRunResult(
    val stopReason: UMachineStopReason,
    val generatedForks: ULong,
    val forkLimit: ULong?,
)
