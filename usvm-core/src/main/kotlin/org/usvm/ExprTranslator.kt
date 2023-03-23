package org.usvm

import org.ksmt.utils.mkConst

open class UExprTranslator<Field, Type> internal constructor(
    ctx: UContext,
) : UExprTransformer<Field, Type>(ctx) {
    private val observers = mutableListOf<UTranslationObserver>()

    internal fun attachObserver(observer: UTranslationObserver) {
        observers += observer
    }

    open fun <Sort : USort> translate(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    // TODO: why do we have this function in UExprTransformer?
    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> {
        error("You must override `transform` function in org.usvm.UExprTranslator for ${expr::class}")
    }

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> {
        val registerConst = expr.sort.mkConst("r${expr.idx}")
        observers.forEach { it.newRegisterReadingTranslated(expr.idx, registerConst) }
        return registerConst
    }

    // TODO: why do we have this function in UExprTransformer?
    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    // TODO: why do we have this function in UExprTransformer?
    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> {
        val const = expr.sort.mkConst("m${expr.method}_${expr.callIndex}")
        observers.forEach { it.newIndexedMethodReturnValueTranslated(expr.method, expr.callIndex, const) }
        return const
    }

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = expr.sort.mkConst("null")

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> {
        error("Unexpected UConcreteHeapRef $expr in UExprTranslator, that has to be impossible by construction!")
    }

    override fun transform(expr: UIsExpr<Type>): UBoolExpr {
        error("Unexpected UIsExpr $expr in UExprTranslator, that has to be impossible by construction!")
    }

    override fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.region, address)
        }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address, expr.index) { address, index ->
            translateRegionReading(expr.region, address to index)
        }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            translateRegionReading(expr.region, index)
        }

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.region, address)
        }

    private val regionToTranslator = mutableMapOf<URegionId<*, *>, URegionTranslator<*, *, *, *>>()
        .withDefault { regionId ->
            val regionTranslator = regionId.translator(this)
            observers.forEach { it.newRegionTranslator(regionId, regionTranslator) }
            regionTranslator
        }

    open fun <Key, Sort : USort> translateRegionReading(
        region: UMemoryRegion<URegionId<Key, Sort>, Key, Sort>,
        key: Key,
    ): UExpr<Sort> {
        @Suppress("UNCHECKED_CAST")
        val translator =
            regionToTranslator.getValue(region.regionId) as URegionTranslator<URegionId<Key, Sort>, Key, Sort, *>
        return translator.translateReading(region, key)
    }
}

internal typealias RegionTranslatorConstructor<T, U> = (UExprTranslator<*, *>) -> URegionTranslator<URegionId<T, U>, T, U, *>

// TODO: maybe split this function into functions of URegionID
internal val <Key, Sort : USort> URegionId<Key, Sort>.translator: RegionTranslatorConstructor<Key, Sort>
    get() = { translator ->
        val ctx = sort.uctx
        @Suppress("UNCHECKED_CAST")
        when (this) {
            is UInputArrayId<*, Sort> -> {
                val updateTranslator = U2DArrayUpdateTranslator(translator, ctx.addressSort, ctx.sizeSort, this)
                val updatesTranslator = UTreeUpdatesTranslator(updateTranslator)
                URegionTranslator(updateTranslator, updatesTranslator)
            }
            is UAllocatedArrayId<*, Sort> -> {
                val updateTranslator = U1DArrayUpdateTranslator(translator, ctx.sizeSort, this)
                val updatesTranslator = UTreeUpdatesTranslator(updateTranslator)
                URegionTranslator(updateTranslator, updatesTranslator)
            }
            is UInputArrayLengthId<*> -> {
                val updateTranslator = U1DArrayUpdateTranslator(translator, ctx.addressSort, this)
                val updatesTranslator = UFlatUpdatesTranslator(updateTranslator)
                URegionTranslator(updateTranslator, updatesTranslator)
            }
            is UInputFieldRegionId<*, Sort> -> {
                val updateTranslator = U1DArrayUpdateTranslator(translator, ctx.addressSort, this)
                val updatesTranslator = UFlatUpdatesTranslator(updateTranslator)
                URegionTranslator(updateTranslator, updatesTranslator)
            }
            else -> error("Unexpected regionId: $this")
        } as URegionTranslator<URegionId<Key, Sort>, Key, Sort, *>
    }

// TODO: looks odd, because we duplicate StackEvaluator::eval, MockEvaluator::eval with slightly changed signature...
internal interface UTranslationObserver {
    fun newRegionTranslator(
        regionId: URegionId<*, *>,
        translator: URegionTranslator<*, *, *, *>,
    )

    fun <Sort : USort> newRegisterReadingTranslated(idx: Int, translated: UExpr<Sort>)

    fun <Method, Sort : USort> newIndexedMethodReturnValueTranslated(
        method: Method,
        callIndex: Int,
        translated: UExpr<Sort>,
    )
}
