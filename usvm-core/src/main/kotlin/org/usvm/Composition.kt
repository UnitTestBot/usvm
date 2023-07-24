package org.usvm

import io.ksmt.expr.KExpr
import io.ksmt.expr.KIteExpr
import io.ksmt.sort.KSort
import org.usvm.constraints.UTypeEvaluator
import org.usvm.memory.UReadOnlySymbolicHeap
import org.usvm.memory.URegionId
import org.usvm.memory.URegistersStackEvaluator

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Field, Type>(
    ctx: UContext,
    internal val stackEvaluator: URegistersStackEvaluator,
    internal val heapEvaluator: UReadOnlySymbolicHeap<Field, Type>,
    internal val typeEvaluator: UTypeEvaluator<Type>,
    internal val mockEvaluator: UMockEvaluator,
) : UExprTransformer<Field, Type>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <T : KSort> transform(expr: KIteExpr<T>): KExpr<T> =
        transformExprAfterTransformed(expr, expr.condition) { condition ->
            when {
                condition.isTrue -> apply(expr.trueBranch)
                condition.isFalse -> apply(expr.falseBranch)
                else -> super.transform(expr)
            }
        }

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>,
    ): UExpr<Sort> = with(expr) { stackEvaluator.readRegister(idx, sort) }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>,
    ): UExpr<Sort> = mockEvaluator.eval(expr)

    override fun transform(expr: UIsSubtypeExpr<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            typeEvaluator.evalIsSubtype(ref, expr.supertype)
        }

    override fun transform(expr: UIsSupertypeExpr<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            typeEvaluator.evalIsSupertype(ref, expr.subtype)
        }

    fun <RegionId : URegionId<Key, Sort, RegionId>, Key, Sort : USort> transformHeapReading(
        expr: UHeapReading<RegionId, Key, Sort>,
        key: Key,
    ): UExpr<Sort> = with(expr) {
        val mappedRegion = region.map(this@UComposer)
        val mappedKey = mappedRegion.regionId.keyMapper(this@UComposer)(key)
        mappedRegion.read(mappedKey)
    }

    override fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr =
        transformHeapReading(expr, expr.address)

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort> =
        transformHeapReading(expr, expr.address to expr.index)

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort> =
        transformHeapReading(expr, expr.index)

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        transformHeapReading(expr, expr.address)

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = heapEvaluator.nullRef()
}
