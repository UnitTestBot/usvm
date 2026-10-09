package org.usvm.state

import io.ksmt.utils.asExpr
import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.readField
import org.usvm.isTrue
import org.usvm.memory.readGoArrayLength
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.sizeSort
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying

internal fun GoState.valueShape(value: UExpr<out USort>, type: GoType): UBoolExpr = with(ctx) {
    if (value.sort != addressSort) return trueExpr

    val reference = value.asExpr(addressSort)
    val isNil = mkHeapRefEq(reference, nullRef)
    if (isNil.isTrue) return if (type.isNilable() || type.underlying() == GoBasicTypes.STRING) trueExpr else falseExpr

    if (type is NamedType && type.underlying() !is InterfaceType) {
        val underlying = type.underlying()
        val payload = unbox(reference, typeToSort(underlying))
        val shape = mkAnd(isBoxed(reference), valueShape(payload, underlying))
        return if (underlying.isNilable()) mkOr(isNil, shape) else mkAnd(mkNot(isNil), shape)
    }

    return when (val underlyingType = type.underlying()) {
        is StructType -> {
            val fields = underlyingType.fields.orEmpty().mapIndexed { index, fieldType ->
                valueShape(memory.readField(reference, index, typeToSort(fieldType)), fieldType)
            }
            mkAnd(listOf(mkNot(isNil)) + fields)
        }
        is ArrayType -> {
            mkNot(isNil)
        }
        is PointerType -> {
            mkOr(isNil, isPointer(reference))
        }
        is InterfaceType -> {
            val boxed = mkAnd(isBoxed(reference), memory.types.evalIsSubtype(reference, underlyingType))
            mkOr(isNil, boxed)
        }
        is SliceType, GoBasicTypes.STRING -> {
            val length = memory.readGoArrayLength(reference, type, sizeSort)
            mkOr(isNil, mkSizeGeExpr(length, mkSizeExpr(0)))
        }
        else -> {
            trueExpr
        }
    }
}

internal fun GoType.isNilable(): Boolean = when (underlying()) {
    is PointerType, is SliceType, is MapType, is InterfaceType, is SignatureType -> true
    else -> false
}
