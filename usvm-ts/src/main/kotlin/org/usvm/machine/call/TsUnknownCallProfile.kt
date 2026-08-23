package org.usvm.machine.call

import org.jacodb.ets.model.EtsClassSignature
import org.usvm.api.mockMethodCall
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
enum class TsUnknownCallModelApplication {
    APPLIED,
    NOT_APPLICABLE,
}

/**
 * Applies semantic models without exposing their lookup or registry implementation to the dispatcher.
 *
 * A provider returning [TsUnknownCallModelApplication.APPLIED] must update the supplied scope with the model's
 * successor states. The deterministic registry and concrete model implementations are introduced separately.
 */
fun interface TsUnknownCallModelProvider {
    fun apply(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallModelApplication
}

/** Empty provider used until an explicit model registry is configured. */
object TsNoUnknownCallModels : TsUnknownCallModelProvider {
    override fun apply(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallModelApplication =
        TsUnknownCallModelApplication.NOT_APPLICABLE
}

/** Applies the selected model/fallback profile to every residual call. */
class TsProfileUnknownCallDispatcher(
    private val profile: TsUnknownCallProfile,
    private val modelProvider: TsUnknownCallModelProvider,
) : TsUnknownCallDispatcher {
    override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
        if (profile.modelLookup == TsUnknownCallModelLookup.ENABLED &&
            modelProvider.apply(scope, call) == TsUnknownCallModelApplication.APPLIED
        ) {
            return TsUnknownCallOutcome.MODEL_APPLIED
        }

        return when (profile.residualPolicyFor(call)) {
            TsResidualCallPolicy.STOP_PATH -> {
                val falseExpr = scope.calcOnState { ctx.falseExpr }
                scope.assert(falseExpr)
                TsUnknownCallOutcome.PATH_STOPPED
            }

            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> {
                mockMethodCall(scope, call.callee, call.resultType)
                scope.doWithState { newStmt(call.callSite) }
                TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
            }
        }
    }
}
