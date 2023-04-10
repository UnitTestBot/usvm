package org.usvm

import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.utils.cast
import org.usvm.UModelDecoderBase.Companion.mapAddress

private class UCachingExprTranslator<Field, Type>(
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

    val nullRef = super.translate(ctx.nullRef)

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = nullRef

    val regionIdToTranslator =
        mutableMapOf<URegionId<*, *>, URegionTranslator<URegionId<*, *>, *, *, *>>()

    override fun <Key, Sort : USort> provide(regionId: URegionId<Key, Sort>): URegionTranslator<URegionId<Key, Sort>, Key, Sort, *> =
        regionIdToTranslator.getOrPut(regionId) {
            super.provide(regionId).cast()
        }.cast()
}

private class UCachingRegionEvaluatorProvider(
    val mapping: AddressesMapping,
    translatedRegionIds: Set<URegionId<*, *>>,
    regionEvaluatorProvider: URegionEvaluatorProviderFromKModel,
) : URegionEvaluatorProvider, URegionIdVisitor<URegionEvaluator<*, *>> {

    private val evaluatorsForTranslatedRegions: MutableMap<URegionId<*, *>, URegionEvaluator<*, *>>

    init {
        evaluatorsForTranslatedRegions = translatedRegionIds.associateWithTo(mutableMapOf()) { regionId ->
            regionEvaluatorProvider.apply(regionId)
        }
    }

    override fun <Key, Sort : USort> provide(regionId: URegionId<Key, Sort>): URegionEvaluator<Key, Sort> =
        evaluatorsForTranslatedRegions.getOrPut(regionId) {
            apply(regionId)
        }.cast()

    override fun <Field, Sort : USort> visit(regionId: UInputFieldId<Field, Sort>): U1DArrayEvaluator<UAddressSort, Sort> {
        // If some region has a default value, it means that the region is an allocated one.
        // All such regions must be processed earlier, and we won't have them here.
        require(regionId.defaultValue == null)
        // So, for these region we should take sample values for theis sorts.
        val mappedConstValue = regionId.sort.sampleValue().mapAddress(mapping)
        return U1DArrayEvaluator(mappedConstValue)
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UAllocatedArrayId<ArrayType, Sort>): URegionEvaluator<*, *> {
        error("Allocated arrays should be evaluated implicitly")
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UInputArrayId<ArrayType, Sort>): U2DArrayEvaluator<UAddressSort, USizeSort, Sort> {
        // If some region has a default value, it means that the region is an allocated one.
        // All such regions must be processed earlier, and we won't have them here.
        require(regionId.defaultValue == null)
        // So, for these region we should take sample values for theis sorts.
        val mappedConstValue = regionId.sort.sampleValue().mapAddress(mapping)
        return U2DArrayEvaluator(mappedConstValue)
    }

    override fun <ArrayType> visit(regionId: UInputArrayLengthId<ArrayType>): U1DArrayEvaluator<UAddressSort, USizeSort> {
        // If some region has a default value, it means that the region is an allocated one.
        // All such regions must be processed earlier, and we won't have them here.
        require(regionId.defaultValue == null)
        // So, for these region we should take sample values for theis sorts.
        val mappedConstValue = regionId.sort.sampleValue().mapAddress(mapping)
        return U1DArrayEvaluator(mappedConstValue)
    }
}

fun <Field, Type, Method> buildDefaultTranslatorAndDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Field, Type>, UModelDecoderBase<Field, Type, Method>> {
    val translator = UCachingExprTranslator<Field, Type>(ctx)

    val decoder = UModelDecoderBase<Field, Type, Method>(
        translator.registerIdxToTranslated,
        translator.indexedMethodReturnValueToTranslated,
        translator.nullRef
    ) { model, mapping ->
        val regionEvaluatorProviderFromKModel =
            URegionEvaluatorProviderFromKModel(model, mapping, translator.regionIdInitialValueProvider)
        UCachingRegionEvaluatorProvider(
            mapping,
            translator.regionIdToTranslator.keys,
            regionEvaluatorProviderFromKModel
        )
    }

    return translator to decoder
}