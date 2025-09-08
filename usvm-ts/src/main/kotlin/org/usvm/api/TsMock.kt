package org.usvm.api

import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsVoidType
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.types.mkFakeValue

fun mockMethodCall(
    scope: TsStepScope,
    method: EtsMethodSignature,
) {
    scope.doWithState {
        val result: UExpr<*>
        if (method.returnType is EtsVoidType) {
            result = ctx.mkUndefinedValue()
        } else {
            val sort = ctx.typeToSort(method.returnType)
            result = when (sort) {
                is UAddressSort -> makeSymbolicRefUntyped()

                is TsUnresolvedSort -> scope.calcOnState {
                    mkFakeValue(
                        scope = scope,
                        boolValue = makeSymbolicPrimitive(ctx.boolSort),
                        fpValue = makeSymbolicPrimitive(ctx.fp64Sort),
                        refValue = makeSymbolicRefUntyped(),
                    )
                }

                else -> makeSymbolicPrimitive(sort)
            }
        }

        methodResult = TsMethodResult.Success.MockedCall(result, method)
    }
}
