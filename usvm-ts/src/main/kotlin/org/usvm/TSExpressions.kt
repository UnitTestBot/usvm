package org.usvm

import io.ksmt.KAst
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFp64Value
import io.ksmt.expr.KIteExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSortVisitor
import org.jacodb.ets.base.EtsNumberType

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
    scope: TSStepScope,
    private val value: UExpr<out USort>
) : UExpr<UAddressSort>(ctx) {
    override val sort: UAddressSort
        get() = uctx.addressSort

    private val exprs =

    private val expr: KIteExpr<UAddressSort> = with(ctx) {
        mkIte(
            condition = scope.calcOnState { memory.types.evalIsSubtype(this@TSWrappedValue, EtsNumberType) },
            trueBranch = mkNullRef(),
            falseBranch = mkNullRef()
        ) as KIteExpr
    }

    override fun accept(transformer: KTransformerBase): KExpr<UAddressSort> {
        return transformer.transform(expr)
    }

    override fun internEquals(other: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun internHashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun print(printer: ExpressionPrinter) {
        TODO("Not yet implemented")
    }

}

fun extractBool(expr: UExpr<out USort>): Boolean = when (expr) {
    expr.ctx.trueExpr -> true
    expr.ctx.falseExpr -> false
    else -> error("Expression $expr is not boolean")
}

fun extractInt(expr: UExpr<out USort>): Int = (expr as? KBitVec32Value)?.intValue ?: 0
fun extractDouble(expr: UExpr<out USort>): Double = (expr as? KFp64Value)?.value ?: 0.0
