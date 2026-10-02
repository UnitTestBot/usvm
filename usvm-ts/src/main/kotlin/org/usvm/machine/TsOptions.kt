package org.usvm.machine

import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    val unknownCallModelSelection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All,
    val unknownCallFallback: TsResidualCallPolicy = TsResidualCallPolicy.STOP_PATH,
    /** Fixed experiment clock used by `Date.now()` and `new Date()`; `null` leaves those calls unsupported. */
    val dateNowMilliseconds: Double? = null,
) {
    init {
        val isValidDateNow = dateNowMilliseconds == null ||
            dateNowMilliseconds.isFinite() &&
            dateNowMilliseconds % 1.0 == 0.0 &&
            dateNowMilliseconds in -DATE_TIME_CLIP_BOUND_MILLIS..DATE_TIME_CLIP_BOUND_MILLIS
        require(isValidDateNow) {
            "The fixed Date clock must be an integral TimeClip-range millisecond timestamp"
        }
    }

    private companion object {
        const val DATE_TIME_CLIP_BOUND_MILLIS = 8_640_000_000_000_000.0
    }
}
