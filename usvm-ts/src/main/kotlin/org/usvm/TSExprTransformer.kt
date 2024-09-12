package org.usvm

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType

private typealias CoerceAction = (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>,
    private val scope: TSStepScope,
) {

    private val exprCache: MutableMap<USort, UExpr<out USort>?> = mutableMapOf(baseExpr.sort to baseExpr)
    private val ctx = baseExpr.tctx

    fun transform(sort: USort): UExpr<out USort>? = with(ctx) {
        when (sort) {
            fp64Sort -> asFp64()
            boolSort -> asBool()
            addressSort -> asRef()
            else -> error("")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun intersectWithTypeCoercion(
        other: TSExprTransformer,
        action: CoerceAction
    ): UExpr<out USort> {
        intersect(other)

        val exprs = exprCache.keys.mapNotNull { sort ->
            val lhv = transform(sort)
            val rhv = other.transform(sort)
            if (lhv != null && rhv != null) {
                action(lhv, rhv)
            } else null
        }

        return if (exprs.size > 1) {
            assert(exprs.all { it.sort == ctx.boolSort })
            ctx.mkAnd(exprs as List<UBoolExpr>)
        } else exprs.single()
    }

    fun intersect(other: TSExprTransformer) {
        exprCache.keys.forEach { sort ->
            other.transform(sort)
        }
        other.exprCache.keys.forEach { sort ->
            transform(sort)
        }
    }

    fun asFp64(
        modifyConstraints: Boolean = true
    ): UExpr<KFp64Sort> = exprCache.getOrPut(ctx.fp64Sort) {
        when (baseExpr.sort) {
            ctx.fp64Sort -> baseExpr
            ctx.boolSort -> ctx.boolToFpSort(baseExpr.cast())
            ctx.addressSort -> with(ctx) {
                val value: URegisterReading<KFp64Sort> =
                    TSRefTransformer(ctx, fp64Sort).apply(baseExpr.cast()).cast()
                if (modifyConstraints) {
                    scope.calcOnState {
                        val ref: UExpr<UAddressSort> = this.models.first().eval(baseExpr).cast()
                        storeSuggestedType(ref, EtsNumberType)
                    }
                }
                value
            }

            else -> ctx.mkFp64(0.0)
        }
    }.cast()


    fun asBool(
        modifyConstraints: Boolean = true
    ): UExpr<KBoolSort> = exprCache.getOrPut(ctx.boolSort) {
        when (baseExpr.sort) {
            ctx.boolSort -> baseExpr
            ctx.fp64Sort -> with(ctx) { mkIte(mkFpEqualExpr(baseExpr.cast(), mkFp64(1.0)), mkTrue(), mkFalse()) }
            ctx.addressSort -> with(ctx) {
                if (modifyConstraints) {
                    scope.calcOnState {
                        val ref: UExpr<UAddressSort> = this.models.first().eval(baseExpr).cast()
                        storeSuggestedType(ref, EtsBooleanType)
                    }
                }
                mkIte(
                    condition = mkFpEqualExpr(asFp64(modifyConstraints = false), mkFp64(1.0)),
                    trueBranch = mkTrue(),
                    falseBranch = mkFalse()
                )
            }

            else -> ctx.mkFalse()
        }
    }.cast()

    fun asRef(): UExpr<UAddressSort>? = exprCache.getOrPut(ctx.addressSort) {
        when (baseExpr.sort) {
            ctx.addressSort -> baseExpr
            else -> null
        }
    }.cast()
}

class TSRefTransformer(
    private val ctx: TSContext,
    private val sort: USort,
) {

    fun apply(expr: UExpr<UAddressSort>): UExpr<USort> = when (expr) {
        is URegisterReading -> transform(expr)
        else -> error("Not yet implemented: $expr")
    }

    private fun transform(expr: URegisterReading<UAddressSort>): UExpr<USort> = ctx.mkRegisterReading(expr.idx, sort)

}