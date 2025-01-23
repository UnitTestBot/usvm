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
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.util.boolToFpSort
import org.usvm.util.fpToBoolForConditions

typealias CoerceAction = (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?

// TODO move to TsState, make a persistentMap
class TSExprTransformer(
    private val ctx: TSContext,
    // private val baseExpr: UExpr<out USort>,
    // private val scope: TSStepScope,
) {
    private val cache: MutableMap<UExpr<out USort>, MutableMap<USort, UExpr<out USort>?>> = hashMapOf()

    // init {
    //     if (baseExpr.sort == ctx.addressSort) {
    //         TSTypeSystem.primitiveTypes.forEach { transform(ctx.typeToSort(it)) }
    //     }
    // }

    // TODO generics
    fun transform(expr: UExpr<out USort>, sort: USort): UExpr<out USort>? = with(ctx) {
        return when (sort) {
            fp64Sort -> asFp64(expr)
            boolSort -> asBool(expr)
            addressSort -> asRef(expr)
            else -> error("Unknown sort: $sort")
        }
    }

    fun intersectWithTypeCoercion(
        fst: UExpr<out USort>,
        snd: UExpr<out USort>,
        action: CoerceAction,
        scope: TSStepScope,
    ): UExpr<out USort> {
        intersect(fst, snd)

        val fstSorts = cache.getValue(fst).keys
        val exprs = fstSorts.mapNotNull { sort ->
            val lhv = transform(fst, sort)
            val rhv = transform(snd, sort)

            if (lhv != null && rhv != null) {
                action(lhv, rhv)
            } else {
                null
            }
        }
        // foo(a, b) {
        //     return a + b
        // }

        // WrappedExpr(...)

        // Wrapped(fpAdd, bvAdd, intAdd)
        // fpAdd <-> a is fp && b is fp
        //
        //

        ctx.generateAdditionalExprs(fst, exprs, scope)

        return if (exprs.size > 1) {
            if (!exprs.all { it.sort == ctx.boolSort }) error("All expressions must be of bool sort.")
            UJoinedBoolExpr(ctx, exprs.cast())
        } else {
            exprs.single()
        }
    }

    private fun intersect(fst: UExpr<out USort>, snd: UExpr<out USort>) {
        val fstCache = cache.getOrPut(fst) { mutableMapOf(fst.sort to fst) }
        val sndCache = cache.getOrPut(snd) { mutableMapOf(snd.sort to snd) }

        val fstSorts = fstCache.keys
        fstSorts.forEach { sort ->
            sndCache.getOrPut(sort) { transform(snd, sort) }
        }

        val sndSorts = sndCache.keys
        sndSorts.forEach { sort ->
            fstCache.getOrPut(sort) { transform(fst, sort) }
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
    private fun TSContext.generateAdditionalExprs(
        expr: UExpr<out USort>,
        rawExprs: List<UExpr<out USort>>,
        scope: TSStepScope,
    ) {
        // a + b // ??? ???
        // regionReading + regionReading

        if (!rawExprs.all { it.sort == boolSort }) return // TODO why???
        when (expr.sort) {
            // Saves link in constraints between asFp64(ref) and asBool(ref) since they were instantiated separately.
            // No need to add link between ref and fp64/bool representations since refs can only be compared with refs.
            // (primitives can't be cast to ref in TypeScript type coercion)
            addressSort -> {
                // TODO check for an error
                val fpToBoolLink = mkEq(fpToBoolForConditions(asFp64(expr)), asBool(expr))
                val boolToRefLink = mkEq(asBool(expr), (expr as UExpr<UAddressSort>).neq(mkNullRef()))
                // TODO do not add path constraints
                if (addedExprCache.add(fpToBoolLink)) scope.calcOnState { pathConstraints.plusAssign(fpToBoolLink) } // TODO check if we can do it
                if (addedExprCache.add(boolToRefLink)) scope.calcOnState { pathConstraints.plusAssign(boolToRefLink) }
            }
        }
    }

    private fun asFp64(expr: UExpr<out USort>): UExpr<KFp64Sort> =
        cache.getOrPut(expr) { mutableMapOf() }
            .getOrPut(ctx.fp64Sort) {
                when (expr.sort) {
                    ctx.fp64Sort -> expr
                    ctx.boolSort -> ctx.boolToFpSort(expr.cast())
                    ctx.addressSort -> with(ctx) {
                        TSRefTransformer(ctx, fp64Sort).apply(expr.cast()).cast()
                    }

                    else -> error("Unsupported sort: ${expr.sort}")
                }
            }.cast()

    private fun asBool(expr: UExpr<out USort>): UExpr<KBoolSort> =
        cache.getOrPut(expr) { mutableMapOf() }
            .getOrPut(ctx.boolSort) {
                when (expr.sort) {
                    ctx.boolSort -> expr
                    // TODO check for an error
                    ctx.fp64Sort -> ctx.fpToBoolForConditions(expr.cast())
                    ctx.addressSort -> with(ctx) {
                        TSRefTransformer(ctx, boolSort).apply(expr.cast()).cast()
                    }

                    else -> error("Unsupported sort: ${expr.sort}")
                }
            }.cast()

    private fun asRef(expr: UExpr<out USort>): UExpr<UAddressSort>? =
        cache.getOrPut(expr) { mutableMapOf() }
            .getOrPut(ctx.addressSort) {
                when (expr.sort) {
                    ctx.addressSort -> expr
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
