package org.usvm

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>
) {

    private val exprCache: MutableMap<USort, UExpr<out USort>> = mutableMapOf(baseExpr.sort to baseExpr)

    private val ctx = baseExpr.tctx

//    @Suppress("UNCHECKED_CAST")
//    fun transform(expr: UExpr<out USort>): Pair<UExpr<out USort>, EtsType> = with(ctx) {
//        when {
//            expr is TSWrappedValue -> transform(expr.value.sort) to expr.type
//            expr is UIntepretedValue -> transform(expr.sort) to EtsAnyType
//            expr.sort == addressSort -> transformRef(expr as UExpr<UAddressSort>)
//            else -> error("Should not be called")
//        }
//    }

    fun transform(sort: USort): UExpr<out USort> = with(ctx) {
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
        action: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>
    ): UExpr<out USort> {
        intersect(other)
        val exprs = exprCache.keys.map { sort -> action(transform(sort), other.transform(sort)) }
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

//    private fun transformRef(expr: UExpr<UAddressSort>): Pair<UExpr<out USort>, EtsType> = TODO()

    fun asFp64(): UExpr<KFp64Sort> = exprCache.getOrPut(ctx.fp64Sort) {
        when (baseExpr.sort) {
            ctx.fp64Sort -> baseExpr
            ctx.boolSort -> with(ctx) { mkIte(baseExpr.cast(), mkFp64(1.0), mkFp64(0.0)) }
            else -> ctx.mkFp64(0.0)
        }
    }.cast()

    fun asBool(): UExpr<KBoolSort> = exprCache.getOrPut(ctx.boolSort) {
        when (baseExpr.sort) {
            ctx.boolSort -> baseExpr
            ctx.fp64Sort -> with(ctx) { mkIte(mkFpEqualExpr(baseExpr.cast(), mkFp64(1.0)), mkTrue(), mkFalse()) }
            else -> ctx.mkFalse()
        }
    }.cast()

    fun asRef(): UExpr<UAddressSort> = exprCache.getOrPut(ctx.addressSort) {
        when (baseExpr.sort) {
            ctx.addressSort -> baseExpr
            else -> error("should not be called")
        }
    }.cast()
}
