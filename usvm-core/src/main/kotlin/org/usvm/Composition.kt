package org.usvm

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Field, Type>(
    ctx: UContext,
    internal val stackEvaluator: URegistersStackEvaluator,
    internal val heapEvaluator: UReadOnlySymbolicHeap<Field, Type>,
    internal val typeEvaluator: UTypeEvaluator<Type>,
    internal val mockEvaluator: UMockEvaluator
) : UExprTransformer<Field, Type>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>
    ): UExpr<Sort> = with(expr) { stackEvaluator.eval(idx, sort) }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>
    ): UExpr<Sort> = mockEvaluator.eval(expr)

    override fun transform(expr: UIsExpr<Type>): UBoolExpr = with(expr) {
        val composedAddress = compose(ref)
        typeEvaluator.evalIs(composedAddress, type)
    }

    fun <RegionId : URegionId<Key, Sort>, Key, Sort : USort> transformHeapReading(
        expr: UHeapReading<RegionId, Key, Sort>,
        key: Key
    ): UExpr<Sort> = with(expr) {
        val instantiator = { key: Key, memoryRegion: UMemoryRegion<RegionId, Key, Sort> ->
            // Create a copy of this heap to avoid its modification
            val heapToApplyUpdates = heapEvaluator.toMutableHeap()
            memoryRegion.applyTo(heapToApplyUpdates)
            region.regionId.read(heapToApplyUpdates, key)
        }

        val mappedRegion = region.map(this@UComposer, instantiator)
        val mappedKey = region.regionId.keyMapper(this@UComposer)(key)
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

    override fun transform(expr: UNullRef): UNullRef = expr
}
