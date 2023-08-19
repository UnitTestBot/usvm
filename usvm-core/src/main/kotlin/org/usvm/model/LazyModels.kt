package org.usvm.model

import io.ksmt.solver.KModel
import io.ksmt.utils.asExpr
import org.usvm.*
import org.usvm.memory.*
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.solver.UExprTranslator


/**
 * A lazy model for registers. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides translated symbolic constants for registers readings.
 */
class ULazyRegistersStackModel(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<*>,
) : UReadOnlyRegistersStack {
    private val uctx = translator.ctx

    override fun <Sort : USort> readRegister(
        index: Int,
        sort: Sort,
    ): UExpr<Sort> {
        val registerReading = uctx.mkRegisterReading(index, sort)
        val translated = translator.translate(registerReading)
        return model.eval(translated, isComplete = true).mapAddress(addressesMapping)
    }
}

/**
 * A lazy model for an indexed mocker. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides translated symbolic constants for mock symbols.
 */
class ULazyIndexedMockModel(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<*>,
) : UMockEvaluator {
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)
        val translated = translator.translate(symbol)
        return model.eval(translated, isComplete = true).mapAddress(addressesMapping)
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
 * Any [UCollectionReading] possibly writing to this heap in its [USymbolicCollectionId.instantiate] call actually has empty updates,
 * because localization happened, so this heap won't be mutated.
 *
 * @param regionIdToInitialValue mapping from [USymbolicCollectionId] to initial values. We decode symbolic collections
 * using this cache.
 * @param model has to be detached.
 */
