package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.UExpr
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from

internal fun TsExprResolver.tryApproximateStaticCall(
    expr: EtsStaticCallExpr,
): TsExprApproximationResult {
    val owner = expr.callee.enclosingClass
    val ownerName = owner.name
    val isUnresolvedGlobal = ownerName == UNKNOWN_CLASS_NAME
    val isTopLevelFunction = ownerName == DEFAULT_ARK_CLASS_NAME && owner.namespace == null

    if (expr.callee.name == "\$r" && (isUnresolvedGlobal || isTopLevelFunction)) {
        return from(handleR())
    }

    if (expr.callee.name == "Number" && isUnresolvedGlobal) {
        return from(handleNumberConverter(expr.args))
    }

    if (expr.callee.name == "Boolean" && isUnresolvedGlobal) {
        return from(handleBooleanConverter(expr))
    }

    return TsExprApproximationResult.NoApproximation
}

internal fun TsExprResolver.tryApproximateResolvedFunctionCall(method: EtsMethod): TsExprApproximationResult {
    // Match the selected top-level function, not its mutable source binding or alias name.
    val owner = method.enclosingClass?.signature
    val isTopLevelFunction = owner?.name == DEFAULT_ARK_CLASS_NAME && owner?.namespace == null
    val isResourceLookup = method.name == "\$r" && isTopLevelFunction
    if (isResourceLookup) {
        return from(handleR())
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

internal fun TsExprResolver.handleNumberConverter(args: List<EtsValue>): UExpr<*>? = with(ctx) {
    if (args.isEmpty()) {
        return mkFp64(0.0)
    }
    check(args.size == 1) {
        "Number() should have exactly one argument, but got ${args.size}"
    }
    val arg = resolve(args.single()) ?: return null
    return mkNumericExpr(arg, scope)
}

private fun TsExprResolver.handleBooleanConverter(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Boolean() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    val truthy = mkTruthyExpr(arg, scope)
    scope.ensureTruthinessSupported(arg) ?: return null

    return truthy
}
