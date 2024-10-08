package org.usvm

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsType

typealias CoerceAction = (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>,
    private val scope: TSStepScope,
) {

    private val exprCache: MutableMap<USort, UExpr<out USort>?> = hashMapOf(baseExpr.sort to baseExpr)
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
            // No primitive type can be suggested from ref -- null is returned.
            addressSort -> asRef() to null
            else -> error("Unknown sort: $sort")
        }

        if (modifyConstraints && type != null) suggestType(type)

        return result
    }

    fun intersectWithTypeCoercion(
        other: TSExprTransformer,
        action: CoerceAction
    ): UExpr<out USort> {
        intersect(other)

        val rawExprs = exprCache.keys.mapNotNull { sort ->
            val lhv = transform(sort)
            val rhv = other.transform(sort)
            if (lhv != null && rhv != null) {
                action(lhv, rhv)
            } else {
                null
            }
        }

        val innerCoercionExprs = this.generateAdditionalExprs(rawExprs) + other.generateAdditionalExprs(rawExprs)

        val exprs = rawExprs + innerCoercionExprs

        return if (exprs.size > 1) {
            if (!exprs.all { it.sort == ctx.boolSort }) error("All expressions must be of bool sort.")
            UJoinedBoolExpr(ctx, exprs.cast())
        } else {
            exprs.single()
        }
    }

    private fun intersect(other: TSExprTransformer) {
        exprCache.keys.forEach { sort ->
            other.transform(sort)
        }
        other.exprCache.keys.forEach { sort ->
            transform(sort)
        }
    }

    private val addedExprCache: MutableSet<UExpr<out USort>> = hashSetOf()

    /**
     * Generates and caches additional constraints for coercion expression list.
     *
     * For now used to save link between fp and bool sorts of [baseExpr].
     *
     * @return List of additional [UExpr].
     */
    private fun generateAdditionalExprs(rawExprs: List<UExpr<out USort>>): List<UExpr<out USort>> = with(ctx) {
        if (!rawExprs.all { it.sort == boolSort }) return emptyList()
        val newExpr = when (baseExpr.sort) {
            // Saves link in constraints between asFp64(ref) and asBool(ref) since they were instantiated separately.
            addressSort -> addedExprCache.putOrNull(mkEq(fpToBoolSort(asFp64()), asBool()))
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

            else -> error("Unsupported sort: ${baseExpr.sort}")
        }
    }.cast()


    fun asBool(): UExpr<KBoolSort> = exprCache.getOrPut(ctx.boolSort) {
        when (baseExpr.sort) {
            ctx.boolSort -> baseExpr
            ctx.fp64Sort -> ctx.fpToBoolSort(baseExpr.cast())
            ctx.addressSort -> with(ctx) {
                TSRefTransformer(ctx, boolSort).apply(baseExpr.cast()).cast()
            }

            else -> error("Unsupported sort: ${baseExpr.sort}")
        }
    }.cast()

    fun asRef(): UExpr<UAddressSort>? = exprCache.getOrPut(ctx.addressSort) {
        when (baseExpr.sort) {
            ctx.addressSort -> baseExpr
            // ctx.mkTrackedSymbol(ctx.addressSort) is possible here, but
            // no constraint-wise benefits of using it instead of null were currently found.
            else -> null
        }
    }.cast()

    private fun suggestType(type: EtsType) {
        if (baseExpr.sort !is UAddressSort) return
        scope.calcOnState { storeSuggestedType(baseExpr.cast(), type) }
    }
}

/**
 * Transforms [UExpr] with [UAddressSort]:
 *
 * UExpr(address sort) -> UExpr'([sort]).
 *
 * TODO: Implement other expressions with address sort.
 */
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
