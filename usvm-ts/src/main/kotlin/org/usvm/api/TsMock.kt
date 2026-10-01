package org.usvm.api

import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsVoidType
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
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
    val prepared = prepareFreshUnknownCallResult(scope, resultType)
    prepared.admissibilityGuard?.let { guard ->
        requireNotNull(scope.assert(guard)) {
            "A fresh string result must admit the string type"
        }
    }

    scope.doWithState {
        setMockMethodCallResult(method, prepared.value)
    }
}

/** Stores a prepared opaque result on this state without applying callee effects or exceptions. */
internal fun TsState.setMockMethodCallResult(
    method: EtsMethodSignature,
    result: UExpr<*>,
) {
    methodResult = TsMethodResult.Success.MockedCall(result, method)
}

internal data class PreparedFreshUnknownCallResult(
    val value: UExpr<*>,
    val admissibilityGuard: UBoolExpr? = null,
)

/** Prepares a fresh opaque result; callers must apply [PreparedFreshUnknownCallResult.admissibilityGuard]. */
internal fun prepareFreshUnknownCallResult(
    scope: TsStepScope,
    resultType: EtsType,
): PreparedFreshUnknownCallResult {
    if (resultType is EtsStringType) {
        // The type guard belongs only to the branch using this result. Asserting it before a
        // partial model forks could discard satisfiable model successors.
        val ref = scope.calcOnState { makeSymbolicRefUntyped() }
        val guard = scope.calcOnState { memory.types.evalTypeEquals(ref, EtsStringType) }
        return PreparedFreshUnknownCallResult(value = ref, admissibilityGuard = guard)
    }

    val value = scope.calcOnState {
        if (resultType is EtsVoidType) return@calcOnState ctx.mkUndefinedValue()

        when (val sort = ctx.typeToSort(resultType)) {
            is UAddressSort -> makeSymbolicRefUntyped()

            is TsUnresolvedSort -> mkFakeValue(
                scope,
                boolValue = makeSymbolicPrimitive(ctx.boolSort),
                fpValue = makeSymbolicPrimitive(ctx.fp64Sort),
                refValue = makeSymbolicRefUntyped(),
            )

            else -> makeSymbolicPrimitive(sort)
        }
    }
    return PreparedFreshUnknownCallResult(value = value)
}
