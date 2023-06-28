package org.usvm.model

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KUninterpretedSort
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.constraints.UTypeModel
import org.usvm.memory.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.memory.UAddressCounter.Companion.NULL_ADDRESS
import org.usvm.memory.UMemoryBase
import org.usvm.solver.UExprTranslator

interface UModelDecoder<Memory, Model> {
    fun decode(model: KModel): Model
}

/**
 * Initializes [UExprTranslator] and [UModelDecoder] and returns them. We can safely reuse them while [UContext] is
 * alive.
 */
fun <Field, Type, Method> buildTranslatorAndLazyDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Field, Type>, ULazyModelDecoder<Field, Type, Method>> {
    val translator = UExprTranslator<Field, Type>(ctx)

    val decoder = ULazyModelDecoder<Field, Type, Method>(
        translator
    )

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
    protected val translator: UExprTranslator<Field, Type>,
) : UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>> {
    private val ctx: UContext = translator.ctx

    private val translatedNullRef = translator.translate(ctx.nullRef)

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
    ): UModelBase<Field, Type> {
        val addressesMapping = buildMapping(model)

        val stack = decodeStack(model, addressesMapping)
        val heap = decodeHeap(model, addressesMapping)
        val types = UTypeModel<Type>(ctx.typeSystem(), typeByAddr = emptyMap())
        val mocks = decodeMocker(model, addressesMapping)

        return UModelBase(ctx, stack, heap, types, mocks)
    }

    private fun decodeStack(model: KModel, addressesMapping: AddressesMapping): ULazyRegistersStackModel =
        ULazyRegistersStackModel(
            model,
            addressesMapping,
            translator
        )

    /**
     * Constructs a [ULazyHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    private fun decodeHeap(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyHeapModel<Field, Type> = ULazyHeapModel(
        model,
        addressesMapping,
        translator,
    )

    private fun decodeMocker(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyIndexedMockModel =
        ULazyIndexedMockModel(
            model,
            addressesMapping,
            translator
        )
}
