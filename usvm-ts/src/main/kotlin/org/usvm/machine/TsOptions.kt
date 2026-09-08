package org.usvm.machine

import org.usvm.machine.call.TsResidualCallPolicy

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    /** `null` enables every built-in model; an empty set disables all models. */
    val enabledUnknownCallModelIds: Set<String>? = null,
    val unknownCallFallback: TsResidualCallPolicy = TsResidualCallPolicy.STOP_PATH,
)
