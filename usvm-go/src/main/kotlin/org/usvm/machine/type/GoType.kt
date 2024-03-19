package org.usvm.machine.type

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.UnknownTypeException
import org.usvm.machine.GoContext

typealias GoType = Long

enum class GoSort(val value: Byte) {
    UNKNOWN(0),
    VOID(1),
    BOOL(2),
    INT8(3),
    INT16(4),
    INT32(5),
    INT64(6),
    UINT8(7),
    UINT16(8),
    UINT32(9),
    UINT64(10),
    FLOAT32(11),
    FLOAT64(12),
    STRING(13),
    ARRAY(14),
    SLICE(15),
    MAP(16),
    STRUCT(17),
    INTERFACE(18),
    POINTER(19),
    TUPLE(20),
    FUNCTION(21);

    fun isSigned(): Boolean = when (this) {
        BOOL, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64 -> true
        UINT8, UINT16, UINT32, UINT64 -> false
        else -> false
    }

    companion object {
        private val values = entries.toTypedArray()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownTypeException()
    }
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