//class ULazyHeapModel<Field, ArrayType>(
//    private val model: KModel,
//    private val addressesMapping: AddressesMapping,
//    private val regionIdToInitialValue: Map<USymbolicCollectionId<*, *, *>, KExpr<*>>,
//) : UReadOnlyMemory<Field, ArrayType> {
//    private val resolvedInputFields = mutableMapOf<Field, UReadOnlyMemoryRegion<UHeapRef, out USort>>()
//    private val resolvedInputArrays = mutableMapOf<ArrayType, UReadOnlyMemoryRegion<USymbolicArrayIndex, out USort>>()
//    private val resolvedInputLengths = mutableMapOf<ArrayType, UReadOnlyMemoryRegion<UHeapRef, USizeSort>>()
//    private val resolvedInputSymbolicMaps =
//        mutableMapOf<USymbolicMapDescriptor<*, *, *>, UReadOnlyMemoryRegion<USymbolicMapKey<*>, out USort>>()
//    private val resolvedInputSymbolicMapsLengths =
//        mutableMapOf<USymbolicMapDescriptor<*, *, *>, UReadOnlyMemoryRegion<UHeapRef, USizeSort>>()
//
//    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
//        // All the expressions in the model are interpreted, therefore, they must
//        // have concrete addresses. Moreover, the model knows only about input values
//        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
//        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)
//
//        val resolvedRegion = resolvedInputFields[field]
//        val initialValue = regionIdToInitialValue[UInputFieldId(field, sort, null)]
//
//        return when {
//            resolvedRegion != null -> resolvedRegion.read(ref).asExpr(sort)
//            initialValue != null -> {
//                val region = UMemory1DArray<UAddressSort, Sort>(initialValue.cast(), model, addressesMapping)
//                resolvedInputFields[field] = region
//                region.read(ref)
//            }
//
//            else -> sort.sampleValue().mapAddress(addressesMapping)
//        }
//    }
//
//    override fun <Sort : USort> readArrayIndex(
//        ref: UHeapRef,
//        index: USizeExpr,
//        arrayType: ArrayType,
//        sort: Sort,
//    ): UExpr<Sort> {
//        // All the expressions in the model are interpreted, therefore, they must
//        // have concrete addresses. Moreover, the model knows only about input values
//        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
//        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)
//
//        val key = ref to index
//
//        val resolvedRegion = resolvedInputArrays[arrayType]
//        val initialValue = regionIdToInitialValue[UInputArrayId(arrayType, sort, null)]
//
//        return when {
//            resolvedRegion != null -> resolvedRegion.read(key).asExpr(sort)
//            initialValue != null -> {
//                val region = UMemory2DArray<UAddressSort, USizeSort, Sort>(initialValue.cast(), model, addressesMapping)
//                resolvedInputArrays[arrayType] = region
//                region.read(key)
//            }
//
//            else -> sort.sampleValue().mapAddress(addressesMapping)
//        }
//    }
//
//    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
//        // All the expressions in the model are interpreted, therefore, they must
//        // have concrete addresses. Moreover, the model knows only about input values
//        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
//        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)
//
//        val resolvedRegion = resolvedInputLengths[arrayType]
//        val sizeSort = ref.uctx.sizeSort
//        val initialValue = regionIdToInitialValue[UInputArrayLengthId(arrayType, sizeSort, null)]
//
//        return when {
//            resolvedRegion != null -> resolvedRegion.read(ref)
//            initialValue != null -> {
//                val region = UMemory1DArray<UAddressSort, USizeSort>(initialValue.cast(), model, addressesMapping)
//                resolvedInputLengths[arrayType] = region
//                region.read(ref)
//            }
//
//            else -> sizeSort.sampleValue()
//        }
//    }
//
//    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> readSymbolicMap(
//        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
//        ref: UHeapRef,
//        key: UExpr<KeySort>
//    ): UExpr<out USort> {
//        requireInputRef(ref)
//
//        if (key.sort == key.uctx.addressSort) {
//            requireInputRef(key.asExpr(key.uctx.addressSort))
//        }
//
//        val symbolicMapKey = ref to key
//
//        val resolvedRegion = resolvedInputSymbolicMaps[descriptor]
//        val initialValue = regionIdToInitialValue[UInputSymbolicMapId(descriptor, null)]
//
//        return when {
//            resolvedRegion != null -> resolvedRegion.read(symbolicMapKey).asExpr(descriptor.valueSort)
//            initialValue != null -> {
//                val region = UMemory2DArray<UAddressSort, KeySort, Sort>(initialValue.cast(), model, addressesMapping)
//                resolvedInputSymbolicMaps[descriptor] = region.uncheckedCast()
//                region.read(symbolicMapKey)
//            }
//
//            else -> descriptor.valueSort.sampleValue().mapAddress(addressesMapping)
//        }
//    }
//
//    override fun readSymbolicMapLength(descriptor: USymbolicMapDescriptor<*, *, *>, ref: UHeapRef): USizeExpr {
//        requireInputRef(ref)
//
//        val resolvedRegion = resolvedInputSymbolicMapsLengths[descriptor]
//        val sizeSort = ref.uctx.sizeSort
//        val initialValue = regionIdToInitialValue[UInputSymbolicMapLengthId(descriptor, sizeSort, contextHeap = null)]
//
//        return when {
//            resolvedRegion != null -> resolvedRegion.read(ref)
//            initialValue != null -> {
//                val region = UMemory1DArray<UAddressSort, USizeSort>(initialValue.cast(), model, addressesMapping)
//                resolvedInputSymbolicMapsLengths[descriptor] = region
//                region.read(ref)
//            }
//
//            else -> sizeSort.sampleValue()
//        }
//    }
//
//    private fun requireInputRef(ref: UHeapRef) {
//        // All the expressions in the model are interpreted, therefore, they must
//        // have concrete addresses. Moreover, the model knows only about input values
//        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
//        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)
//    }
//
//    override fun <Sort : USort> writeField(
//        ref: UHeapRef,
//        field: Field,
//        sort: Sort,
//        value: UExpr<out USort>,
//        guard: UBoolExpr,
//    ) = error("Illegal operation for a model")
//
//    override fun <Sort : USort> writeArrayIndex(
//        ref: UHeapRef,
//        index: USizeExpr,
//        type: ArrayType,
//        sort: Sort,
//        value: UExpr<out USort>,
//        guard: UBoolExpr,
//    ) = error("Illegal operation for a model")
//
//    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) =
//        error("Illegal operation for a model")
//
//    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> writeSymbolicMap(
//        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
//        ref: UHeapRef,
//        key: UExpr<KeySort>,
//        value: UExpr<out USort>,
//        guard: UBoolExpr
//    ) = error("Illegal operation for a model")
//
//    override fun writeSymbolicMapLength(
//        descriptor: USymbolicMapDescriptor<*, *, *>,
//        ref: UHeapRef,
//        size: USizeExpr,
//        guard: UBoolExpr
//    ) = error("Illegal operation for a model")
//
//    override fun <Sort : USort> memcpy(
//        srcRef: UHeapRef,
//        dstRef: UHeapRef,
//        type: ArrayType,
//        elementSort: Sort,
//        fromSrcIdx: USizeExpr,
//        fromDstIdx: USizeExpr,
//        toDstIdx: USizeExpr,
//        guard: UBoolExpr,
//    ) = error("Illegal operation for a model")
//
//    override fun <Reg : Region<Reg>, Sort : USort> copySymbolicMapIndexRange(
//        descriptor: USymbolicMapDescriptor<USizeSort, Sort, Reg>,
//        srcRef: UHeapRef,
//        dstRef: UHeapRef,
//        fromSrcKey: USizeExpr,
//        fromDstKey: USizeExpr,
//        toDstKey: USizeExpr,
//        guard: UBoolExpr
//    ) = error("Illegal operation for a model")
//
//    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> mergeSymbolicMap(
//        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
//        keyContainsDescriptor: USymbolicMapDescriptor<KeySort, UBoolSort, Reg>,
//        srcRef: UHeapRef,
//        dstRef: UHeapRef,
//        guard: UBoolExpr
//    ) = error("Illegal operation for a model")
//
//    override fun <Sort : USort> memset(
//        ref: UHeapRef,
//        type: ArrayType,
//        sort: Sort,
//        contents: Sequence<UExpr<out USort>>,
//    ) = error("Illegal operation for a model")
//
//    override fun allocate() = error("Illegal operation for a model")
//
//    override fun allocateArray(count: USizeExpr) = error("Illegal operation for a model")
//
//    override fun <Sort : USort> allocateArrayInitialized(
//        type: ArrayType,
//        sort: Sort,
//        contents: Sequence<UExpr<out USort>>
//    ) = error("Illegal operation for a model")
//
//    override fun nullRef(): UConcreteHeapRef = nullRef
//
//    override fun toMutableHeap(): ULazyHeapModel<Field, ArrayType> = this
//}

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