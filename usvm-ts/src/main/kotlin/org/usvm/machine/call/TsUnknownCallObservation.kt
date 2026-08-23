package org.usvm.machine.call

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStmt
import org.usvm.machine.TsInterpreterObserver

private val logger = KotlinLogging.logger {}

/** Explains why a call reached the residual fallback instead of a semantic model. */
enum class TsUnknownCallResidualReason {
    MODEL_LOOKUP_DISABLED,
    MODEL_NOT_APPLICABLE,
}

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
        val reason: TsUnknownCallResidualReason,
    ) : TsUnknownCallDecision
}

/** A structured decision reported for one unknown call. */
data class TsUnknownCallEvent(
    val callSite: EtsStmt,
    val callee: EtsMethodSignature,
    val failureReason: TsUnknownCallFailureReason,
    val profile: TsUnknownCallProfile,
    val outcome: TsUnknownCallOutcome,
    val decision: TsUnknownCallDecision,
)

internal fun TsInterpreterObserver.onUnknownCallSafely(event: TsUnknownCallEvent) {
    runCatching { onUnknownCall(event) }
        .onFailure { error -> logger.warn(error) { "Unknown-call observer failed while recording an event" } }
}
