package org.usvm.machine.call

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.api.makeFreshUnknownCallResult
import org.usvm.api.mockMethodCall
import org.usvm.api.setMockMethodCallResult
import org.usvm.isTrue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.newStmt
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.solver.UUnknownResult
import org.usvm.solver.UUnsatResult

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

/** Empty provider used until an explicit model registry is configured. */
object TsNoUnknownCallModels : TsUnknownCallModelProvider {
    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication =
        TsUnknownCallModelApplication.NotApplicable
}

/** Applies the selected model/fallback profile to every residual call. */
class TsProfileUnknownCallDispatcher(
    private val profile: TsUnknownCallProfile,
    private val modelProvider: TsUnknownCallModelProvider,
    private val observer: TsInterpreterObserver? = null,
) : TsUnknownCallModelDispatcher {
    override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
        if (profile.modelLookup == TsUnknownCallModelLookup.DISABLED) {
            return applyResidualFallback(
                scope = scope,
                call = call,
                reason = TsUnknownCallResidualReason.MODEL_LOOKUP_DISABLED,
            )
        }

        val application = scope.calcOnState {
            modelProvider.apply(state = this, call = call)
        }
        return when (application) {
            is TsUnknownCallModelApplication.Applied -> applyModel(
                scope = scope,
                call = call,
                application = application,
            )

            TsUnknownCallModelApplication.NotApplicable -> applyResidualFallback(
                scope = scope,
                call = call,
                reason = TsUnknownCallResidualReason.MODEL_NOT_APPLICABLE,
            )
        }
    }

    private fun applyResidualFallback(
        scope: TsStepScope,
        call: TsUnknownCall,
        reason: TsUnknownCallResidualReason,
    ): TsUnknownCallOutcome {
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
                reason = reason,
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

    private fun applyModel(
        scope: TsStepScope,
        call: TsUnknownCall,
        application: TsUnknownCallModelApplication.Applied,
    ): TsUnknownCallOutcome {
        validateExecutionGuards(scope = scope, application = application)

        val residualGuard = application.execution.residualGuard
        val residualPolicy = profile.residualPolicyFor(call)
        val freshResidualResult = if (
            residualGuard != null && residualPolicy == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN
        ) {
            makeFreshUnknownCallResult(scope = scope, resultType = call.resultType)
        } else {
            null
        }
        val stoppedResidualIsSatisfiable = residualGuard != null &&
            residualPolicy == TsResidualCallPolicy.STOP_PATH &&
            scope.checkSat(residualGuard) != null

        var modelApplied = false
        var modelEventReported = false
        var freshResidualApplied = false
        val guardedStateChanges = application.execution.successors.map { successor ->
            successor.guard to modelStateChange(
                call = call,
                application = application,
                successor = successor,
                onApplied = {
                    modelApplied = true
                    if (modelEventReported) {
                        false
                    } else {
                        modelEventReported = true
                        true
                    }
                },
            )
        }.toMutableList()

        if (residualGuard != null && residualPolicy == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN) {
            guardedStateChanges += residualGuard to {
                setMockMethodCallResult(
                    method = call.callee,
                    result = requireNotNull(freshResidualResult),
                )
                newStmt(call.callSite)
                freshResidualApplied = true

                val event = residualEvent(
                    call = call,
                    policy = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
                )
                observer?.onUnknownCallSafely(event)
            }
        }

        scope.forkMulti(guardedStateChanges)

        if (stoppedResidualIsSatisfiable) {
            val event = residualEvent(
                call = call,
                policy = TsResidualCallPolicy.STOP_PATH,
            )
            observer?.onUnknownCallSafely(event)
        }

        return when {
            modelApplied -> TsUnknownCallOutcome.MODEL_APPLIED
            freshResidualApplied -> TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
            stoppedResidualIsSatisfiable -> TsUnknownCallOutcome.PATH_STOPPED
            else -> error("Semantic model ${application.modelId} produced no satisfiable successor or residual state")
        }
    }

    private fun validateExecutionGuards(
        scope: TsStepScope,
        application: TsUnknownCallModelApplication.Applied,
    ) = scope.doWithState {
        val namedGuards = buildList {
            application.execution.successors.forEachIndexed { index, successor ->
                add(NamedGuard(name = "successor[$index]", guard = successor.guard))
            }
            application.execution.residualGuard?.let { residualGuard ->
                add(NamedGuard(name = "residual", guard = residualGuard))
            }
        }
        val overlaps = buildList {
            namedGuards.forEachIndexed { firstIndex, first ->
                namedGuards.drop(firstIndex + 1).forEach { second ->
                    add(
                        GuardOverlap(
                            firstName = first.name,
                            secondName = second.name,
                            condition = ctx.mkAnd(first.guard, second.guard),
                        )
                    )
                }
            }
        }
        val coveredDomain = ctx.mkOr(namedGuards.map(NamedGuard::guard))
        val uncoveredDomain = ctx.mkNot(coveredDomain)
        val invalidity = ctx.mkOr(overlaps.map(GuardOverlap::condition) + uncoveredDomain)
        val validationConstraints = pathConstraints.clone()
        validationConstraints += invalidity

        val solverResult = ctx.solver<EtsType>().check(validationConstraints)
        solverResult.requireConclusiveGuardValidation(modelId = application.modelId)

        when (solverResult) {
            is UUnsatResult -> {
                // The invalidity condition is unreachable, so the guards form a partition.
            }

            is USatResult -> {
                val witnessedOverlap = overlaps.firstOrNull { overlap ->
                    solverResult.model.eval(overlap.condition).isTrue
                }
                if (witnessedOverlap != null) {
                    error(
                        "Semantic model ${application.modelId} produced overlapping guards: " +
                            "${witnessedOverlap.firstName}, ${witnessedOverlap.secondName}"
                    )
                }

                error("Semantic model ${application.modelId} guards do not cover the current call domain")
            }

            is UUnknownResult -> {
                error("Unreachable after conclusive guard validation")
            }
        }
    }

    private fun modelStateChange(
        call: TsUnknownCall,
        application: TsUnknownCallModelApplication.Applied,
        successor: TsUnknownCallModelSuccessor,
        onApplied: () -> Boolean,
    ): TsState.() -> Unit = {
        successor.applyStateChanges(this)

        when (val completion = successor.completion) {
            is TsUnknownCallModelCompletion.Normal -> {
                val result = completion.result(this)
                methodResult = TsMethodResult.Success.MockedCall(result, call.callee)
                newStmt(call.callSite)
            }

            is TsUnknownCallModelCompletion.Exceptional -> {
                val (exception, type) = completion.exception(this)
                methodResult = TsMethodResult.TsException(exception, type)
            }
        }

        if (onApplied()) {
            val event = event(
                call = call,
                outcome = TsUnknownCallOutcome.MODEL_APPLIED,
                decision = TsUnknownCallDecision.ModelApplied(modelId = application.modelId),
            )
            observer?.onUnknownCallSafely(event)
        }
    }

    private fun residualEvent(
        call: TsUnknownCall,
        policy: TsResidualCallPolicy,
    ) = event(
        call = call,
        outcome = when (policy) {
            TsResidualCallPolicy.STOP_PATH -> TsUnknownCallOutcome.PATH_STOPPED
            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
        },
        decision = TsUnknownCallDecision.ResidualFallback(
            policy = policy,
            reason = TsUnknownCallResidualReason.MODEL_NOT_APPLICABLE,
        ),
    )

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

internal fun USolverResult<*>.requireConclusiveGuardValidation(modelId: String) {
    check(this !is UUnknownResult) {
        "Semantic model $modelId guards could not be validated: solver returned UNKNOWN"
    }
}

private data class NamedGuard(
    val name: String,
    val guard: UBoolExpr,
)

private data class GuardOverlap(
    val firstName: String,
    val secondName: String,
    val condition: UBoolExpr,
)
