package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast
import org.ksmt.utils.sampleValue


/**
 * Since expressions from [this] might have the [UAddressSort] and therefore
 * they could be uninterpreted constants, we have to replace them with
 * corresponding concrete addresses from the [addressesMapping].
 */
private fun <K, T : USort> Map<K, UExpr<out USort>>.evalAndReplace(
    key: K,
    model: KModel,
    addressesMapping: AddressesMapping,
    sort: T
): UExpr<T> {
    val value = get(key)?.asExpr(sort) ?: sort.sampleValue()
    return model.eval(value, isComplete = true).mapAddress(addressesMapping)
}

/**
 * A lazy model for registers. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param registerIdxToTranslated a translated cache.
 * @param model has to be detached.
 */
class ULazyRegistersStackModel(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val registerIdxToTranslated: Map<Int, UExpr<out USort>>
) : URegistersStackEvaluator {
    override fun <Sort : USort> eval(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> = registerIdxToTranslated.evalAndReplace(key = registerIndex, model, addressesMapping, sort)
}

/**
 * A lazy model for an indexed mocker. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param indexedMethodReturnValueToTranslated a translated cache.
 * @param model has to be detached.
 */
class ULazyIndexedMockModel<Method>(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val indexedMethodReturnValueToTranslated: Map<Pair<*, Int>, UExpr<*>>,
) : UMockEvaluator {

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)

        val sort = symbol.sort

        @Suppress("UNCHECKED_CAST")
        val key = symbol.method as Method to symbol.callIndex

        return indexedMethodReturnValueToTranslated.evalAndReplace(key = key, model, addressesMapping, sort)
    }
}

/**
 *
 * A lazy immutable heap model. Firstly, searches for decoded [UMemoryRegion], decodes it from [model] if not found,
 * secondly, evaluates a value from it.
 *
 * Declared as mutable heap for using in regions composition in [UComposer]. Any call to
 * modifying operation throws an exception.
 *
 * Any [UHeapReading] possibly writing to this heap in its [URegionId.instantiate] call actually has empty updates,
 * because localization happened, so this heap won't be mutated.
 *
 * @param regionIdToInitialValue mapping from [URegionId] to initial values. We decode memory regions
 * using this cache.
 * @param model has to be detached.
 */
class ULazyHeapModel<Field, ArrayType>(
    private val model: KModel,
    private val nullRef: UConcreteHeapRef,
    private val addressesMapping: AddressesMapping,
    private val regionIdToInitialValue: Map<URegionId<*, *, *>, KExpr<*>>,
) : USymbolicHeap<Field, ArrayType> {
    private val resolvedInputFields = mutableMapOf<Field, UMemoryRegion<UHeapRef, out USort>>()
    private val resolvedInputArrays = mutableMapOf<ArrayType, UMemoryRegion<USymbolicArrayIndex, out USort>>()
    private val resolvedInputLengths = mutableMapOf<ArrayType, UMemoryRegion<UHeapRef, USizeSort>>()
    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)

        val resolvedRegion = resolvedInputFields[field]
        val initialValue = regionIdToInitialValue[UInputFieldId(field, sort, null)]

        return when {
            resolvedRegion != null -> resolvedRegion.read(ref).asExpr(sort)
            initialValue != null -> {
                val region = UMemory1DArray<UAddressSort, Sort>(initialValue.cast(), model, addressesMapping)
                resolvedInputFields[field] = region
                region.read(ref)
            }

            else -> sort.sampleValue().mapAddress(addressesMapping)
        }
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
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)

        val key = ref to index

        val resolvedRegion = resolvedInputArrays[arrayType]
        val initialValue = regionIdToInitialValue[UInputArrayId(arrayType, sort, null)]

        return when {
            resolvedRegion != null -> resolvedRegion.read(key).asExpr(sort)
            initialValue != null -> {
                val region = UMemory2DArray<UAddressSort, USizeSort, Sort>(initialValue.cast(), model, addressesMapping)
                resolvedInputArrays[arrayType] = region
                region.read(key)
            }

            else -> sort.sampleValue().mapAddress(addressesMapping)
        }
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)

        val resolvedRegion = resolvedInputLengths[arrayType]
        val sizeSort = ref.uctx.sizeSort
        val initialValue = regionIdToInitialValue[UInputArrayLengthId(arrayType, sizeSort, null)]

        return when {
            resolvedRegion != null -> resolvedRegion.read(ref)
            initialValue != null -> {
                val region = UMemory1DArray<UAddressSort, USizeSort>(initialValue.cast(), model, addressesMapping)
                resolvedInputLengths[arrayType] = region
                region.read(ref)
            }

            else -> sizeSort.sampleValue()
        }
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

    override fun toMutableHeap(): ULazyHeapModel<Field, ArrayType> = this
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