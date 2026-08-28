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
    scope.doWithState {
        mockMethodCall(method = method, resultType = resultType)
    }
}

/** Creates a fresh opaque result directly on this state without applying callee effects or exceptions. */
fun TsState.mockMethodCall(
    method: EtsMethodSignature,
    resultType: EtsType = method.returnType,
) {
    val result = freshUnknownCallResult(resultType)
    methodResult = TsMethodResult.Success.MockedCall(result, method)
}

private fun TsState.freshUnknownCallResult(resultType: EtsType): UExpr<*> {
    if (resultType is EtsVoidType) {
        return ctx.mkUndefinedValue()
    }

    return when (val sort = ctx.typeToSort(resultType)) {
        is UAddressSort -> makeSymbolicRefUntyped()

        is TsUnresolvedSort -> mkFakeValue(
            scope = null,
            boolValue = makeSymbolicPrimitive(ctx.boolSort),
            fpValue = makeSymbolicPrimitive(ctx.fp64Sort),
            refValue = makeSymbolicRefUntyped(),
        )

        else -> makeSymbolicPrimitive(sort)
    }
}
