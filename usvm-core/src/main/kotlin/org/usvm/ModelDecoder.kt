package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.sort.KUninterpretedSort
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast
import org.usvm.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS
import org.ksmt.utils.sampleValue

interface UModelDecoder<Memory, Model> {
    fun decode(memory: Memory, model: KModel): Model
}

/**
 * Initializes [UExprTranslator] and [UModelDecoder] and returns them. They bounded to the root method of the analysis,
 * because of internal caches (e.g., translated register readings).
 */
fun <Field, Type, Method> buildDefaultTranslatorAndDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Field, Type>, UModelDecoderBase<Field, Type, Method>> {
    val translator = UCachingExprTranslator<Field, Type>(ctx)

    val decoder = UModelDecoderBase<Field, Type, Method>(
        translator.registerIdxToTranslated,
        translator.indexedMethodReturnValueToTranslated,
        translator.translatedNullRef,
        translator.regionIdToTranslator.keys,
        translator.regionIdInitialValueProvider,
    )

    return translator to decoder
}

typealias AddressesMapping = Map<UExpr<UAddressSort>, UConcreteHeapRef>


/**
 * Base decoder suitable for decoding [KModel] to [UModelBase]. It can't be reused between different root methods,
 * because of a matched translator caches.
 *
 * Passed parameters updates on the fly in a matched translator, so they are mutable in fact.
 *
 * @param registerIdxToTranslated a mapping from a register idx to a translated expression.
 * @param indexedMethodReturnValueToTranslated a mapping from an indexed mock symbol to a translated expression.
 * @param translatedNullRef translated null reference.
 * @param translatedRegionIds a set of translated region ids.
 * @param regionIdInitialValueProvider an inital value provider, the same as used in the translator, so we can build
 * concrete regions from a [KModel].
 */
open class UModelDecoderBase<Field, Type, Method>(
    protected val registerIdxToTranslated: Map<Int, UExpr<out USort>>,
    protected val indexedMethodReturnValueToTranslated: Map<Pair<*, Int>, UExpr<*>>,
    protected val translatedNullRef: UExpr<UAddressSort>,
    protected val translatedRegionIds: Set<URegionId<*, *, *>>,
    protected val regionIdInitialValueProvider: URegionIdInitialValueFactory,
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

    private fun decodeStack(model: KModel, addressesMapping: AddressesMapping): URegistersStackModel {
        val registers = registerIdxToTranslated.replaceUninterpretedConsts(model, addressesMapping)

        return URegistersStackModel(addressesMapping.getValue(translatedNullRef), registers)
    }

    /**
     * Constructs a [UHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    @Suppress("UNCHECKED_CAST")
    private fun decodeHeap(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): UHeapModel<Field, Type> {
        val resolvedInputFields = mutableMapOf<Field, UMemoryRegion<UHeapRef, *>>()
        val resolvedInputArrays = mutableMapOf<Type, UMemoryRegion<USymbolicArrayIndex, *>>()
        val resolvedInputArrayLengths = mutableMapOf<Type, UMemoryRegion<UHeapRef, USizeSort>>()

        // Performs decoding from model to concrete UMemoryRegions.
        // It's an internal knowledge of initialValue for each type of URegionId, so we use .cast() here.
        translatedRegionIds.forEach {
            when (it) {
                is UInputFieldId<*, *> -> {
                    val resolved = UMemory1DArray<UAddressSort, USort>(
                        regionIdInitialValueProvider.visit(it).cast(),
                        model,
                        addressesMapping
                    )
                    resolvedInputFields[it.field as Field] = resolved
                }

                is UInputArrayId<*, *> -> {
                    val resolved = UMemory2DArray<UAddressSort, USizeSort, USort>(
                        regionIdInitialValueProvider.visit(it).cast(),
                        model,
                        addressesMapping
                    )
                    resolvedInputArrays[it.arrayType as Type] = resolved
                }

                is UInputArrayLengthId<*> -> {
                    val resolved = UMemory1DArray<UAddressSort, USizeSort>(
                        regionIdInitialValueProvider.visit(it).cast(),
                        model,
                        addressesMapping
                    )
                    resolvedInputArrayLengths[it.arrayType as Type] = resolved
                }
            }
        }

        return UHeapModel(
            addressesMapping.getValue(translatedNullRef),
            resolvedInputFields,
            resolvedInputArrays,
            resolvedInputArrayLengths
        )
    }

    private fun decodeMocker(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): UIndexedMockModel<Method> {
        val values = indexedMethodReturnValueToTranslated.replaceUninterpretedConsts(model, addressesMapping)

        return UIndexedMockModel(addressesMapping.getValue(translatedNullRef), values)
    }

    /**
     * Since expressions from [this] might have the [UAddressSort] and therefore
     * they could be uninterpreted constants, we have to replace them with
     * corresponding concrete addresses from the [addressesMapping].
     */
    private fun <K> Map<K, UExpr<out USort>>.replaceUninterpretedConsts(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): Map<K, UExpr<out USort>> {
        val values = mapValues { (_, expr) ->
            val value = model.eval(expr, isComplete = true)
            val transformedValue = value.mapAddress(addressesMapping)

            transformedValue
        }

        return values
    }

}

