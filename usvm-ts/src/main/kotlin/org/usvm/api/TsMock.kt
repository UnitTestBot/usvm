package org.usvm.api

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsVoidType
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.dateModelTimestampLValue
import org.usvm.machine.expr.intrinsicDateConstructorReceiver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue

fun mockMethodCall(
    scope: TsStepScope,
    method: EtsMethodSignature,
    resultType: EtsType = method.returnType,
) {
    val result = makeFreshUnknownCallResult(scope, resultType) ?: return

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

/** Native Date construction retains its allocated identity even when its timestamp is opaque. */
internal fun makeFreshUnknownCallResult(scope: TsStepScope, call: TsUnknownCall): UExpr<*>? {
    val date = scope.calcOnState { intrinsicDateConstructorReceiver(call) }
    if (date == null) return makeFreshUnknownCallResult(scope, call.resultType)

    scope.doWithState {
        val timestamp = makeSymbolicPrimitive(ctx.fp64Sort)
        memory.write(ctx.dateModelTimestampLValue(date), timestamp, guard = ctx.trueExpr)
    }
    return date
}

/** Creates a fresh opaque result through [scope], keeping solver models consistent with new constraints. */
internal fun makeFreshUnknownCallResult(
    scope: TsStepScope,
    resultType: EtsType,
): UExpr<*>? = scope.calcOnState {
    if (resultType is EtsVoidType) return@calcOnState ctx.mkUndefinedValue()
    val literal = makeLiteralResult(resultType)
    if (literal != null) return@calcOnState literal
    if (resultType == EtsStringType) return@calcOnState makeFreshBoundedString()
    if (resultType is EtsUnionType) {
        val variants = resultType.finiteLiteralVariants()
        if (!variants.isNullOrEmpty()) {
            return@calcOnState makeFiniteLiteralUnion(scope = scope, variants = variants)
        }
    }

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

private fun EtsType.finiteLiteralVariants(): List<EtsType>? {
    return when (this) {
        is EtsUnionType -> {
            val variants = mutableListOf<EtsType>()
            for (member in types) {
                val memberVariants = member.finiteLiteralVariants() ?: return null
                variants += memberVariants
            }
            variants.distinct()
        }

        is EtsStringLiteralType, is EtsNumberLiteralType, is EtsBooleanLiteralType,
        EtsNullType, EtsUndefinedType,
        -> listOf(this)

        EtsBooleanType -> listOf(EtsBooleanLiteralType(value = false), EtsBooleanLiteralType(value = true))
        else -> null
    }
}

private fun TsState.makeLiteralResult(type: EtsType): UExpr<*>? = when (type) {
    is EtsStringLiteralType -> mkInitializedStringConstant(type.value)
    is EtsNumberLiteralType -> ctx.mkFp64(type.value)
    is EtsBooleanLiteralType -> ctx.mkBool(type.value)
    EtsNullType -> ctx.mkTsNullValue()
    EtsUndefinedType -> ctx.mkUndefinedValue()
    else -> null
}

private fun TsState.makeFiniteLiteralUnion(scope: TsStepScope, variants: List<EtsType>): UExpr<*>? {
    var result = requireNotNull(makeLiteralResult(variants.first()))
    for (variant in variants.drop(1)) {
        val selected = makeSymbolicPrimitive(ctx.boolSort)
        val value = requireNotNull(makeLiteralResult(variant))
        result = ctx.iteWriteIntoFakeObject(
            scope = scope,
            condition = selected,
            trueBranchValue = value,
            falseBranchValue = result,
        ) ?: return null
    }

    return result
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
    boundedStringBackingRefs += string to maxStringLength
    string
}
