package org.usvm

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsType

private typealias CoerceAction = (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>,
    private val scope: TSStepScope,
) {

    private val exprCache: MutableMap<USort, UExpr<out USort>?> = mutableMapOf(baseExpr.sort to baseExpr)
    private val ctx = baseExpr.tctx

    init {
        if (baseExpr.sort == ctx.addressSort) {
            TSTypeSystem.primitiveTypes.forEach { transform(ctx.typeToSort(it), modifyConstraints = false) }
        }
    }

    fun transform(sort: USort, modifyConstraints: Boolean = true): UExpr<out USort>? = with(ctx) {
        val (result, type) = when (sort) {
            fp64Sort -> asFp64() to EtsNumberType
            boolSort -> asBool() to EtsBooleanType
            addressSort -> asRef() to null
            else -> error("")
        }

        if (modifyConstraints && type != null) suggestType(type)

        return result
    }

    fun intersectWithTypeCoercion(
        other: TSExprTransformer,
        action: CoerceAction
    ): UExpr<out USort> {
        intersect(other)

        val innerCoercionExprs = this.generateAdditionalExprs() + other.generateAdditionalExprs()

        val exprs = exprCache.keys.mapNotNull { sort ->
            val lhv = transform(sort)
            val rhv = other.transform(sort)
            if (lhv != null && rhv != null) {
                action(lhv, rhv)
            } else null
        } + innerCoercionExprs

        return if (exprs.size > 1) {
            assert(exprs.all { it.sort == ctx.boolSort })
            UJoinedBoolExpr(ctx, exprs.cast())
        } else exprs.single()
    }

    private fun intersect(other: TSExprTransformer) {
        exprCache.keys.forEach { sort ->
            other.transform(sort)
        }
        other.exprCache.keys.forEach { sort ->
            transform(sort)
        }
    }

    private val addedExprCache: MutableSet<UExpr<out USort>> = mutableSetOf()

    private fun generateAdditionalExprs(): List<UExpr<out USort>> = with(ctx) {
        val newExpr = when (baseExpr.sort) {
            addressSort -> addedExprCache.putOrNull(mkEq(asFp64(), boolToFpSort(asBool())))
            else -> null
        }

        return newExpr?.let { listOf(it) } ?: emptyList()
    }

    fun asFp64(): UExpr<KFp64Sort> = exprCache.getOrPut(ctx.fp64Sort) {
        when (baseExpr.sort) {
            ctx.fp64Sort -> baseExpr
            ctx.boolSort -> ctx.boolToFpSort(baseExpr.cast())
            ctx.addressSort -> with(ctx) {
                TSRefTransformer(ctx, fp64Sort).apply(baseExpr.cast()).cast()
            }

            else -> ctx.mkFp64(0.0)
        }
    }.cast()


    fun asBool(): UExpr<KBoolSort> = exprCache.getOrPut(ctx.boolSort) {
        when (baseExpr.sort) {
            ctx.boolSort -> baseExpr
            ctx.fp64Sort -> with(ctx) { mkIte(mkFpEqualExpr(baseExpr.cast(), mkFp64(1.0)), mkTrue(), mkFalse()) }
            ctx.addressSort -> with(ctx) {
//                mkIte(
//                    condition = mkFpEqualExpr(asFp64(), mkFp64(1.0)),
//                    trueBranch = mkTrue(),
//                    falseBranch = mkFalse()
//                )
                TSRefTransformer(ctx, boolSort).apply(baseExpr.cast()).cast()
            }

            else -> ctx.mkFalse()
        }
    }.cast()

    fun asRef(): UExpr<UAddressSort>? = exprCache.getOrPut(ctx.addressSort) {
        when (baseExpr.sort) {
            ctx.addressSort -> baseExpr
            else -> ctx.mkTrackedSymbol(ctx.addressSort)
        }
    }.cast()

    private fun suggestType(type: EtsType) {
        if (baseExpr.sort !is UAddressSort) return
        scope.calcOnState { storeSuggestedType(baseExpr.cast(), type) }
    }
}

class TSRefTransformer(
    private val ctx: TSContext,
    private val sort: USort,
) {

    fun apply(expr: UExpr<UAddressSort>): UExpr<USort> = when (expr) {
        is URegisterReading -> transform(expr)
        else -> error("Not yet implemented: $expr")
    }

    fun transform(expr: URegisterReading<UAddressSort>): UExpr<USort> = ctx.mkRegisterReading(expr.idx, sort)
}
