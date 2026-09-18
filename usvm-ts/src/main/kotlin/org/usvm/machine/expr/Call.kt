package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UIteExpr
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.expr.TsExprApproximationResult.NoApproximation
import org.usvm.machine.expr.TsExprApproximationResult.ResolveFailure
import org.usvm.machine.expr.TsExprApproximationResult.SuccessfulApproximation
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.extractValue
import org.usvm.memory.splitUHeapRef

internal fun TsExprResolver.handleInstanceCall(
    expr: EtsInstanceCallExpr,
): UExpr<*>? = with(ctx) {
    // Check if the method was already called and returned a value.
    when (val result = scope.calcOnState { methodResult }) {
        is TsMethodResult.Success -> {
            scope.doWithState { methodResult = TsMethodResult.NoCall }
            return result.value
        }

        is TsMethodResult.TsException -> {
            error("Exception should be handled earlier")
        }

        is TsMethodResult.NoCall -> {} // proceed to call
    }

    // Try to approximate the call.
    when (val result = tryApproximateGlobalInstanceCall(expr)) {
        is SuccessfulApproximation -> return result.expr
        is ResolveFailure -> return null
        is NoApproximation -> {}
    }

    val instance = resolve(expr.instance) ?: return null

    // Resolve arguments.
    val args = expr.args.map { resolve(it) ?: return null }

    // Call.
    callInstanceMethod(scope, expr, instance, args)
}

fun TsContext.callInstanceMethod(
    scope: TsStepScope,
    call: EtsInstanceCallExpr,
    instance: UExpr<*>,
    args: List<UExpr<*>>,
): UExpr<*>? {
    val returnSite = scope.calcOnState { lastStmt }
    val alternatives = scope.calcOnState { receiverAlternatives(instance) }
    val successors = alternatives.map { (guard, receiver) ->
        val callStmt = TsVirtualMethodCallStmt(
            call = call,
            instance = receiver,
            args = args,
            returnSite = returnSite,
        )
        val advance: TsState.() -> Unit = { newStmt(callStmt) }
        guard to advance
    }

    if (successors.size == 1 && successors.single().first.isTrue) {
        scope.doWithState(successors.single().second)
    } else {
        scope.forkMulti(successors)
    }

    return null
}

/** Keeps the type constraints attached to every receiver passed to the common instance-call pipeline. */
private fun TsState.receiverAlternatives(
    value: UExpr<*>,
    guard: UBoolExpr = ctx.trueExpr,
): List<Pair<UBoolExpr, UExpr<*>>> = with(ctx) {
    if (guard.isFalse) return emptyList()

    when {
        value.isFakeObject() -> listOf(
            extractValue(value, boolSort, ::getIntermediateBoolLValue),
            extractValue(value, fp64Sort, ::getIntermediateFpLValue),
            extractValue(value, addressSort, ::getIntermediateRefLValue),
        ).flatMap { (payload, typeGuard) ->
            receiverAlternatives(requireNotNull(payload), mkAnd(guard, typeGuard))
        }

        value.sort == addressSort && value is UIteExpr<*> -> {
            val refs = splitUHeapRef(
                ref = value.asExpr(addressSort),
                initialGuard = guard,
                ignoreNullRefs = false,
                collapseHeapRefs = false,
            )
            (refs.concreteHeapRefs + refs.symbolicHeapRef).flatMap { (ref, refGuard) ->
                receiverAlternatives(ref, refGuard)
            }
        }

        else -> listOf(guard to value)
    }
}
