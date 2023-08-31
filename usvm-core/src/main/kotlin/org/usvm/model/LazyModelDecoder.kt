package org.usvm.model

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KUninterpretedSort
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.solver.UExprTranslator
import org.usvm.UMockEvaluator

interface UModelDecoder<Model> {
    fun decode(model: KModel): Model
}

/**
 * Initializes [UExprTranslator] and [UModelDecoder] and returns them. We can safely reuse them while [UContext] is
 * alive.
 */
fun <Type> buildTranslatorAndLazyDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Type>, ULazyModelDecoder<Type>> {
    val translator = UExprTranslator<Type>(ctx)
    val decoder = ULazyModelDecoder<Type>(translator)

    return translator to decoder
}

typealias AddressesMapping = Map<UExpr<UAddressSort>, UConcreteHeapRef>


/**
 * A lazy decoder suitable for decoding [KModel] to [UModelBase]. We can safely reuse it between different root methods.
 *
 * @param translator an expression translator used for encoding constraints.
 */
open class ULazyModelDecoder<Type>(
    protected val translator: UExprTranslator<Type>,
) : UModelDecoder<UModelBase<Type>> {
    private val ctx: UContext = translator.ctx

    private val translatedNullRef = translator.translate(ctx.nullRef)

    /**
     * Build a mapping from instances of an uninterpreted [UAddressSort]
     * to [UConcreteHeapRef] with integer addresses. It allows us to enumerate
     * equivalence classes of addresses and work with their number in the future.
     */
    private fun buildMapping(model: KModel): AddressesMapping {
        val interpreterdNullRef = model.eval(translatedNullRef, isComplete = true)

        val result = mutableMapOf<KExpr<KUninterpretedSort>, UConcreteHeapRef>()
        // The null value has the NULL_ADDRESS
        result[interpreterdNullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)

        val universe = model.uninterpretedSortUniverse(ctx.addressSort) ?: return result
        // All the numbers are enumerated from the INITIAL_INPUT_ADDRESS to the Int.MIN_VALUE
        var counter = INITIAL_INPUT_ADDRESS

        for (interpretedAddress in universe) {
            if (interpretedAddress == interpreterdNullRef) {
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
        val regions = decodeHeap(model, addressesMapping)
        val types = UTypeModel<Type>(ctx.typeSystem(), typeStreamByAddr = emptyMap())
        val mocks = decodeMocker(model, addressesMapping)

        /**
         * To resolve nullRef, we need to:
         * * translate it
         * * evaluate the translated value in the [model]
         * * map the evaluated value with the [addressesMapping]
         *
         * Actually, its address should always be equal 0.
         */
        val nullRef = model
            .eval(translator.translate(translator.ctx.nullRef))
            .mapAddress(addressesMapping) as UConcreteHeapRef

        check(nullRef.address == NULL_ADDRESS) { "Incorrect null ref: $nullRef" }

        return UModelBase(ctx, stack, types, mocks, regions, nullRef)
    }

    private fun decodeStack(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyRegistersStackModel = ULazyRegistersStackModel(
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
    ) = translator.regionIdToDecoder.mapValues { (_, decoder) ->
        decoder.decodeLazyRegion(model, addressesMapping)
    }

    private fun decodeMocker(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): UMockEvaluator = ULazyIndexedMockModel(
        model,
        addressesMapping,
        translator
    )
}
