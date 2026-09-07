package org.usvm.machine.call

import org.usvm.UExpr
import org.usvm.api.makeFreshUnknownCallResult
import org.usvm.api.mockMethodCall
import org.usvm.api.setMockMethodCallResult
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.mkFakeValue

/** The externally observable effect of an unknown-call decision. */
enum class TsUnknownCallOutcome {
    MODEL_APPLIED,
    FRESH_SYMBOLIC_RETURN,
    PATH_STOPPED,
}

/** Selects what happens when no semantic model handles an unknown call. */
enum class TsResidualCallPolicy {
    STOP_PATH,
    FRESH_SYMBOLIC_RETURN,
}

/** Selects a semantic model and sends unsupported states to one configured fallback. */
class TsModelUnknownCallDispatcher(
    private val models: TsUnknownCallModelCatalog,
    private val fallback: TsResidualCallPolicy,
    private val observer: TsInterpreterObserver? = null,
) : TsUnknownCallModelDispatcher {
    override fun dispatch(scope: TsStepScope, call: TsUnknownCall): TsUnknownCallOutcome {
        val application = scope.calcOnState {
            this@TsModelUnknownCallDispatcher.models.apply(this, call)
        }

        return when (application) {
            is TsUnknownCallModelApplication.Applied -> applyModel(scope, call, application)
            TsUnknownCallModelApplication.NotApplicable -> applyFallback(scope, call)
        }
    }

    private fun applyFallback(
        scope: TsStepScope,
        call: TsUnknownCall,
    ): TsUnknownCallOutcome {
        val decision = TsUnknownCallDecision.ResidualFallback(fallback)

        when (fallback) {
            TsResidualCallPolicy.STOP_PATH -> {
                val falseExpr = scope.calcOnState { ctx.falseExpr }
                scope.assert(falseExpr)
            }

            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> {
                mockMethodCall(scope, call.callee, call.resultType)
                scope.doWithState { newStmt(call.callSite) }
            }
        }

        observer?.onUnknownCallSafely(event(call, decision))
        return decision.outcome
    }

    private fun applyModel(
        scope: TsStepScope,
        call: TsUnknownCall,
        application: TsUnknownCallModelApplication.Applied,
    ): TsUnknownCallOutcome {
        val residualGuard = application.execution.residualGuard
        // Creating an unresolved value may add fake-value constraints. Do it before forking so the residual clone
        // inherits both the constraints and their solver models.
        val freshResidualResult = if (
            residualGuard != null && fallback == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN
        ) {
            makeFreshUnknownCallResult(scope, call.resultType)
        } else {
            null
        }
        val stoppedResidualIsSatisfiable = residualGuard != null &&
            fallback == TsResidualCallPolicy.STOP_PATH &&
            scope.checkSat(residualGuard) != null

        var modelApplied = false
        var modelEventReported = false
        var freshResidualApplied = false
        // Materializing an unresolved result adds its exactly-one constraint. Do it before forking so every
        // successor that uses the wrapper inherits both the constraint and the refreshed solver models.
        val preparedUnresolvedResults = application.execution.successors.map { successor ->
            val completion = successor.completion as? TsUnknownCallModelCompletion.Unresolved
                ?: return@map null

            scope.calcOnState {
                mkFakeValue(scope = scope, value = completion.value)
            }
        }
        val guardedStateChanges = application.execution.successors.mapIndexed { index, successor ->
            successor.guard to modelStateChange(
                call = call,
                modelId = application.modelId,
                successor = successor,
                preparedUnresolvedResult = preparedUnresolvedResults[index],
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

        if (residualGuard != null && fallback == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN) {
            guardedStateChanges += residualGuard to {
                setMockMethodCallResult(call.callee, requireNotNull(freshResidualResult))
                newStmt(call.callSite)
                freshResidualApplied = true

                val decision = TsUnknownCallDecision.ResidualFallback(TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN)
                val fallbackEvent = event(call, decision)
                observer?.onUnknownCallSafely(fallbackEvent)
            }
        }

        scope.forkMulti(guardedStateChanges)

        if (stoppedResidualIsSatisfiable) {
            val decision = TsUnknownCallDecision.ResidualFallback(TsResidualCallPolicy.STOP_PATH)
            val fallbackEvent = event(call, decision)
            observer?.onUnknownCallSafely(fallbackEvent)
        }

        return when {
            modelApplied -> TsUnknownCallOutcome.MODEL_APPLIED
            freshResidualApplied -> TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
            stoppedResidualIsSatisfiable -> TsUnknownCallOutcome.PATH_STOPPED
            else -> error("Semantic model ${application.modelId} produced no satisfiable successor or residual state")
        }
    }

    private fun modelStateChange(
        call: TsUnknownCall,
        modelId: String,
        successor: TsUnknownCallModelSuccessor,
        preparedUnresolvedResult: UExpr<*>?,
        onApplied: () -> Boolean,
    ): TsState.() -> Unit = {
        successor.applyStateChanges(this)

        when (val completion = successor.completion) {
            is TsUnknownCallModelCompletion.Normal -> {
                val result = completion.result(this)
                methodResult = TsMethodResult.Success.MockedCall(result, call.callee)
                newStmt(call.callSite)
            }

            is TsUnknownCallModelCompletion.Unresolved -> {
                val result = requireNotNull(preparedUnresolvedResult) {
                    "Unresolved semantic-model result was not materialized"
                }
                methodResult = TsMethodResult.Success.MockedCall(result, call.callee)
                newStmt(call.callSite)
            }

            is TsUnknownCallModelCompletion.Exceptional -> {
                val (exception, type) = completion.exception(this)
                methodResult = TsMethodResult.TsException(exception, type)
            }

            is TsUnknownCallModelCompletion.EtsIrBody -> {
                enterEtsIrUnknownCallModel(
                    modelId = modelId,
                    entryPoint = completion.entryPoint,
                    inputs = completion.inputs,
                    returnSite = call.callSite,
                )
            }
        }

        if (onApplied()) {
            observer?.onUnknownCallSafely(event(call, TsUnknownCallDecision.ModelApplied(modelId)))
        }
    }

    private fun event(
        call: TsUnknownCall,
        decision: TsUnknownCallDecision,
    ) = TsUnknownCallEvent(
        callSite = call.callSite,
        callee = call.callee,
        failureReason = call.failureReason,
        decision = decision,
    )
}
