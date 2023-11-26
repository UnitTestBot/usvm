package org.usvm.type

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.SliceType
import org.usvm.GoContext
import org.usvm.UExpr
import org.usvm.USort

object GoTypes {
    const val BOOL = "bool"
    const val INT = "int"
    const val INT8 = "int8"
    const val INT16 = "int16"
    const val INT32 = "int32"
    const val INT64 = "int64"
    const val UINT = "uint"
    const val UINT8 = "uint8"
    const val UINT16 = "uint16"
    const val UINT32 = "uint32"
    const val UINT64 = "uint64"
    const val FLOAT32 = "float32"
    const val FLOAT64 = "float64"
    const val STRING = "string"
    const val BYTE = "byte"
    const val RUNE = "rune"

    const val UINTPTR = "uintptr"
    const val UNTYPED_BOOL = "untyped bool"
    const val UNTYPED_INT = "untyped int"
    const val UNTYPED_RUNE = "untyped rune"
    const val UNTYPED_FLOAT = "untyped float"
    const val UNTYPED_STRING = "untyped string"

    const val UNSAFE_POINTER = "unsafe.Pointer"
}

object GoBasicTypes {
    val BOOL = BasicType(GoTypes.BOOL)
    val INT = BasicType(GoTypes.INT)
    val INT8 = BasicType(GoTypes.INT8)
    val INT16 = BasicType(GoTypes.INT16)
    val INT32 = BasicType(GoTypes.INT32)
    val INT64 = BasicType(GoTypes.INT64)
    val UINT = BasicType(GoTypes.UINT)
    val UINT8 = BasicType(GoTypes.UINT8)
    val UINT16 = BasicType(GoTypes.UINT16)
    val UINT32 = BasicType(GoTypes.UINT32)
    val UINT64 = BasicType(GoTypes.UINT64)
    val UINTPTR = BasicType(GoTypes.UINTPTR)
    val FLOAT32 = BasicType(GoTypes.FLOAT32)
    val FLOAT64 = BasicType(GoTypes.FLOAT64)
    val STRING = SliceType(UINT8)
    val RUNE = BasicType(GoTypes.RUNE)
    val UNSAFE_POINTER = BasicType(GoTypes.UNSAFE_POINTER)
}

fun GoType.underlying(): GoType = when (this) {
    is NamedType -> this.underlyingType.underlying()
    else -> this
}

class GoVoidSort(ctx: GoContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("void sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("should not be called")
}

class GoVoidValue(ctx: GoContext) : UExpr<GoVoidSort>(ctx) {
    override val sort: GoVoidSort get() = goCtx.voidSort

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun accept(transformer: KTransformerBase): GoVoidValue = this

    override fun print(printer: ExpressionPrinter) {
        printer.append("void")
    }
}

val KAst.goCtx get() = ctx as GoContext