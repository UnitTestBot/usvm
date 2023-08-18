package org.usvm.model

import io.ksmt.utils.asExpr
import io.ksmt.utils.sampleValue
import org.usvm.*
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.URegistersStackEvaluator
import org.usvm.memory.collections.USymbolicArrayIndex
import org.usvm.memory.USymbolicHeap
import org.usvm.memory.USymbolicMapDescriptor
import org.usvm.memory.collections.USymbolicMapKey
import org.usvm.util.Region

/**
 * An eager model for registers that stores mapping
 * from mock symbols to evaluated expressions.
 */
class URegistersStackEagerModel(
    private val nullRef: UConcreteHeapRef,
    private val registers: Map<Int, UExpr<out USort>>
) : URegistersStackEvaluator {
    override fun <Sort : USort> eval(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> = registers
        .getOrElse(registerIndex) { sort.sampleValue().nullAddress(nullRef) } // sampleValue here is important
        .asExpr(sort)
}

/**
 * An eager model for an indexed mocker that stores mapping
 * from mock symbols and invocation indices to expressions.
 */
class UIndexedMockEagerModel<Method>(
    private val nullRef: UConcreteHeapRef,
    private val values: Map<Pair<*, Int>, UExpr<*>>,
) : UMockEvaluator {

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)

        val sort = symbol.sort

        @Suppress("UNCHECKED_CAST")
        val key = symbol.method as Method to symbol.callIndex

        // sampleValue here is important
        return values.getOrDefault(key, sort.sampleValue().nullAddress(nullRef)).asExpr(sort)
    }
}

typealias UReadOnlySymbolicMapAnyRegion = UReadOnlyMemoryRegion<USymbolicMapKey<*>, out USort>
typealias UReadOnlySymbolicMapLengthRegion = UReadOnlyMemoryRegion<UHeapRef, USizeSort>

/**
 * An eager immutable heap model.
 *
 * Declared as mutable heap for using in regions composition in [UComposer]. Any call to
 * modifying operation throws an exception.
 *
 * Any [UCollectionReading] possibly writing to this heap in its [UCollectionId.instantiate] call actually has empty updates,
 * because localization happened, so this heap won't be mutated.
 */
class UHeapEagerModel<Field, ArrayType>(
    private val nullRef: UConcreteHeapRef,
    private val resolvedInputFields: Map<Field, UReadOnlyMemoryRegion<UHeapRef, out USort>>,
    private val resolvedInputArrays: Map<ArrayType, UReadOnlyMemoryRegion<USymbolicArrayIndex, out USort>>,
    private val resolvedInputLengths: Map<ArrayType, UReadOnlyMemoryRegion<UHeapRef, USizeSort>>,
    private val resolvedInputSymbolicMaps: Map<USymbolicMapDescriptor<*, *, *>, UReadOnlySymbolicMapAnyRegion>,
    private val resolvedInputSymbolicMapsLengths: Map<USymbolicMapDescriptor<*, *, *>, UReadOnlySymbolicMapLengthRegion>
) : USymbolicHeap<Field, ArrayType> {
    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)

        @Suppress("UNCHECKED_CAST")
        val region = resolvedInputFields.getOrElse(field) {
            // sampleValue here is important
            UMemory1DArray(sort.sampleValue().nullAddress(nullRef))
        } as UReadOnlyMemoryRegion<UHeapRef, Sort>

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
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)

        val key = ref to index

        @Suppress("UNCHECKED_CAST")
        val region = resolvedInputArrays.getOrElse(arrayType) {
            // sampleValue here is important
            UMemory2DArray(sort.sampleValue().nullAddress(nullRef))
        } as UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort>

        return region.read(key)
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)

        val region = resolvedInputLengths.getOrElse<ArrayType, UReadOnlyMemoryRegion<UHeapRef, USizeSort>>(arrayType) {
            // sampleValue here is important
            UMemory1DArray(ref.uctx.sizeSort.sampleValue())
        }

        return region.read(ref)
    }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> readSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        ref: UHeapRef,
        key: UExpr<KeySort>
    ): UExpr<out USort> {
        requireInputRef(ref)

        if (key.sort == key.uctx.addressSort) {
            requireInputRef(key.asExpr(key.uctx.addressSort))
        }

        val mapKey = ref to key

        val region = resolvedInputSymbolicMaps.getOrElse<_, UReadOnlySymbolicMapAnyRegion>(descriptor) {
            // sampleValue here is important
            val defaultValue = descriptor.valueSort.sampleValue().nullAddress(nullRef)

            @Suppress("UNCHECKED_CAST")
            UMemory2DArray<UAddressSort, KeySort, Sort>(defaultValue) as UReadOnlySymbolicMapAnyRegion
        }

        return region.read(mapKey)
    }

    override fun readSymbolicMapLength(descriptor: USymbolicMapDescriptor<*, *, *>, ref: UHeapRef): USizeExpr {
        requireInputRef(ref)

        val region = resolvedInputSymbolicMapsLengths.getOrElse<_, UReadOnlySymbolicMapLengthRegion>(descriptor) {
            // sampleValue here is important
            UMemory1DArray(ref.uctx.sizeSort.sampleValue())
        }

        return region.read(ref)
    }

    private fun requireInputRef(ref: UHeapRef) {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= UAddressCounter.INITIAL_INPUT_ADDRESS)
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

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> writeSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        ref: UHeapRef,
        key: UExpr<KeySort>,
        value: UExpr<out USort>,
        guard: UBoolExpr
    ) = error("Illegal operation for a model")

    override fun writeSymbolicMapLength(
        descriptor: USymbolicMapDescriptor<*, *, *>,
        ref: UHeapRef,
        size: USizeExpr,
        guard: UBoolExpr
    ) = error("Illegal operation for a model")

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

    override fun <Reg : Region<Reg>, Sort : USort> copySymbolicMapIndexRange(
        descriptor: USymbolicMapDescriptor<USizeSort, Sort, Reg>,
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        fromSrcKey: USizeExpr,
        fromDstKey: USizeExpr,
        toDstKey: USizeExpr,
        guard: UBoolExpr
    ) = error("Illegal operation for a model")

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> mergeSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        keyContainsDescriptor: USymbolicMapDescriptor<KeySort, UBoolSort, Reg>,
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        guard: UBoolExpr
    ) = error("Illegal operation for a model")

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ) = error("Illegal operation for a model")

    override fun allocate() = error("Illegal operation for a model")

    override fun allocateArray(count: USizeExpr) = error("Illegal operation for a model")

    override fun <Sort : USort> allocateArrayInitialized(
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>
    ) = error("Illegal operation for a model")

    override fun nullRef(): UConcreteHeapRef = nullRef

    override fun toMutableHeap(): UHeapEagerModel<Field, ArrayType> = this
}

fun <T : USort> UExpr<T>.nullAddress(nullRef: UConcreteHeapRef): UExpr<T> =
    if (this == uctx.nullRef) {
        nullRef.asExpr(sort)
    } else {
        this
    }