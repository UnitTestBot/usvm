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

    val decoder = ULazyModelDecoder<Field, Type, Method>(translator)

    return translator to decoder
}

typealias AddressesMapping = Map<UExpr<UAddressSort>, UConcreteHeapRef>


/**
 * A lazy decoder suitable for decoding [KModel] to [UModelBase]. We can safely reuse it between different root methods.
 *
 * @param translator an expression translator used for encoding constraints.
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
    ): UModelBase<Field, Type> {
        val addressesMapping = buildMapping(model)

        val stack = decodeStack(model, addressesMapping)
        val heap = decodeHeap(model, addressesMapping)
        val types = UTypeModel<Type>(ctx.typeSystem(), typeStreamByAddr = emptyMap())
        val mocks = decodeMocker(model, addressesMapping)

        return UModelBase(ctx, stack, heap, types, mocks)
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
    ): ULazyHeapModel<Field, Type> = ULazyHeapModel(
        model,
        addressesMapping,
        translator,
    )

    private fun decodeMocker(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): ULazyIndexedMockModel = ULazyIndexedMockModel(
        model,
        addressesMapping,
        translator
    )
}