class URegistersStackModel(
    private val nullRef: UConcreteHeapRef,
    private val registers: Map<Int, UExpr<out USort>>
) : URegistersStackEvaluator {
    override fun <Sort : USort> eval(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> = registers.getOrDefault(registerIndex, sort.sampleValue().nullAddress(nullRef)).asExpr(sort)
}

/**
 * A model for an indexed mocker that stores mapping
 * from mock symbols and invocation indices to expressions.
 */
class UIndexedMockModel<Method>(
    private val nullRef: UConcreteHeapRef,
    private val values: Map<Pair<*, Int>, UExpr<*>>,
) : UMockEvaluator {

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)

        val sort = symbol.sort

        @Suppress("UNCHECKED_CAST")
        val key = symbol.method as Method to symbol.callIndex

        return values.getOrDefault(key, sort.sampleValue().nullAddress(nullRef)).asExpr(sort)
    }
}

/**
 * An immutable heap model. Declared as mutable heap for using in regions composition in [UComposer]. Any call to
 * modifying operation throws an exception.
 *
 * Any [UHeapReading] possibly writing to this heap in its [URegionId.instantiate] call actually has empty updates,
 * because localization took place, so this heap won't be mutated.
 */
class UHeapModel<Field, ArrayType>(
    private val nullRef: UConcreteHeapRef,
    private val resolvedInputFields: Map<Field, UMemoryRegion<UHeapRef, out USort>>,
    private val resolvedInputArrays: Map<ArrayType, UMemoryRegion<USymbolicArrayIndex, out USort>>,
    private val resolvedInputLengths: Map<ArrayType, UMemoryRegion<UHeapRef, USizeSort>>,
) : USymbolicHeap<Field, ArrayType> {
    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        @Suppress("UNCHECKED_CAST")
        val region = resolvedInputFields.getOrElse(field) {
            UMemory1DArray(sort.sampleValue().nullAddress(nullRef))
        } as UMemoryRegion<UHeapRef, Sort>

        return region.read(ref)
    }

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        sort: Sort,
    ): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val key = ref to index

        @Suppress("UNCHECKED_CAST")
        val region = resolvedInputArrays.getOrElse(arrayType) {
            UMemory2DArray(sort.sampleValue().nullAddress(nullRef))
        } as UMemoryRegion<USymbolicArrayIndex, Sort>

        return region.read(key)
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val region = resolvedInputLengths.getOrElse<ArrayType, UMemoryRegion<UHeapRef, USizeSort>>(arrayType) {
            UMemory1DArray(ref.uctx.sizeSort.sampleValue())
        }

        return region.read(ref)
    }

    override fun <Sort : USort> writeField(
        ref: UHeapRef,
        field: Field,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) = error("Illegal operation for a model")

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) = error("Illegal operation for a model")

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) =
        error("Illegal operation for a model")

    override fun <Sort : USort> memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ) = error("Illegal operation for a model")

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ) = error("Illegal operation for a model")

    override fun allocate() = error("Illegal operation for a model")

    override fun allocateArray(count: USizeExpr) = error("Illegal operation for a model")

    override fun nullRef(): UConcreteHeapRef = nullRef

    override fun toMutableHeap(): UHeapModel<Field, ArrayType> = this
}

fun <T : USort> UExpr<T>.nullAddress(nullRef: UConcreteHeapRef): UExpr<T> =
    if (this == uctx.nullRef) {
        nullRef.asExpr(sort)
    } else {
        this
    }

/**
 * If [this] value is an instance of address expression, returns
 * an expression with a corresponding concrete address, otherwise
 * returns [this] unchanched.
 */
fun <S : USort> UExpr<S>.mapAddress(
    addressesMapping: AddressesMapping,
): UExpr<S> = if (sort == uctx.addressSort) {
    addressesMapping.getValue(asExpr(uctx.addressSort)).asExpr(sort)
} else {
    this
}