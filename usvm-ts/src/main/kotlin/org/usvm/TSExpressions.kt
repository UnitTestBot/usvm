package org.usvm

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFp64Value
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import io.ksmt.utils.cast

val KAst.tctx get() = ctx as TSContext

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

class TSWrappedValue(
    ctx: TSContext,
    val value: UExpr<out USort>
) : USymbol<USort>(ctx) {
    override val sort: USort
        get() = value.sort

    private val transformer = TSExprTransformer(value)

    fun coerce(
        other: UExpr<out USort>,
        action: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?
    ): UExpr<out USort> = when {
        other is UIntepretedValue -> {
            val otherTransformer = TSExprTransformer(other)
            transformer.intersectWithTypeCoercion(otherTransformer, action)
        }
        other is TSWrappedValue -> {
            transformer.intersectWithTypeCoercion(other.transformer, action)
        }
        else -> TODO()
    }

    fun coerceWithSort(
        other: UExpr<out USort>,
        action: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?,
        sort: USort,
    ): UExpr<out USort> {
        transformer.transform(sort)
        return coerce(other , action)
    }

    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        return value.cast()
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
       printer.append("rot ebal...")
    }

}

fun extractBool(expr: UExpr<out USort>): Boolean = when (expr) {
    expr.ctx.trueExpr -> true
    expr.ctx.falseExpr -> false
    else -> error("Expression $expr is not boolean")
}

fun extractInt(expr: UExpr<out USort>): Int = (expr as? KBitVec32Value)?.intValue ?: 0
fun extractDouble(expr: UExpr<out USort>): Double = (expr as? KFp64Value)?.value ?: 0.0
