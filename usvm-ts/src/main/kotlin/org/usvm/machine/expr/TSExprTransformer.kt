package org.usvm.machine.expr

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.UJoinedBoolExpr
import org.usvm.URegisterReading
import org.usvm.USort
import org.usvm.machine.TSContext
import org.usvm.machine.TSTypeSystem
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.util.boolToFpSort
import org.usvm.util.fpToBoolSort

typealias CoerceAction = (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?

class TSExprTransformer(
    private val baseExpr: UExpr<out USort>,
    private val scope: TSStepScope,
) {

    private val exprCache: MutableMap<USort, UExpr<out USort>?> = hashMapOf(baseExpr.sort to baseExpr)
    private val ctx = baseExpr.tctx

    init {
        if (baseExpr.sort == ctx.addressSort) {
            TSTypeSystem.primitiveTypes.forEach { transform(ctx.typeToSort(it)) }
        }
    }

    fun transform(sort: USort): UExpr<out USort>? = with(ctx) {
        return when (sort) {
            fp64Sort -> asFp64()
            boolSort -> asBool()
            addressSort -> asRef()
            else -> error("Unknown sort: $sort")
        }
    }

    fun intersectWithTypeCoercion(
        other: TSExprTransformer,
        action: CoerceAction,
    ): UExpr<out USort> {
        intersect(other)

        val exprs = exprCache.keys.mapNotNull { sort ->
            val lhv = transform(sort)
            val rhv = other.transform(sort)
            if (lhv != null && rhv != null) {
                action(lhv, rhv)
            } else {
                null
            }
        }

        ctx.generateAdditionalExprs(exprs)

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
    @Suppress("UNCHECKED_CAST")
    private fun TSContext.generateAdditionalExprs(rawExprs: List<UExpr<out USort>>) {
        if (!rawExprs.all { it.sort == boolSort }) return
        when (baseExpr.sort) {
            // Saves link in constraints between asFp64(ref) and asBool(ref) since they were instantiated separately.
            // No need to add link between ref and fp64/bool representations since refs can only be compared with refs.
            // (primitives can't be cast to ref in TypeScript type coercion)
            addressSort -> {
                val fpToBoolLink = mkEq(fpToBoolSort(asFp64()), asBool())
                val boolToRefLink =  mkEq(asBool(), (baseExpr as UExpr<UAddressSort>).neq(mkNullRef()))
                if (addedExprCache.add(fpToBoolLink)) scope.calcOnState { pathConstraints.plusAssign(fpToBoolLink) }
                if (addedExprCache.add(boolToRefLink)) scope.calcOnState { pathConstraints.plusAssign(boolToRefLink) }
            }
        }
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
