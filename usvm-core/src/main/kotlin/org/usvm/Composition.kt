package org.usvm

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

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>,
    ): UExpr<Sort> = with(expr) { stackEvaluator.eval(idx, sort) }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>,
    ): UExpr<Sort> = mockEvaluator.eval(expr)

    override fun transform(expr: UIsExpr<Type>): UBoolExpr = with(expr) {
        val composedAddress = compose(ref)
        typeEvaluator.evalIs(composedAddress, type)
    }

    fun <RegionId : URegionId<Key, Sort>, Key, Sort : USort> transformHeapReading(
        expr: UHeapReading<RegionId, Key, Sort>,
        key: Key,
    ): UExpr<Sort> = with(expr) {
        // if region.defaultValue != null, we don't need to apply updates to the heapEvaluator. expr.region
        // already contains ALL region writes, and underlying value (defaultValue) is defined, so we have all the
        // required information, and it cannot be refined.
        // Otherwise, the underlying value may be reified accordingly to the heapEvaluator

        val instantiatorFactory = object : UInstantiatorFactory {
            override fun <RegionId : URegionId<Key, Sort>, Key, Sort : USort> build(): UInstantiator<RegionId, Key, Sort> =
                { key, memoryRegion ->
                    // Create a copy of this heap to avoid its modification
                    val heapToApplyUpdates = heapEvaluator.toMutableHeap()
                    memoryRegion.applyTo(heapToApplyUpdates)
                    memoryRegion.regionId.read(heapToApplyUpdates, key)
                }
        }

        @Suppress("UNCHECKED_CAST")
        val mappedRegion = region.map(this@UComposer, instantiatorFactory)
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
