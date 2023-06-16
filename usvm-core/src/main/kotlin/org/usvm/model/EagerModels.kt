package org.usvm.model

import io.ksmt.utils.asExpr
import io.ksmt.utils.sampleValue
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.memory.UAddressCounter
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.URegistersStackEvaluator
import org.usvm.memory.USymbolicArrayIndex
import org.usvm.memory.USymbolicHeap
import org.usvm.uctx

/**
 * An eager model for registers that stores mapping
 * from mock symbols to evaluated expressions.
 */
class URegistersStackEagerModel(
    private val nullRef: UConcreteHeapRef,
    private val registers: Map<Int, UExpr<out USort>>
) : URegistersStackEvaluator {
    override fun <Sort : USort> readRegister(
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

/**
 * An eager immutable heap model.
 *
 * Declared as mutable heap for using in regions composition in [UComposer]. Any call to
 * modifying operation throws an exception.
 *
 * Any [UHeapReading] possibly writing to this heap in its [URegionId.instantiate] call actually has empty updates,
 * because localization happened, so this heap won't be mutated.
 */
class UHeapEagerModel<Field, ArrayType>(
    private val nullRef: UConcreteHeapRef,
    private val resolvedInputFields: Map<Field, UReadOnlyMemoryRegion<UHeapRef, out USort>>,
    private val resolvedInputArrays: Map<ArrayType, UReadOnlyMemoryRegion<USymbolicArrayIndex, out USort>>,
    private val resolvedInputLengths: Map<ArrayType, UReadOnlyMemoryRegion<UHeapRef, USizeSort>>,
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