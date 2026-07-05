package org.usvm.machine

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    /**
     * Observed input type profiles from a dynamic (e.g. PBT) phase.
     * Restrict fake-object type discriminators for unresolved parameters.
     * [TsInputTypeHints.EMPTY] preserves the default behavior.
     */
    val inputTypeHints: TsInputTypeHints = TsInputTypeHints.EMPTY,
)
