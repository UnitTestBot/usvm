package org.usvm.model

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KUninterpretedSort
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.solver.UExprTranslator
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.solver.UTrackingExprTranslator
import org.usvm.constraints.UTypeModel
import org.usvm.memory.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.memory.UAddressCounter.Companion.NULL_ADDRESS
import org.usvm.memory.UMemoryBase
import org.usvm.memory.collections.USymbolicCollectionId
import org.usvm.uctx

interface UModelDecoder<Model> {
    fun decode(model: KModel): Model
}

/**
 * Initializes [UExprTranslator] and [UModelDecoder] and returns them. We can safely reuse them while [UContext] is
 * alive.
 */
fun <Type, Method> buildTranslatorAndLazyDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Type>, ULazyModelDecoder<Type>> {
    val translator = UTrackingExprTranslator<Type>(ctx)

    val decoder = with(translator) {
        ULazyModelDecoder<Type>(
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
open class ULazyModelDecoder<Type>(
    protected val registerIdxToTranslated: Map<Int, UExpr<out USort>>,
    protected val indexedMethodReturnValueToTranslated: Map<Pair<*, Int>, UExpr<*>>,
    protected val translatedNullRef: UHeapRef,
    protected val translatedRegionIds: Set<USymbolicCollectionId<*, *, *>>,
    protected val regionIdToInitialValue: Map<USymbolicCollectionId<*, *, *>, KExpr<*>>,
) : UModelDecoder<UModelBase<Type>> {
    private val ctx: UContext = translatedNullRef.uctx

    /**
     * Build a mapping from instances of an uninterpreted [UAddressSort]
     * to [UConcreteHeapRef] with integer addresses. It allows us to enumerate
     * equivalence classes of addresses and work with their number in the future.
     */
    private fun buildMapping(model: KModel): AddressesMapping {
        // Translated null has to be equal to evaluated null, because it is of KUninterpretedSort and translatedNullRef
        // defined as mkUninterpretedSortValue(addressSort, 0).
        check(translatedNullRef === model.eval(translatedNullRef, isComplete = true))

        val result = mutableMapOf<KExpr<KUninterpretedSort>, UConcreteHeapRef>()
        // Except the null value, it has the NULL_ADDRESS
        result[translatedNullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)

        val universe = model.uninterpretedSortUniverse(ctx.addressSort) ?: return result
        // All the numbers are enumerated from the INITIAL_INPUT_ADDRESS to the Int.MIN_VALUE
        var counter = INITIAL_INPUT_ADDRESS

        for (interpretedAddress in universe) {
            if (interpretedAddress == translatedNullRef) {
                continue
            }
            result[interpretedAddress] = ctx.mkConcreteHeapRef(counter--)
        }

        return result
    }

    /**
     * Decodes a [model] into a [UModelBase].
     *
     * @param model should be detached.
     */
    override fun decode(
        model: KModel,
    ): UModelBase<Type> {
        val addressesMapping = buildMapping(model)

        val stack = decodeStack(model, addressesMapping)
        val heap = decodeHeap(model, addressesMapping)
        val types = UTypeModel<Type>(ctx.typeSystem(), typeByAddr = emptyMap())
        val mocks = decodeMocker(model, addressesMapping)

        return UModelBase(ctx, stack, types, mocks, ...)
    }

    private fun decodeStack(model: KModel, addressesMapping: AddressesMapping): ULazyRegistersStackModel =
        ULazyRegistersStackModel(
            model,
            addressesMapping,
            registerIdxToTranslated
        )

    /**
     * Constructs a [ULazyHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    private fun decodeHeap(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyHeapModel<Type> = ULazyHeapModel(
        model,
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
            indexedMethodReturnValueToTranslated
        )
}
