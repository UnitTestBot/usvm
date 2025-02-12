package org.usvm.machine.expr

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TSContext

val KAst.tctx: TSContext
    get() = ctx as TSContext

class TSUndefinedSort(ctx: TSContext) : USort(ctx) {
    override fun print(builder: StringBuilder) {
        builder.append("undefined sort")
    }

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")
}

class TSUndefinedValue(ctx: TSContext) : UExpr<TSUndefinedSort>(ctx) {
    override val sort: TSUndefinedSort
        get() = tctx.undefinedSort

    override fun accept(transformer: KTransformerBase): TSUndefinedValue = this

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("undefined")
    }
}

/**
 * Represents a sort for objects with unknown type.
 */
class TSUnresolvedSort(ctx: TSContext) : USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")

    override fun print(builder: StringBuilder) {
        builder.append("Unresolved sort")
    }
}

class TSStringSort(ctx: TSContext) : USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T {
        return visitor.visit(this)
    }

    override fun print(builder: StringBuilder) {
        builder.append("stringSort")
    }
}

fun UExpr<out USort>.extractBool(): Boolean = when (this) {
    ctx.trueExpr -> true
    ctx.falseExpr -> false
    else -> error("Cannot extract boolean from $this")
}

fun extractInt(expr: UExpr<out USort>): Int =
    (expr as? KBitVec32Value)?.intValue ?: error("Cannot extract int from $expr")

fun extractDouble(expr: UExpr<out USort>): Double =
    (expr as? KFp64Value)?.value ?: error("Cannot extract double from $expr")
