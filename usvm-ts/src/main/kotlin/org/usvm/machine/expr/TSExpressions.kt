package org.usvm.machine.expr

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
import org.usvm.machine.TSContext
import org.usvm.UExpr
import org.usvm.UIntepretedValue
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.machine.interpreter.TSStepScope

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

/**
 * [UExpr] wrapper that handles type coercion.
 *
 * @param value wrapped expression.
 */
class TSWrappedValue(
    ctx: TSContext,
    val value: UExpr<out USort>,
    private val scope: TSStepScope,
) : USymbol<USort>(ctx) {
    override val sort: USort
        get() = value.sort

    private val transformer = TSExprTransformer(value, scope)

    fun asSort(sort: USort): UExpr<out USort>? = transformer.transform(sort)

    private fun coerce(
        other: UExpr<out USort>,
        action: CoerceAction,
    ): UExpr<out USort> = when (other) {
        is UIntepretedValue -> {
            val otherTransformer = TSExprTransformer(other, scope)
            transformer.intersectWithTypeCoercion(otherTransformer, action)
        }

        is TSWrappedValue -> {
            transformer.intersectWithTypeCoercion(other.transformer, action)
        }

        else -> error("Unexpected $other in type coercion")
    }

    fun coerceWithSort(
        other: UExpr<out USort>,
        action: CoerceAction,
        sort: USort,
    ): UExpr<out USort> {
        transformer.transform(sort)
        return coerce(other, action)
    }

    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        return value.cast()
    }

    // TODO: draft
    override fun internEquals(other: Any): Boolean = structurallyEqual(other)

    // TODO: draft
    override fun internHashCode(): Int = hash()

    override fun print(printer: ExpressionPrinter) {
        printer.append("wrapped(")
        value.print(printer)
        printer.append(")")
    }
}

fun extractBool(expr: UExpr<out USort>): Boolean = when (expr) {
    expr.ctx.trueExpr -> true
    expr.ctx.falseExpr -> false
    else -> error("Expression $expr is not boolean")
}

fun extractInt(expr: UExpr<out USort>): Int = (expr as? KBitVec32Value)?.intValue ?: 0
fun extractDouble(expr: UExpr<out USort>): Double = (expr as? KFp64Value)?.value ?: 0.0
