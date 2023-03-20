package org.usvm

import org.ksmt.utils.asExpr


/**
 * An interface that represents any possible type of regions that can be used in the memory.
 */
interface URegionId<Key> {
    fun <Field, ArrayType, Sort : USort> read(
        key: Key,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
    ): UExpr<Sort>

    fun <Field, ArrayType, Sort : USort> write(
        key: Key,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    fun <Field, ArrayType> keyMapper(composer: UComposer<Field, ArrayType>): KeyMapper<Key>
}

/**
 * A region id for a region storing the specific [field].
 */
data class UInputFieldRegionId<Field> internal constructor(
    val field: Field,
) : URegionId<UHeapRef> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: UHeapRef,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
    ) = heap.readField(key, field as Field, sort).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: UHeapRef,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = heap.writeField(key, field as Field, sort, value, guard)

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>,
    ): KeyMapper<UHeapRef> = { composer.compose(it) }

    override fun toString(): String {
        return "inputField($field)"
    }
}

interface UArrayId<ArrayType, Key> : URegionId<Key> {
    val arrayType: ArrayType
}

/**
 * A region id for a region storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
data class UAllocatedArrayId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    val address: UConcreteHeapAddress,
) : UArrayId<ArrayType, USizeExpr> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: USizeExpr,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
    ): UExpr<Sort> {
        val ref = key.uctx.mkConcreteHeapRef(address)
        return heap.readArrayIndex(ref, key, arrayType as ArrayType, sort).asExpr(sort)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: USizeExpr,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        heap.writeArrayIndex(ref, key, arrayType as ArrayType, sort, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>,
    ): KeyMapper<USizeExpr> = { composer.compose(it) }

    // we don't include arrayType into hashcode and equals, because [address] already defines unambiguously
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayId<*>

        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int {
        return address
    }

    override fun toString(): String {
        return "allocatedArray($address)"
    }
}

/**
 * A region id for a region storing arrays retrieved as a symbolic value, contains only its [arrayType].
 */
data class UInputArrayId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
) : UArrayId<ArrayType, USymbolicArrayIndex> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: USymbolicArrayIndex,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
    ): UExpr<Sort> = heap.readArrayIndex(key.first, key.second, arrayType as ArrayType, sort).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: USymbolicArrayIndex,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = heap.writeArrayIndex(key.first, key.second, arrayType as ArrayType, sort, value, guard)

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>,
    ): KeyMapper<USymbolicArrayIndex> = {
        val ref = composer.compose(it.first)
        val idx = composer.compose(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun toString(): String {
        return "inputArray($arrayType)"
    }
}

/**
 * A region id for a region storing array lengths for arrays of a specific [arrayType].
 */
data class UInputArrayLengthId<ArrayType> internal constructor(
    val arrayType: ArrayType,
) : URegionId<UHeapRef> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: UHeapRef,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
    ): UExpr<Sort> = heap.readArrayLength(key, arrayType as ArrayType).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: UHeapRef,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) {
        assert(guard.isTrue)
        heap.writeArrayLength(key, value.asExpr(key.uctx.sizeSort), arrayType as ArrayType)
    }

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>,
    ): KeyMapper<UHeapRef> = { composer.compose(it) }

    override fun toString(): String {
        return "length($arrayType)"
    }
}
