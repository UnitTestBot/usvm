package org.usvm.api

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsVoidType
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkFakeValue

private val logger = KotlinLogging.logger {}

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
                is UAddressSort -> makeSymbolicRef(scope, method.returnType)

                is TsUnresolvedSort -> mkFakeValue(scope, method.returnType)

                else -> makeSymbolicPrimitive(sort)
            }
        }

        methodResult = TsMethodResult.Success.MockedCall(result, method)
    }
}

fun TsState.makeSymbolicRef(
    scope: TsStepScope,
    type: EtsType,
): UHeapRef {
    var canBeNull = false
    var canBeUndefined = false

    if (type is EtsNullType) {
        canBeNull = true
    }
    if (type is EtsUndefinedType) {
        canBeUndefined = true
    }
    if (type is EtsUnionType) {
        for (t in type.types) {
            when (t) {
                is EtsNullType -> canBeNull = true
                is EtsUndefinedType -> canBeUndefined = true
            }
        }
    }

    return makeSymbolicRef(
        scope = scope,
        canBeNull = canBeNull,
        canBeUndefined = canBeUndefined,
    )
}

fun TsState.makeSymbolicRef(
    scope: TsStepScope,
    canBeNull: Boolean = true,
    canBeUndefined: Boolean = true,
): UHeapRef = with(ctx) {
    val ref = makeSymbolicRefUntyped()

    if (!canBeNull) {
        scope.assert(mkNot(mkHeapRefEq(ref, mkTsNullValue()))) ?: run {
            logger.warn { "Failed to assert that symbolic ref is not null" }
        }
    }
    if (!canBeUndefined) {
        scope.assert(mkNot(mkHeapRefEq(ref, mkUndefinedValue()))) ?: run {
            logger.warn { "Failed to assert that symbolic ref is not undefined" }
        }
    }

    ref
}
