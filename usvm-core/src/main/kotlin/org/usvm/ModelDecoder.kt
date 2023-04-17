package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.expr.KInterpretedValue
import org.ksmt.solver.KModel
import org.ksmt.sort.KUninterpretedSort
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast
import org.usvm.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import org.ksmt.utils.sampleValue

interface UModelDecoder<Type, Memory, Model> {
    fun decode(memory: Memory, model: KModel): Model
}

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

open class UModelDecoderBase<Field, Type, Method>(
    protected val registerIdxToTranslated: Map<Int, UExpr<out USort>>,
    protected val indexedMethodReturnValueToTranslated: Map<Pair<*, Int>, UExpr<*>>,
    protected val translatedNullRef: UExpr<UAddressSort>,
    protected val translatedRegionIds: Set<URegionId<*, *, *>>,
    protected val regionIdInitialValueProvider: URegionIdInitialValueFactory,
) : UModelDecoder<Type, UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>> {
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
            resolvedInputFields.toPersistentMap(),
            resolvedInputArrays.toPersistentMap(),
            resolvedInputArrayLengths.toPersistentMap()
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

class UHeapModel<Field, ArrayType>(
    private val nullRef: UConcreteHeapRef,
    private var resolvedInputFields: PersistentMap<Field, UMemoryRegion<UHeapRef, out USort>>,
    private var resolvedInputArrays: PersistentMap<ArrayType, UMemoryRegion<USymbolicArrayIndex, out USort>>,
    private var resolvedInputLengths: PersistentMap<ArrayType, UMemoryRegion<UHeapRef, USizeSort>>,
) : USymbolicHeap<Field, ArrayType> {

    @Suppress("UNCHECKED_CAST")
    private fun <Sort : USort> inputFieldRegion(field: Field, sort: Sort): UMemoryRegion<UHeapRef, Sort> =
        resolvedInputFields.getOrElse(field) {
            UMemory1DArray(sort.sampleValue().nullAddress(nullRef))
        } as UMemoryRegion<UHeapRef, Sort>

    @Suppress("UNCHECKED_CAST")
    private fun <Sort : USort> inputArrayRegion(arrayType: ArrayType, sort: Sort): UMemoryRegion<USymbolicArrayIndex, Sort> =
        resolvedInputArrays.getOrElse(arrayType) {
            UMemory2DArray(sort.sampleValue().nullAddress(nullRef))
        } as UMemoryRegion<USymbolicArrayIndex, Sort>

    private fun inputLengthRegion(arrayType: ArrayType, sort: USizeSort): UMemoryRegion<UHeapRef, USizeSort> =
        resolvedInputLengths.getOrElse(arrayType) {
            UMemory1DArray(sort.sampleValue())
        }


    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val region = inputFieldRegion(field, sort)

        return region.read(ref)
    }

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        sort: Sort,
    ): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val key = ref to index

        val region = inputArrayRegion(arrayType, sort)

        return region.read(key)
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val region = inputLengthRegion(arrayType, ref.uctx.sizeSort)

        return region.read(ref)
    }

    override fun <Sort : USort> writeField(
        ref: UHeapRef,
        field: Field,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        // Since all values in the model are interpreted, we can check the exact guard value.
        when {
            guard.isFalse -> return
            else -> require(guard.isTrue)
        }

        val valueToWrite = value.asExpr(sort)

        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val region = inputFieldRegion(field, sort).write(ref, valueToWrite, guard)
        resolvedInputFields = resolvedInputFields.put(field, region)

    }

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        // Since all values in the model are interpreted, we can check the exact guard value.
        when {
            guard.isFalse -> return
            else -> require(guard.isTrue)
        }

        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(index is KInterpretedValue<USizeSort>)
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val valueToWrite = value.asExpr(sort)

        val region = inputArrayRegion(type, sort).write(ref to index, valueToWrite, guard)
        resolvedInputArrays = resolvedInputArrays.put(type, region)
    }

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(size is KInterpretedValue<USizeSort>)
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val region = inputLengthRegion(arrayType, size.sort).write(ref, size, size.sort.uctx.trueExpr)
        resolvedInputLengths = resolvedInputLengths.put(arrayType, region)
    }

    override fun <Sort : USort> memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ): Unit = error("Illegal operation for a model")

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ): Unit = error("Illegal operation for a model")

    override fun allocate() = error("Illegal operation for a model")

    override fun allocateArray(count: USizeExpr): UConcreteHeapAddress = error("Illegal operation for a model")

    override fun clone(): UHeapModel<Field, ArrayType> =
        UHeapModel(
            nullRef,
            resolvedInputFields,
            resolvedInputArrays,
            resolvedInputLengths,
        )

    override fun nullRef(): UConcreteHeapRef = nullRef

    override fun toMutableHeap(): UHeapModel<Field, ArrayType> = clone()
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