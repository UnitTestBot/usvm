package org.usvm.machine.call

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStmt
import org.usvm.machine.TsInterpreterObserver

private val logger = KotlinLogging.logger {}

/** Describes the model or fallback action selected for one unknown call. */
sealed interface TsUnknownCallDecision {
    data class ModelApplied(
        val modelId: String,
    ) : TsUnknownCallDecision {
        init {
            require(modelId.isNotBlank()) { "Applied model ID must not be blank" }
        }
    }

    data class ResidualFallback(
        val policy: TsResidualCallPolicy,
    ) : TsUnknownCallDecision
}

val TsUnknownCallDecision.outcome: TsUnknownCallOutcome
    get() = when (this) {
        is TsUnknownCallDecision.ModelApplied -> TsUnknownCallOutcome.MODEL_APPLIED
        is TsUnknownCallDecision.ResidualFallback -> when (policy) {
            TsResidualCallPolicy.STOP_PATH -> TsUnknownCallOutcome.PATH_STOPPED
            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
        }
    }

/** A structured decision reported for one unknown call. */
data class TsUnknownCallEvent(
    val callSite: EtsStmt,
    val callee: EtsMethodSignature,
    val failureReason: TsUnknownCallFailureReason,
    val decision: TsUnknownCallDecision,
) {
    val outcome: TsUnknownCallOutcome
        get() = decision.outcome
}

internal fun TsInterpreterObserver.onUnknownCallSafely(event: TsUnknownCallEvent) {
    runCatching { onUnknownCall(event) }
        .onFailure { error -> logger.warn(error) { "Unknown-call observer failed while recording an event" } }
}
