package org.usvm.machine.call

import mu.KotlinLogging
import org.usvm.UExpr
import org.usvm.api.makeFreshUnknownCallResult
import org.usvm.api.setMockMethodCallResult
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.call.intrinsic.TsDateEtsIrModelFamily
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.mkFakeValue

private val logger = KotlinLogging.logger {}

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
        val (application, residualPolicy) = scope.calcOnState {
            val catalog = this@TsModelUnknownCallDispatcher.models
            val hasDirectTarget = catalog.select(call) != null
            // A direct target retains precedence even when its model rejects the call or is already active.
            val modelCall = if (hasDirectTarget) {
                call
            } else {
                TsDateEtsIrModelFamily.canonicalModelCall(this, call) ?: call
            }

            val residualPolicy = catalog.select(modelCall)?.residualPolicy(this, modelCall) ?: fallback
            catalog.apply(this, modelCall) to residualPolicy
        }

        return when (application) {
            is TsUnknownCallModelApplication.Applied -> applyModel(scope, call, application, residualPolicy)
            TsUnknownCallModelApplication.NotApplicable -> applyFallback(scope, call, residualPolicy)
        }
    }

    private fun applyFallback(
        scope: TsStepScope,
        call: TsUnknownCall,
        residualPolicy: TsResidualCallPolicy,
    ): TsUnknownCallOutcome {
        val decision = TsUnknownCallDecision.ResidualFallback(residualPolicy)

        when (residualPolicy) {
            TsResidualCallPolicy.STOP_PATH -> {
                val stoppedState = scope.calcOnState { this }
                val falseExpr = stoppedState.ctx.falseExpr
                logger.warn {
                    "Stopping path for unknown call ${call.callee} at ${call.callSite.location}: " +
                        "reason=${call.failureReason}"
                }
                observer?.onPathStoppedSafely(state = stoppedState, reason = stoppedPathReason(call))
                scope.assert(falseExpr)
            }

            TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN -> {
                val result = makeFreshUnknownCallResult(scope, call) ?: return TsUnknownCallOutcome.PATH_STOPPED
                scope.doWithState {
                    setMockMethodCallResult(call.callee, result)
                    newStmt(call.callSite)
                }
            }
        }

        reportFallback(call, residualPolicy)
        return decision.outcome
    }

    private fun applyModel(
        scope: TsStepScope,
        call: TsUnknownCall,
        application: TsUnknownCallModelApplication.Applied,
        residualPolicy: TsResidualCallPolicy,
    ): TsUnknownCallOutcome {
        val residualGuard = application.execution.residualGuard
        // Creating an unresolved value may add fake-value constraints. Do it before forking so the residual clone
        // inherits both the constraints and their solver models.
        val freshResidualResult = if (
            residualGuard != null && residualPolicy == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN
        ) {
            makeFreshUnknownCallResult(scope, call) ?: return TsUnknownCallOutcome.PATH_STOPPED
        } else {
            null
        }
        val stoppedResidualState = if (residualGuard != null && residualPolicy == TsResidualCallPolicy.STOP_PATH) {
            scope.checkSat(residualGuard)
        } else {
            null
        }
        val stoppedResidualIsSatisfiable = stoppedResidualState != null

        var modelApplied = false
        var freshResidualApplied = false
        // Materializing an unresolved result adds its exactly-one constraint. Do it before forking so every
        // successor that uses the wrapper inherits both the constraint and the refreshed solver models.
        val preparedUnresolvedResults = application.execution.successors.map { successor ->
            val completion = successor.completion as? TsUnknownCallModelCompletion.Unresolved
                ?: return@map null

            val result = scope.calcOnState {
                mkFakeValue(scope = scope, value = completion.value)
            } ?: return TsUnknownCallOutcome.PATH_STOPPED
            result
        }
        val guardedStateChanges = application.execution.successors.mapIndexed { index, successor ->
            successor.guard to modelStateChange(
                call = call,
                modelId = application.modelId,
                successor = successor,
                preparedUnresolvedResult = preparedUnresolvedResults[index],
                onApplied = { modelApplied = true },
            )
        }.toMutableList()

        if (residualGuard != null && residualPolicy == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN) {
            guardedStateChanges += residualGuard to {
                setMockMethodCallResult(call.callee, requireNotNull(freshResidualResult))
                newStmt(call.callSite)
                freshResidualApplied = true
            }
        }

        if (stoppedResidualState != null) {
            logger.warn {
                "Stopping residual path for unknown call ${call.callee} at ${call.callSite.location}: " +
                    "reason=${call.failureReason}"
            }
            observer?.onPathStoppedSafely(state = stoppedResidualState, reason = stoppedPathReason(call))
        }
        scope.forkMulti(guardedStateChanges)

        // A complete relation (including assume(false)) may have no feasible outputs. That is pruning,
        // not an unsupported call and not a request to invent a fresh result.
        val modelPruned = !modelApplied && !freshResidualApplied && !stoppedResidualIsSatisfiable
        if (modelApplied || modelPruned) {
            observer?.onUnknownCallSafely(event(call, TsUnknownCallDecision.ModelApplied(application.modelId)))
        }
        if (freshResidualApplied || stoppedResidualIsSatisfiable) {
            reportFallback(call, residualPolicy)
        }

        return when {
            modelApplied -> TsUnknownCallOutcome.MODEL_APPLIED
            freshResidualApplied -> TsUnknownCallOutcome.FRESH_SYMBOLIC_RETURN
            stoppedResidualIsSatisfiable -> TsUnknownCallOutcome.PATH_STOPPED
            else -> TsUnknownCallOutcome.MODEL_APPLIED
        }
    }

    private fun stoppedPathReason(call: TsUnknownCall): String =
        "Unknown call ${call.callee} at ${call.callSite.location}: reason=${call.failureReason}"

    private fun reportFallback(call: TsUnknownCall, residualPolicy: TsResidualCallPolicy) {
        if (residualPolicy == TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN) {
            logger.debug {
                "Unknown call ${call.callee} at ${call.callSite.location}: " +
                    "fallback=$residualPolicy, reason=${call.failureReason}"
            }
        }
        observer?.onUnknownCallSafely(event(call, TsUnknownCallDecision.ResidualFallback(residualPolicy)))
    }

    private fun modelStateChange(
        call: TsUnknownCall,
        modelId: String,
        successor: TsUnknownCallModelSuccessor,
        preparedUnresolvedResult: UExpr<*>?,
        onApplied: () -> Unit,
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

        onApplied()
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

private fun TsInterpreterObserver.onPathStoppedSafely(state: TsState, reason: String) {
    runCatching { onPathStopped(state = state, reason = reason) }
        .onFailure { error -> logger.warn(error) { "Unknown-call observer failed while recording a stopped path" } }
}
