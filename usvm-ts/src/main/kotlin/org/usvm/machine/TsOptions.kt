package org.usvm.machine

import org.jacodb.ets.model.EtsClassSignature
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.expr.MAX_MODELED_COLLECTION_ENTRIES

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
    /** Bounded native Map/Set input domain; null retains unsupported symbolic collection inputs. */
    val maxInputCollectionSize: Int? = null,
    val inputPropertyPresence: TsInputPropertyPresence = TsInputPropertyPresence.DECLARED_FIELDS,
    val unknownCallModelSelection: TsUnknownCallModelSelection = TsUnknownCallModelSelection.All,
    val unknownCallFallback: TsResidualCallPolicy = TsResidualCallPolicy.STOP_PATH,
    /** Fixed experiment clock used by `Date.now()` and `new Date()`; `null` leaves those calls unsupported. */
    val dateNowMilliseconds: Double? = null,
    /** Erase TypeScript assertions; false retains the existing ETS cast constraints and unsupported casts. */
    val eraseTypeAssertions: Boolean = false,
    /** CLASS coverage targets for wrapper entry points; null uses the entry points' declaring classes. */
    val classCoverageTargets: Set<EtsClassSignature>? = null,
) {
    init {
        require(maxInputCollectionSize == null || maxInputCollectionSize in 1..MAX_MODELED_COLLECTION_ENTRIES) {
            "Input collection capacity must be between 1 and 16, or disabled"
        }

        val hasCoverageTargets = classCoverageTargets == null || classCoverageTargets.isNotEmpty()
        require(hasCoverageTargets) { "Explicit class coverage targets must not be empty" }

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
