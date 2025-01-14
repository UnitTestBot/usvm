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
import org.usvm.UExpr
import org.usvm.UIntepretedValue
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.machine.TSContext
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

// TODO rename it
class TSUnresolvedSort(ctx: TSContext) : USort(ctx) {
    val possibleSorts: Set<USort> = mutableSetOf() // TODO ??????

    override fun <T> accept(visitor: KSortVisitor<T>): T = error("Should not be called")

    override fun print(builder: StringBuilder) {
        builder.append("Unresolved sort")
    }
}

/**
 * [UExpr] wrapper that handles type coercion.
 *
 * @param value wrapped expression.
 */
// TODO check that can occur only in assignStmt
class TSWrappedValue<T : USort>(
    ctx: TSContext,
    val value: UExpr<T>,
) : USymbol<USort>(ctx) {
    override val sort: USort
        get() = value.sort

    fun asSort(
        sort: USort,
        scope: TSStepScope,
    ): UExpr<out USort>? = scope.calcOnState { exprTransformer.transform(value, sort) }

    private fun coerce(
        other: UExpr<out USort>,
        action: CoerceAction,
        scope: TSStepScope,
    ): UExpr<out USort> = with(scope) {
        when (other) {
            is UIntepretedValue -> {
                calcOnState {
                    exprTransformer.intersectWithTypeCoercion(value, other, action, scope)
                }
            }

            is TSWrappedValue<*> -> {
                calcOnState {
                    exprTransformer.intersectWithTypeCoercion(value, other.value, action, scope)
                }
            }

            else -> error("Unexpected $other in type coercion")
        }
    }

    fun coerceWithSort(
        other: UExpr<out USort>,
        action: CoerceAction,
        desiredSort: USort?,
        scope: TSStepScope,
    ): UExpr<out USort> = with(scope) {
        desiredSort?.let {
            doWithState {
                exprTransformer.transform(value, it)
            }
        }

        return coerce(other, action, scope)
    }

    override fun accept(transformer: KTransformerBase): KExpr<USort> {
        return value.cast()
    }

    // TODO: draft
    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { value }

    // TODO: draft
    override fun internHashCode(): Int = hash(value)

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
