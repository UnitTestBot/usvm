package org.usvm.api

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsVoidType
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue

fun mockMethodCall(
    scope: TsStepScope,
    method: EtsMethodSignature,
    resultType: EtsType = method.returnType,
) {
    val result = makeFreshUnknownCallResult(scope, resultType)

    scope.doWithState {
        setMockMethodCallResult(method, result)
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
    if (resultType is EtsStringLiteralType) return@calcOnState mkInitializedStringConstant(resultType.value)
    if (resultType == EtsStringType) return@calcOnState makeFreshBoundedString()

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

/** An opaque string still has a real bounded UTF-16 backing and cannot become an arbitrary object reference. */
private fun TsState.makeFreshBoundedString(): UConcreteHeapRef = with(ctx) {
    val string = memory.allocConcrete(EtsStringType)
    val characters = memory.allocConcrete(EtsArrayType(elementType = EtsNumberType, dimensions = 1))
    val length = makeSymbolicPrimitive(sizeSort)
    val minimum = mkBv(0)
    val maximum = mkBv(maxStringLength)
    val lengthBounds = mkAnd(
        mkBvSignedGreaterOrEqualExpr(length, minimum),
        mkBvSignedLessOrEqualExpr(length, maximum),
    )
    pathConstraints += lengthBounds

    memory.initializeArray(
        arrayHeapRef = characters,
        type = stringBackingArrayDescriptor,
        sort = bv16Sort,
        sizeSort = sizeSort,
        contents = (0 until maxStringLength).asSequence().map { makeSymbolicPrimitive(bv16Sort) },
    )
    memory.write(mkStringBackingLengthLValue(characters), length, guard = trueExpr)
    memory.write(mkStringBackingLValue(string), characters, guard = trueExpr)
    boundedStringBackingRefs += string
    string
}
