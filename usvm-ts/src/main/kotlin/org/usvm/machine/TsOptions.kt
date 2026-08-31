package org.usvm.machine

import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.call.TsUnknownCallProfile
import org.usvm.machine.call.TsUnknownCallProfiles

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    val unknownCallProfile: TsUnknownCallProfile = TsUnknownCallProfiles.MODELS_THEN_STOP,
    val unknownCallModels: TsUnknownCallModelSelection = TsUnknownCallModelSelection(),
)
