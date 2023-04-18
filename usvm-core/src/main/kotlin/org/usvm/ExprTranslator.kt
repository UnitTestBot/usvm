package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort
import org.ksmt.sort.KArraySortBase
import org.ksmt.sort.KBoolSort
import org.ksmt.utils.cast
import org.ksmt.utils.mkConst

/**
 * Translates custom [UExpr] to a [KExpr]. Region readings are translated via [URegionTranslator]s.
 * Base version cache everything, but doesn't track translated expressions.
 *
 * To show semantics of the translator, we use [KExpr] as return values, though [UExpr] is a typealias for it.
 */
open class UExprTranslator<Field, Type>(
    override val ctx: UContext,
) : UExprTransformer<Field, Type>(ctx), URegionIdVisitor<URegionTranslator<*, *, *, *>> {

    open fun <Sort : USort> translate(expr: UExpr<Sort>): KExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): KExpr<Sort> {
        // TODO: we must ensure all ids are different
        val registerConst = expr.sort.mkConst("r${expr.idx}")
        return registerConst
    }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): KExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): KExpr<Sort> {
        // TODO: we must ensure all ids are different
        val const = expr.sort.mkConst("m${expr.method}_${expr.callIndex}")
        return const
    }

    override fun transform(expr: UNullRef): KExpr<UAddressSort> {
        val const = ctx.mkUninterpretedSortValue(ctx.addressSort, 0)
        return const
    }

    override fun transform(expr: UConcreteHeapRef): KExpr<UAddressSort> =
        error("Unexpected UConcreteHeapRef $expr in UExprTranslator, that has to be impossible by construction!")

    override fun transform(expr: UIsExpr<Type>): KExpr<KBoolSort> =
        error("Unexpected UIsExpr $expr in UExprTranslator, that has to be impossible by construction!")

    override fun transform(expr: UInputArrayLengthReading<Type>): KExpr<USizeSort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.region, address)
        }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address, expr.index) { address, index ->
            translateRegionReading(expr.region, address to index)
        }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.index) { index ->
            translateRegionReading(expr.region, index)
        }

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): KExpr<Sort> =
        transformExprAfterTransformed(expr, expr.address) { address ->
            translateRegionReading(expr.region, address)
        }

    open fun <Key, Sort : USort> translateRegionReading(
        region: USymbolicMemoryRegion<URegionId<Key, Sort, *>, Key, Sort>,
        key: Key,
    ): KExpr<Sort> {
        val regionTranslator = buildTranslator(region.regionId)
        return regionTranslator.translateReading(region, key)
    }

    // these functions implement URegionIdTranslatorFactory

    override fun <Field, Sort : USort> visit(
        regionId: UInputFieldId<Field, Sort>,
    ): URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort, *> {
        val initialValue = regionIdInitialValueProvider.visit(regionId)
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <ArrayType, Sort : USort> visit(
        regionId: UAllocatedArrayId<ArrayType, Sort>,
    ): URegionTranslator<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort, *> {
        val initialValue = regionIdInitialValueProvider.visit(regionId)
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <ArrayType, Sort : USort> visit(
        regionId: UInputArrayId<ArrayType, Sort>,
    ): URegionTranslator<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort, *> {
        val initialValue = regionIdInitialValueProvider.visit(regionId)
        val updateTranslator = U2DArrayUpdateVisitor(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    override fun <ArrayType> visit(
        regionId: UInputArrayLengthId<ArrayType>,
    ): URegionTranslator<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort, *> {
        val initialValue = regionIdInitialValueProvider.visit(regionId)
        val updateTranslator = U1DArrayUpdateTranslate(this, initialValue)
        return URegionTranslator(updateTranslator)
    }

    open fun <Key, Sort : USort> buildTranslator(
        regionId: URegionId<Key, Sort, *>,
    ): URegionTranslator<URegionId<Key, Sort, *>, Key, Sort, *> {
        @Suppress("UNCHECKED_CAST")
        return regionId.accept(this) as URegionTranslator<URegionId<Key, Sort, *>, Key, Sort, *>
    }

    val regionIdInitialValueProvider = URegionIdInitialValueFactoryBase(onDefaultValuePresent = { translate(it) })
}

/**
 * Tracks translated symbols. This information used in [UModelDecoderBase].
 */
open class UCachingExprTranslator<Field, Type>(
    ctx: UContext,
) : UExprTranslator<Field, Type>(ctx) {

    val registerIdxToTranslated = mutableMapOf<Int, UExpr<*>>()

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> =
        registerIdxToTranslated.getOrPut(expr.idx) {
            super.transform(expr)
        }.cast()

    val indexedMethodReturnValueToTranslated = mutableMapOf<Pair<*, Int>, UExpr<*>>()

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> =
        indexedMethodReturnValueToTranslated.getOrPut(expr.method to expr.callIndex) {
            super.transform(expr)
        }.cast()

    val translatedNullRef = super.transform(ctx.nullRef)

    val regionIdToTranslator =
        mutableMapOf<URegionId<*, *, *>, URegionTranslator<URegionId<*, *, *>, *, *, *>>()

    override fun <Key, Sort : USort> buildTranslator(
        regionId: URegionId<Key, Sort, *>,
    ): URegionTranslator<URegionId<Key, Sort, *>, Key, Sort, *> =
        regionIdToTranslator.getOrPut(regionId) {
            super.buildTranslator(regionId).cast()
        }.cast()
}

typealias URegionIdInitialValueFactory = URegionIdVisitor<UExpr<out KArraySortBase<*>>>

/**
 * @param onDefaultValuePresent translates default values.
 */
open class URegionIdInitialValueFactoryBase(
    val onDefaultValuePresent: (UExpr<*>) -> KExpr<*>,
) : URegionIdInitialValueFactory {
    override fun <Field, Sort : USort> visit(regionId: UInputFieldId<Field, Sort>): KExpr<KArraySort<UAddressSort, Sort>> {
        require(regionId.defaultValue == null)
        return with(regionId.sort.uctx) {
            mkArraySort(addressSort, regionId.sort).mkConst(regionId.toString()) // TODO: replace toString
        }
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UAllocatedArrayId<ArrayType, Sort>): KExpr<KArraySort<USizeSort, Sort>> {
        @Suppress("SENSELESS_COMPARISON")
        require(regionId.defaultValue != null)
        return with(regionId.sort.uctx) {
            val sort = mkArraySort(sizeSort, regionId.sort)

            @Suppress("UNCHECKED_CAST")
            val value = onDefaultValuePresent(regionId.defaultValue) as UExpr<Sort>
            mkArrayConst(sort, value)
        }
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UInputArrayId<ArrayType, Sort>): KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> {
        require(regionId.defaultValue == null)
        return with(regionId.sort.uctx) {
            mkArraySort(addressSort, sizeSort, regionId.sort).mkConst(regionId.toString()) // TODO: replace toString
        }
    }

    override fun <ArrayType> visit(regionId: UInputArrayLengthId<ArrayType>): KExpr<KArraySort<UAddressSort, USizeSort>> {
        require(regionId.defaultValue == null)
        return with(regionId.sort.uctx) {
            mkArraySort(addressSort, sizeSort).mkConst(regionId.toString()) // TODO: replace toString
        }
    }
}