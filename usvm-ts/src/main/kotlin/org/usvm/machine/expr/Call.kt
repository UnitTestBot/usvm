package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.expr.TsExprApproximationResult.NoApproximation
import org.usvm.machine.expr.TsExprApproximationResult.ResolveFailure
import org.usvm.machine.expr.TsExprApproximationResult.SuccessfulApproximation
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt

private val logger = KotlinLogging.logger {}

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
    when (val result = tryApproximateInstanceCall(expr)) {
        is SuccessfulApproximation -> return result.expr
        is ResolveFailure -> return null
        is NoApproximation -> {}
    }

    // Resolve the instance.
    val instance = run {
        val resolved = resolve(expr.instance) ?: return null
        if (resolved.isFakeObject()) {
            val fakeType = resolved.getFakeType(scope)
            scope.assert(fakeType.refTypeExpr) ?: run {
                logger.warn { "Calls on non-ref (fake) instance is not supported: $expr" }
                return null
            }
            resolved.extractRef(scope)
        } else {
            if (resolved.sort != addressSort) {
                logger.warn { "Calling method on non-ref instance is not yet supported: $expr" }
                scope.assert(falseExpr)
                return null
            }
            resolved.asExpr(addressSort)
        }
    }

    // Check for undefined or null property access.
    checkUndefinedOrNullPropertyRead(scope, instance, expr.callee.name) ?: return null

    // Resolve arguments.
    val args = expr.args.map { resolve(it) ?: return null }

    // Call.
    callInstanceMethod(scope, expr.callee, instance, args)
}

fun TsContext.callInstanceMethod(
    scope: TsStepScope,
    callee: EtsMethodSignature,
    instance: UExpr<*>,
    args: List<UExpr<*>>,
): UExpr<*>? {
    // Create the virtual call statement.
    val virtualCall = TsVirtualMethodCallStmt(
        callee = callee,
        instance = instance,
        args = args,
        returnSite = scope.calcOnState { lastStmt },
    )
    scope.doWithState { newStmt(virtualCall) }

    // Return null to indicate that we are waiting for the call to be executed.
    return null
}
