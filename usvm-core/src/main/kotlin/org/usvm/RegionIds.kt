package org.usvm

import org.ksmt.utils.asExpr

/**
 * An interface that represents any possible type of regions that can be used in the memory.
 */
interface URegionId<Key, Sort : USort, out RegionId : URegionId<Key, Sort, RegionId>> {
    val sort: Sort
    val defaultValue: UExpr<Sort>?
    fun instantiate(region: USymbolicMemoryRegion<@UnsafeVariance RegionId, Key, Sort>, key: Key): UExpr<Sort>

    fun <Field, ArrayType> read(
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
        key: Key,
    ): UExpr<Sort>

    fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    fun <Field, ArrayType> keyMapper(transformer: UExprTransformer<Field, ArrayType>): KeyMapper<Key>

    fun <Field, ArrayType> map(composer: UComposer<Field, ArrayType>): RegionId

    fun <R> accept(visitor: URegionIdVisitor<R>): R
}

interface URegionIdVisitor<R> {
    fun <Key, Sort : USort, RegionId : URegionId<Key, Sort, RegionId>> apply(regionId: URegionId<Key, Sort, RegionId>): R =
        regionId.accept(this)

    fun <Key, Sort : USort, RegionId : URegionId<Key, Sort, RegionId>> visit(regionId: URegionId<Key, Sort, RegionId>): Any? =
        error("You must provide visit implementation for ${regionId::class} in ${this::class}")

    fun <Field, Sort : USort> visit(regionId: UInputFieldId<Field, Sort>): R

    fun <ArrayType, Sort : USort> visit(regionId: UAllocatedArrayId<ArrayType, Sort>): R

    fun <ArrayType, Sort : USort> visit(regionId: UInputArrayId<ArrayType, Sort>): R

    fun <ArrayType> visit(regionId: UInputArrayLengthId<ArrayType>): R
}

/**
 * A region id for a region storing the specific [field].
 */
data class UInputFieldId<Field, Sort : USort> internal constructor(
    val field: Field,
    override val sort: Sort,
    val contextHeap: USymbolicHeap<Field, *>?,
) : URegionId<UHeapRef, Sort, UInputFieldId<Field, Sort>> {
    override val defaultValue: UExpr<Sort>? get() = null
    override fun instantiate(
        region: USymbolicMemoryRegion<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputFieldReading(region.copy(regionId = UInputFieldId(field, sort, null)), key)
    } else {
        region.applyTo(contextHeap)
        read(contextHeap, key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> read(
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
        key: UHeapRef,
    ) = heap.readField(key, field as Field, sort).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = heap.writeField(key, field as Field, sort, value, guard)

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<UHeapRef> = { transformer.apply(it) }

    override fun <CField, ArrayType> map(composer: UComposer<CField, ArrayType>): UInputFieldId<Field, Sort> {
        check(contextHeap == null) { "ContextHeap is not null in composition!" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<Field, *>)
    }

    override fun <R> accept(visitor: URegionIdVisitor<R>): R =
        visitor.visit(this)

    override fun toString(): String {
        return "inputField($field)"
    }
}

interface UArrayId<ArrayType, Key, Sort : USort, out RegionId : UArrayId<ArrayType, Key, Sort, RegionId>> :
    URegionId<Key, Sort, RegionId> {
    val arrayType: ArrayType
}

/**
 * A region id for a region storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
data class UAllocatedArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    val address: UConcreteHeapAddress,
    override val sort: Sort,
    override val defaultValue: UExpr<Sort>,
    val contextHeap: USymbolicHeap<*, ArrayType>?,
) : UArrayId<ArrayType, USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>> {
    override fun instantiate(
        region: USymbolicMemoryRegion<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkAllocatedArrayReading(region, key)
    } else {
        region.applyTo(contextHeap)
        read(contextHeap, key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> read(
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
        key: USizeExpr,
    ): UExpr<Sort> {
        val ref = key.uctx.mkConcreteHeapRef(address)
        return heap.readArrayIndex(ref, key, arrayType as ArrayType, sort).asExpr(sort)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: USizeExpr,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        heap.writeArrayIndex(ref, key, arrayType as ArrayType, sort, value, guard)
    }


    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<USizeExpr> = { transformer.apply(it) }


    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UAllocatedArrayId<ArrayType, Sort> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextHeap == null) { "ContextHeap is not null in composition!" }
        @Suppress("UNCHECKED_CAST")
        return copy(
            contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<*, ArrayType>,
            defaultValue = composedDefaultValue
        )
    }

    // we don't include arrayType into hashcode and equals, because [address] already defines unambiguously
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayId<*, *>

        if (address != other.address) return false

        return true
    }

    override fun <R> accept(visitor: URegionIdVisitor<R>): R =
        visitor.visit(this)

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
data class UInputArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    val contextHeap: USymbolicHeap<*, ArrayType>?,
) : UArrayId<ArrayType, USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>> {
    override val defaultValue: UExpr<Sort>? get() = null
    override fun instantiate(
        region: USymbolicMemoryRegion<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputArrayReading(region, key.first, key.second)
    } else {
        region.applyTo(contextHeap)
        read(contextHeap, key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> read(
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
        key: USymbolicArrayIndex,
    ): UExpr<Sort> = heap.readArrayIndex(key.first, key.second, arrayType as ArrayType, sort).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: USymbolicArrayIndex,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = heap.writeArrayIndex(key.first, key.second, arrayType as ArrayType, sort, value, guard)

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<USymbolicArrayIndex> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <R> accept(visitor: URegionIdVisitor<R>): R =
        visitor.visit(this)

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputArrayId<ArrayType, Sort> {
        check(contextHeap == null) { "ContextHeap is not null in composition!" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<*, ArrayType>)
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
    override val sort: USizeSort,
    val contextHeap: USymbolicHeap<*, ArrayType>?,
) : URegionId<UHeapRef, USizeSort, UInputArrayLengthId<ArrayType>> {
    override val defaultValue: UExpr<USizeSort>? get() = null
    override fun instantiate(
        region: USymbolicMemoryRegion<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = if (contextHeap == null) {
        sort.uctx.mkInputArrayLengthReading(region, key)
    } else {
        region.applyTo(contextHeap)
        read(contextHeap, key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> read(
        heap: UReadOnlySymbolicHeap<Field, ArrayType>,
        key: UHeapRef,
    ): UExpr<USizeSort> = heap.readArrayLength(key, arrayType as ArrayType).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        assert(guard.isTrue)
        heap.writeArrayLength(key, value.asExpr(key.uctx.sizeSort), arrayType as ArrayType)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<UHeapRef> = { transformer.apply(it) }

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputArrayLengthId<ArrayType> {
        check(contextHeap == null) { "ContextHeap is not null in composition!" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<*, ArrayType>)
    }
    override fun <R> accept(visitor: URegionIdVisitor<R>): R =
        visitor.visit(this)

    override fun toString(): String {
        return "length($arrayType)"
    }
}
