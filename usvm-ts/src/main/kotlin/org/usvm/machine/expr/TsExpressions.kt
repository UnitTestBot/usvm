package org.usvm.machine.expr

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext

val KAst.tctx: TsContext
    get() = ctx as TsContext

class TsUndefinedSort(ctx: TsContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("undefined sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")
}

class TsUndefinedValue(ctx: TsContext) : UExpr<TsUndefinedSort>(ctx) {
    override val sort: TsUndefinedSort
        get() = tctx.undefinedSort

    override fun accept(transformer: KTransformerBase): TsUndefinedValue = this

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("undefined")
    }
}

/**
 * Represents a sort for objects with unknown type.
 */
class TsUnresolvedSort(ctx: TsContext) : USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")

    override fun print(builder: StringBuilder) {
        builder.append("Unresolved sort")
    }
}

fun UExpr<out USort>.extractBool(): Boolean = when (this) {
    ctx.trueExpr -> true
    ctx.falseExpr -> false
    else -> error("Cannot extract boolean from $this")
}

fun extractInt(expr: UExpr<out USort>): Int =
    (expr as? KBitVec32Value)?.intValue ?: error("Cannot extract int from $expr")

fun UExpr<out USort>.extractDouble(): Double {
    if (this@extractDouble is KFp64Value) {
        return value
    }
    error("Cannot extract double from $this")
}
