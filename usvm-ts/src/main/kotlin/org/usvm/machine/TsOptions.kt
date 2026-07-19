package org.usvm.machine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    /**
     * Replace the legacy targeted-run timeout with a no-terminal-progress window.
     * The flag is deliberately off by default; [progressTimeout] is ignored while it is off.
     */
    val symbolicProgressStop: Boolean = false,
    val progressTimeout: Duration? = null,
    /**
     * Reject forks that are proven unable to reach any target active in the current state.
     * Disabled by default until the TS reachability campaign gate is complete.
     */
    val tsTargetReachabilityPruning: Boolean = false,
    /**
     * Observed input type profiles from a dynamic (e.g. PBT) phase.
     * Restrict fake-object type discriminators for unresolved parameters.
     * [TsInputTypeHints.EMPTY] preserves the default behavior.
     */
    val inputTypeHints: TsInputTypeHints = TsInputTypeHints.EMPTY,
) {
    init {
        require(!symbolicProgressStop || progressTimeout != null) {
            "progressTimeout is required when symbolicProgressStop is enabled"
        }
        require(progressTimeout == null || progressTimeout > ZERO && progressTimeout.isFinite()) {
            "progressTimeout must be positive and finite, got $progressTimeout"
        }
    }
}
