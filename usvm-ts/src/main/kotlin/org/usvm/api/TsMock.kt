package org.usvm.api

import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsVoidType
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkFakeValue

fun mockMethodCall(
    scope: TsStepScope,
    method: EtsMethodSignature,
    resultType: EtsType = method.returnType,
) {
    val result = makeFreshUnknownCallResult(scope = scope, resultType = resultType)

    scope.doWithState {
        setMockMethodCallResult(method = method, result = result)
    }
}

/** Stores a prepared opaque result on this state without applying callee effects or exceptions. */
internal fun TsState.setMockMethodCallResult(
    method: EtsMethodSignature,
    result: UExpr<*>,
) {
    methodResult = TsMethodResult.Success.MockedCall(result, method)
}

/** Creates a fresh opaque result through [scope], keeping solver models consistent with new constraints. */
internal fun makeFreshUnknownCallResult(
    scope: TsStepScope,
    resultType: EtsType,
): UExpr<*> = scope.calcOnState {
    if (resultType is EtsVoidType) return@calcOnState ctx.mkUndefinedValue()

    when (val sort = ctx.typeToSort(resultType)) {
        is UAddressSort -> makeSymbolicRefUntyped()

        is TsUnresolvedSort -> mkFakeValue(
            scope = scope,
            boolValue = makeSymbolicPrimitive(ctx.boolSort),
            fpValue = makeSymbolicPrimitive(ctx.fp64Sort),
            refValue = makeSymbolicRefUntyped(),
        )

        else -> makeSymbolicPrimitive(sort)
    }
}
