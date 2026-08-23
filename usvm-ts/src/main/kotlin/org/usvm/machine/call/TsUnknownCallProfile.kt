package org.usvm.machine.call

import org.jacodb.ets.model.EtsClassSignature
import org.usvm.api.mockMethodCall
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.newStmt

/** The externally observable decision made for a call that could not be executed normally. */
enum class TsUnknownCallOutcome {
    MODEL_APPLIED,
    FRESH_SYMBOLIC_RETURN,
    PATH_STOPPED,
}

/** Controls whether the dispatcher asks the configured model provider to handle a call. */
enum class TsUnknownCallModelLookup {
    DISABLED,
    ENABLED,
}

/**
 * Selects what happens when model lookup is disabled or no model applies.
 *
 * [FRESH_SYMBOLIC_RETURN] creates a new symbolic value of the call expression's result type and advances past the
 * call. It deliberately ignores all callee side effects and exceptions, so it is an opaque continuation rather than
 * a semantic model of the callee.
 */
enum class TsResidualCallPolicy {
    STOP_PATH,
    FRESH_SYMBOLIC_RETURN,
}

/** Independently configures model lookup and the fallback for residual calls. */
data class TsUnknownCallProfile(
    val modelLookup: TsUnknownCallModelLookup,
    val residualPolicy: TsResidualCallPolicy,
    val residualOverrides: Map<EtsClassSignature, TsResidualCallPolicy> = emptyMap(),
) {
    internal fun residualPolicyFor(call: TsUnknownCall): TsResidualCallPolicy =
        residualOverrides[call.callee.enclosingClass] ?: residualPolicy
}

/** Ready-to-use profiles for the four supported model/fallback combinations. */
object TsUnknownCallProfiles {
    val STOP_ALL = TsUnknownCallProfile(
        modelLookup = TsUnknownCallModelLookup.DISABLED,
        residualPolicy = TsResidualCallPolicy.STOP_PATH,
    )
    val FRESH_SYMBOLIC_FOR_ALL = TsUnknownCallProfile(
        modelLookup = TsUnknownCallModelLookup.DISABLED,
        residualPolicy = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
    )
    val MODELS_THEN_STOP = TsUnknownCallProfile(
        modelLookup = TsUnknownCallModelLookup.ENABLED,
        residualPolicy = TsResidualCallPolicy.STOP_PATH,
    )
    val MODELS_THEN_FRESH_SYMBOLIC = TsUnknownCallProfile(
        modelLookup = TsUnknownCallModelLookup.ENABLED,
        residualPolicy = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
    )
}

/** The result of asking a model provider to handle one unknown call. */
sealed interface TsUnknownCallModelApplication {
    /** Identifies the semantic model that produced the successor states. */
    data class Applied(
        val modelId: String,
    ) : TsUnknownCallModelApplication {
        init {
            require(modelId.isNotBlank()) { "Applied model ID must not be blank" }
        }
    }

    /** Indicates that the provider has no semantic model for this call. */
    data object NotApplicable : TsUnknownCallModelApplication
}

/**
 * Applies semantic models without exposing their lookup or registry implementation to the dispatcher.
 *
 * A provider returning [TsUnknownCallModelApplication.Applied] must update the supplied scope with the model's
 * successor states. The deterministic registry and concrete model implementations are introduced separately.
 */
fun interface TsUnknownCallModelProvider {
    fun apply(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallModelApplication
}

/** Empty provider used until an explicit model registry is configured. */
object TsNoUnknownCallModels : TsUnknownCallModelProvider {
    override fun apply(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallModelApplication =
        TsUnknownCallModelApplication.NotApplicable
}

/** Applies the selected model/fallback profile to every residual call. */
class TsProfileUnknownCallDispatcher(
    private val profile: TsUnknownCallProfile,
    private val modelProvider: TsUnknownCallModelProvider,
    private val observer: TsInterpreterObserver? = null,
) : TsUnknownCallDispatcher {
    override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
        val residualReason = when (profile.modelLookup) {
            TsUnknownCallModelLookup.DISABLED -> {
                TsUnknownCallResidualReason.MODEL_LOOKUP_DISABLED
            }

            TsUnknownCallModelLookup.ENABLED -> {
                when (val application = modelProvider.apply(scope, call)) {
                    is TsUnknownCallModelApplication.Applied -> {
                        val event = event(
                            call = call,
                            outcome = TsUnknownCallOutcome.MODEL_APPLIED,
                            decision = TsUnknownCallDecision.ModelApplied(modelId = application.modelId),
                        )
                        observer?.onUnknownCallSafely(event)
                        return TsUnknownCallOutcome.MODEL_APPLIED
                    }

                    TsUnknownCallModelApplication.NotApplicable -> {
                        TsUnknownCallResidualReason.MODEL_NOT_APPLICABLE
                    }
                }
            }
        }

        val residualPolicy = profile.residualPolicyFor(call)
        val outcome = when (residualPolicy) {
            TsResidualCallPolicy.STOP_PATH -> TsUnknownCallOutcome.PATH_STOPPED
            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
        }
        val event = event(
            call = call,
            outcome = outcome,
            decision = TsUnknownCallDecision.ResidualFallback(
                policy = residualPolicy,
                reason = residualReason,
            ),
        )
        when (residualPolicy) {
            TsResidualCallPolicy.STOP_PATH -> {
                val falseExpr = scope.calcOnState { ctx.falseExpr }
                scope.assert(falseExpr)
            }

            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> {
                mockMethodCall(scope, call.callee, call.resultType)
                scope.doWithState { newStmt(call.callSite) }
            }
        }

        observer?.onUnknownCallSafely(event)
        return outcome
    }

    private fun event(
        call: TsUnknownCall,
        outcome: TsUnknownCallOutcome,
        decision: TsUnknownCallDecision,
    ) = TsUnknownCallEvent(
        callSite = call.callSite,
        callee = call.callee,
        failureReason = call.failureReason,
        profile = profile.copy(residualOverrides = profile.residualOverrides.toMap()),
        outcome = outcome,
        decision = decision,
    )
}
