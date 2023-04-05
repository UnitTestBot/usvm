package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.sort.KUninterpretedSort
import org.usvm.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS

interface UModelDecoder<Memory, Model> {
    fun decode(memory: Memory, model: KModel): Model
}

/**
 * Initializes [UExprTranslator] and [UModelDecoder] and returns them. We can safely reuse them while [UContext] is
 * alive.
 */
fun <Field, Type, Method> buildTranslatorAndLazyDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Field, Type>, ULazyModelDecoder<Field, Type, Method>> {
    val translator = UCachingExprTranslator<Field, Type>(ctx)

    val decoder = with(translator) {
        ULazyModelDecoder<Field, Type, Method>(
            registerIdxToTranslated,
            indexedMethodReturnValueToTranslated,
            translatedNullRef,
            regionIdToTranslator.keys,
            regionIdToInitialValue,
        )
    }

    return translator to decoder
}

typealias AddressesMapping = Map<UExpr<UAddressSort>, UConcreteHeapRef>


/**
 * A lazy decoder suitable for decoding [KModel] to [UModelBase]. It can't be reused between different root methods,
 * because of a matched translator caches.
 *
 * Passed parameters updates on the fly in a matched translator, so they are mutable in fact.
 *
 * @param registerIdxToTranslated a mapping from a register idx to a translated expression.
 * @param indexedMethodReturnValueToTranslated a mapping from an indexed mock symbol to a translated expression.
 * @param translatedNullRef translated null reference.
 * @param translatedRegionIds a set of translated region ids.
 * @param regionIdToInitialValue an initial value provider, the same as used in the translator, so we can build
 * concrete regions from a [KModel].
 */
open class ULazyModelDecoder<Field, Type, Method>(
    protected val registerIdxToTranslated: Map<Int, UExpr<out USort>>,
    protected val indexedMethodReturnValueToTranslated: Map<Pair<*, Int>, UExpr<*>>,
    protected val translatedNullRef: UExpr<UAddressSort>,
    protected val translatedRegionIds: Set<URegionId<*, *, *>>,
    protected val regionIdToInitialValue: Map<URegionId<*, *, *>, KExpr<*>>,
) : UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>> {
    private val ctx: UContext = translatedNullRef.uctx

    /**
     * Build a mapping from instances of an uninterpreted [UAddressSort]
     * to [UConcreteHeapRef] with integer addresses. It allows us to enumerate
     * equivalence classes of addresses and work with their number in the future.
     */
    private fun buildMapping(model: KModel): AddressesMapping {
        // Null is a special value that we want to translate in any case.
        val interpretedNullRef = model.eval(translatedNullRef, isComplete = true)

        val result = mutableMapOf<KExpr<KUninterpretedSort>, UConcreteHeapRef>()
        // Except the null value, it has the NULL_ADDRESS
        result[interpretedNullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        result[translatedNullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)

        val universe = model.uninterpretedSortUniverse(ctx.addressSort) ?: return result
        // All the numbers are enumerated from the INITIAL_INPUT_ADDRESS to the Int.MIN_VALUE
        var counter = INITIAL_INPUT_ADDRESS

        for (interpretedAddress in universe) {
            if (interpretedAddress == interpretedNullRef) {
                continue
            }
            result[interpretedAddress] = ctx.mkConcreteHeapRef(counter--)
        }

        return result
    }

    /**
     * Decodes a [model] from a [memory] to a [UModelBase].
     *
     * @param model should be detached.
     */
    override fun decode(
        memory: UMemoryBase<Field, Type, Method>,
        model: KModel,
    ): UModelBase<Field, Type> {
        val addressesMapping = buildMapping(model)

        val stack = decodeStack(model, addressesMapping)
        val heap = decodeHeap(model, addressesMapping)
        val types = UTypeModel(ctx, memory.typeSystem, typeByAddr = emptyMap())
        val mocks = decodeMocker(model, addressesMapping)

        return UModelBase(ctx, stack, heap, types, mocks)
    }

    private fun decodeStack(model: KModel, addressesMapping: AddressesMapping): ULazyRegistersStackModel =
        ULazyRegistersStackModel(
            model,
            addressesMapping,
            addressesMapping.getValue(translatedNullRef),
            registerIdxToTranslated
        )

    /**
     * Constructs a [ULazyHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    private fun decodeHeap(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyHeapModel<Field, Type> = ULazyHeapModel(
        model,
        addressesMapping.getValue(translatedNullRef),
        addressesMapping,
        regionIdToInitialValue,
    )

    private fun decodeMocker(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyIndexedMockModel<Method> =
        ULazyIndexedMockModel(
            model,
            addressesMapping,
            addressesMapping.getValue(translatedNullRef),
            indexedMethodReturnValueToTranslated
        )
}
