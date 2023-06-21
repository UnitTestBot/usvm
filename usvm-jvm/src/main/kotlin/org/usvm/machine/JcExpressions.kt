package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort

class JcVoidValue(ctx: JcContext) : UExpr<UAddressSort>(ctx) {
    override val sort: UAddressSort get() = jctx.voidSort

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> = this

    override fun print(printer: ExpressionPrinter) {
        printer.append("void")
    }
}

// region utility functions

val KAst.jctx get() = ctx as JcContext

fun extractBool(expr: UExpr<out USort>): Boolean? = when (expr) {
    expr.ctx.trueExpr -> true
    expr.ctx.falseExpr -> false
    else -> null
}

fun extractByte(expr: UExpr<out USort>): Byte? = (expr as? KBitVec8Value)?.byteValue
fun extractShort(expr: UExpr<out USort>): Short? = (expr as? KBitVec16Value)?.shortValue
fun extractChar(expr: UExpr<out USort>): Char? = (expr as? KBitVec16Value)?.shortValue?.toInt()?.toChar()
fun extractInt(expr: UExpr<out USort>): Int? = (expr as? KBitVec32Value)?.intValue
fun extractLong(expr: UExpr<out USort>): Long? = (expr as? KBitVec64Value)?.longValue
fun extractFloat(expr: UExpr<out USort>): Float? = (expr as? KFp32Value)?.value
fun extractDouble(expr: UExpr<out USort>): Double? = (expr as? KFp64Value)?.value

// endregion