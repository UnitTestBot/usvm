package org.usvm.machine

import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    val unknownCallModelSelection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All,
    val unknownCallFallback: TsResidualCallPolicy = TsResidualCallPolicy.STOP_PATH,
)
