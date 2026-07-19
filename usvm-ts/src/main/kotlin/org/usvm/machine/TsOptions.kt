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
     * Enable exact lookup for the supported stateless ES module subset.
     * Kept off until the hybrid integration layer opts into the semantic profile.
     */
    val moduleRuntimeModel: Boolean = false,
    /**
     * Materialize stable function values and dispatch pointer calls exactly when
     * their source method can be resolved. Dynamic/opaque callables are rejected.
     */
    val callableValueModel: Boolean = false,
    /**
     * Enable the synchronous iterator protocol subset used by the TS fixtures.
     * Generators, async iterators and dynamic iterator replacement stay outside it.
     */
    val iteratorModel: Boolean = false,
    /**
     * Enable exact models and rejection boundaries for collection builtins.
     * Unsupported property-presence and Map state never become unconstrained results.
     */
    val exactCollectionBuiltins: Boolean = false,
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
