package org.usvm.machine.expr

import org.jacodb.ets.model.EtsStaticCallExpr
import org.usvm.UExpr
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from

internal fun TsExprResolver.tryApproximateStaticCall(
    expr: EtsStaticCallExpr,
): TsExprApproximationResult {
    // Mock `$r` calls
    if (expr.callee.name == "\$r") {
        return from(handleR())
    }

    // Handle `Number(...)` calls
    if (expr.callee.name == "Number") {
        return from(handleNumberConverter(expr))
    }

    // Handle `Boolean(...)` calls
    if (expr.callee.name == "Boolean") {
        return from(handleBooleanConverter(expr))
    }

    return TsExprApproximationResult.NoApproximation
}

private fun TsExprResolver.handleR(): UExpr<*> = with(ctx) {
    val mockSymbol = scope.calcOnState {
        memory.mocker.createMockSymbol(trackedLiteral = null, addressSort, ownership)
    }
    scope.assert(mkNot(mkEq(mockSymbol, mkTsNullValue())))
    mockSymbol
}

private fun TsExprResolver.handleNumberConverter(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Number() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null
    return mkNumericExpr(arg, scope)
}

private fun TsExprResolver.handleBooleanConverter(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Boolean() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null
    return mkTruthyExpr(arg, scope)
}
