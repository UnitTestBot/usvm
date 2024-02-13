package org.usvm.model

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.utils.uncheckedCast
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.solver.UExprTranslator

interface UModelDecoder<Model> {
    fun decode(model: KModel, assertions: List<KExpr<KBoolSort>>): Model
}


typealias AddressesMapping = Map<UExpr<UAddressSort>, UConcreteHeapRef>


/**
 * A lazy decoder suitable for decoding [KModel] to [UModelBase]. We can safely reuse it between different root methods.
 *
 * @param translator an expression translator used for encoding constraints.
 */
open class ULazyModelDecoder<Type>(
    protected val translator: UExprTranslator<Type, *>,
) : UModelDecoder<UModelBase<Type>> {
    private val ctx: UContext<*> = translator.ctx

    private val translatedNullRef = translator.translate(ctx.nullRef)

    /**
     * Build a mapping from instances of an uninterpreted [UAddressSort]
     * to [UConcreteHeapRef] with integer addresses. It allows us to enumerate
     * equivalence classes of addresses and work with their number in the future.
     */
    private fun buildMapping(model: KModel, nullRef: UConcreteHeapRef): AddressesMapping {
        val interpretedNullRef = model.eval(translatedNullRef, isComplete = true)

        val result = mutableMapOf<KExpr<KUninterpretedSort>, UConcreteHeapRef>()
        // The null value has the NULL_ADDRESS
        result[interpretedNullRef] = nullRef

        val universe = model.uninterpretedSortUniverse(ctx.addressSort) ?: return result
        // All the numbers are enumerated from the INITIAL_INPUT_ADDRESS to the Int.MIN_VALUE
        var counter = INITIAL_INPUT_ADDRESS

        for (interpretedAddress in universe) {
            if (interpretedAddress == interpretedNullRef) {
                continue
            }

            // Static refs already have negative addresses, so just reuse them
            if (interpretedAddress.valueIdx <= INITIAL_STATIC_ADDRESS) {
                result[interpretedAddress] = ctx.mkConcreteHeapRef(interpretedAddress.valueIdx)
                continue
            }

            result[interpretedAddress] = ctx.mkConcreteHeapRef(counter--)
        }

        return result
    }

    /**
     * Creates a [UModelEvaluator] for the provided [model].
     *
     * See [UModelEvaluator] for the model completion and expression evaluation details.
     * */
    open fun buildModelEvaluator(model: KModel, addressesMapping: AddressesMapping): UModelEvaluator<*> =
        UModelEvaluator(ctx, model, addressesMapping)

    /**
     * Decodes a [model] into a [UModelBase].
     *
     * @param model should be detached.
     */
    override fun decode(
        model: KModel,
        assertions: List<KExpr<KBoolSort>>,
    ): UModelBase<Type> {
        val nullRef = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        val addressesMapping = buildMapping(model, nullRef)

        val evaluator = buildModelEvaluator(model, addressesMapping)
        val stack = decodeStack(evaluator)
        val regions = decodeHeap(evaluator, assertions)
        val types = UTypeModel<Type>(ctx.typeSystem(), typeRegionByAddr = emptyMap())
        val mocks = decodeMocker(evaluator)

        return UModelBase(ctx, stack, types, mocks, regions, nullRef)
    }

    private fun decodeStack(model: UModelEvaluator<*>): ULazyRegistersStackModel =
        ULazyRegistersStackModel(model, translator)

    /**
     * Constructs a [ULazyHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    private fun decodeHeap(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>,
    ): Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>> {
        val result = hashMapOf<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>()
        for ((regionId, decoder) in translator.regionIdToDecoder) {
            val modelRegion = decoder.decodeLazyRegion(model, assertions) ?: continue
            result[regionId] = modelRegion
        }
        return UHeapModelWithCompletion(result, model)
    }

    private fun decodeMocker(model: UModelEvaluator<*>): UMockEvaluator =
        ULazyIndexedMockModel(model, translator)

    private class UHeapModelWithCompletion(
        val regions: Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>,
        val model: UModelEvaluator<*>
    ) : Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>> by regions {
        override fun get(key: UMemoryRegionId<*, *>): UReadOnlyMemoryRegion<*, *> =
            regions[key] ?: completeRegion(key)

        private val completedRegions = hashMapOf<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>()

        private fun <Key, Sort : USort> completeRegion(
            regionId: UMemoryRegionId<Key, Sort>
        ): UReadOnlyMemoryRegion<Key, Sort> = completedRegions.getOrPut(regionId) {
            DefaultRegion(regionId, regionId.sort.accept(model).uncheckedCast())
        }.uncheckedCast()
    }

    private class DefaultRegion<Key, Sort : USort>(
        private val regionId: UMemoryRegionId<Key, Sort>,
        private val value: UExpr<Sort>
    ) : UReadOnlyMemoryRegion<Key, Sort> {
        override fun read(key: Key): UExpr<Sort> = value
    }
}
