package org.usvm

import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort
import org.ksmt.sort.KArraySortBase
import org.ksmt.utils.cast
import org.ksmt.utils.mkConst

open class UExprTranslator<Field, Type> constructor(
    override val ctx: UContext,
) : UExprTransformer<Field, Type>(ctx), URegionIdTranslatorFactory {

    open fun <Sort : USort> translate(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> {
        // TODO: we must ensure all ids are different
        val registerConst = expr.sort.mkConst("r${expr.idx}")
        return registerConst
    }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> {
        // TODO: we must ensure all ids are different
        val const = expr.sort.mkConst("m${expr.method}_${expr.callIndex}")
        return const
    }

    override fun transform(expr: UNullRef): UExpr<UAddressSort> {
        val const = expr.sort.mkConst("null")
        return const
    }

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> =
        error("Unexpected UConcreteHeapRef $expr in UExprTranslator, that has to be impossible by construction!")

    override fun transform(expr: UIsExpr<Type>): UBoolExpr =
        error("Unexpected UIsExpr $expr in UExprTranslator, that has to be impossible by construction!")

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

    open fun <Key, Sort : USort> translateRegionReading(
        region: USymbolicMemoryRegion<URegionId<Key, Sort, *>, Key, Sort>,
        key: Key,
    ): UExpr<Sort> {
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

    val regionIdInitialValueProvider = URegionIdInitialValueProvider(onDefaultValuePresent = { translate(it) })
}

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

interface URegionIdTranslatorFactory : URegionIdVisitor<URegionTranslator<*, *, *, *>> {
    fun <Key, Sort : USort> buildTranslator(
        regionId: URegionId<Key, Sort, *>,
    ): URegionTranslator<URegionId<Key, Sort, *>, Key, Sort, *> {
        @Suppress("UNCHECKED_CAST")
        return regionId.accept(this) as URegionTranslator<URegionId<Key, Sort, *>, Key, Sort, *>
    }
}

typealias URegionIdInitialValueFactory = URegionIdVisitor<out UExpr<*>>

open class URegionIdInitialValueProvider(
    val onDefaultValuePresent: (UExpr<*>) -> UExpr<*>,
) : URegionIdVisitor<UExpr<out KArraySortBase<*>>> {
    override fun <Field, Sort : USort> visit(regionId: UInputFieldId<Field, Sort>): UExpr<KArraySort<UAddressSort, Sort>> {
        require(regionId.defaultValue == null)
        return with(regionId.sort.uctx) {
            mkArraySort(addressSort, regionId.sort).mkConst(regionId.toString()) // TODO: replace toString
        }
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UAllocatedArrayId<ArrayType, Sort>): UExpr<KArraySort<USizeSort, Sort>> {
        @Suppress("SENSELESS_COMPARISON")
        require(regionId.defaultValue != null)
        return with(regionId.sort.uctx) {
            val sort = mkArraySort(sizeSort, regionId.sort)

            @Suppress("UNCHECKED_CAST")
            val value = onDefaultValuePresent(regionId.defaultValue) as UExpr<Sort>
            mkArrayConst(sort, value)
        }
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UInputArrayId<ArrayType, Sort>): UExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> {
        require(regionId.defaultValue == null)
        return with(regionId.sort.uctx) {
            mkArraySort(addressSort, sizeSort, regionId.sort).mkConst(regionId.toString()) // TODO: replace toString
        }
    }

    override fun <ArrayType> visit(regionId: UInputArrayLengthId<ArrayType>): UExpr<KArraySort<UAddressSort, USizeSort>> {
        require(regionId.defaultValue == null)
        return with(regionId.sort.uctx) {
            mkArraySort(addressSort, sizeSort).mkConst(regionId.toString()) // TODO: replace toString
        }
    }
}