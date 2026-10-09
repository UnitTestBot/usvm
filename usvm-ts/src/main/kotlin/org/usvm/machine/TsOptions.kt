package org.usvm.machine

import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection

/** Initial own-property assumptions for symbolic input objects; writes and deletes always take precedence. */
enum class TsInputPropertyPresence {
    /** Required declared fields are present; optional and undeclared fields remain symbolic. */
    DECLARED_FIELDS,

    /** Every queried own property may initially be present or absent, independently of annotations. */
    SYMBOLIC,

    /** Every queried own property is initially present, possibly with an undefined value. */
    ASSUME_PRESENT,

    /** Every queried own property is initially absent. */
    ASSUME_ABSENT,
}

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
    val inputPropertyPresence: TsInputPropertyPresence = TsInputPropertyPresence.DECLARED_FIELDS,
    val unknownCallModelSelection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All,
    val unknownCallFallback: TsResidualCallPolicy = TsResidualCallPolicy.STOP_PATH,
)
