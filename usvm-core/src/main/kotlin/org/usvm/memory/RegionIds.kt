package org.usvm.memory

import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.model.ULazyHeapModel
import org.usvm.uctx
import org.usvm.util.Region

/**
 * Represents any possible type of regions that can be used in the memory.
 */
interface URegionId<Key, Sort : USort, out RegionId : URegionId<Key, Sort, RegionId>> {
    val sort: Sort

    val defaultValue: UExpr<Sort>?

    /**
     * Performs a reading from a [region] by a [key]. Inheritors uses context heap in memory regions composition.
     */
    fun instantiate(region: USymbolicMemoryRegion<@UnsafeVariance RegionId, Key, Sort>, key: Key): UExpr<Sort>

    fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    fun <Field, ArrayType> keyMapper(transformer: UExprTransformer<Field, ArrayType>): KeyMapper<Key>

    fun <Field, ArrayType> map(composer: UComposer<Field, ArrayType>): RegionId
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
        sort.uctx.mkInputFieldReading(region, key)
    } else {
        region.applyTo(contextHeap)
        contextHeap.readField(key, field, sort).asExpr(sort)
    }

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
        check(contextHeap == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<Field, *>)
    }

    override fun toString(): String {
        return "inputField($field)"
    }
}

sealed interface UArrayId<Key, Sort : USort, out RegionId : UArrayId<Key, Sort, RegionId>> :
    URegionId<Key, Sort, RegionId>

interface UTypedArrayId<ArrayType, Key, Sort : USort, out RegionId : UTypedArrayId<ArrayType, Key, Sort, RegionId>> :
    UArrayId<Key, Sort, RegionId> {
    val arrayType: ArrayType
}

interface USymbolicMapId<Key, KeySort : USort, Reg : Region<Reg>, Sort : USort, out RegionId : USymbolicMapId<Key, KeySort, Reg, Sort, RegionId>> :
    UArrayId<Key, Sort, RegionId> {
    val descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>
}

/**
 * A region id for a region storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
data class UAllocatedArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    override val defaultValue: UExpr<Sort>,
    val address: UConcreteHeapAddress,
    val contextHeap: USymbolicHeap<*, ArrayType>?,
) : UTypedArrayId<ArrayType, USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>> {

    override fun instantiate(
        region: USymbolicMemoryRegion<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkAllocatedArrayReading(region, key)
    } else {
        region.applyTo(contextHeap)
        val ref = key.uctx.mkConcreteHeapRef(address)
        contextHeap.readArrayIndex(ref, key, arrayType, sort).asExpr(sort)
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
        check(contextHeap == null) { "contextHeap is not null in composition" }
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
) : UTypedArrayId<ArrayType, USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>> {
    override val defaultValue: UExpr<Sort>? get() = null
    override fun instantiate(
        region: USymbolicMemoryRegion<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputArrayReading(region, key.first, key.second)
    } else {
        region.applyTo(contextHeap)
        contextHeap.readArrayIndex(key.first, key.second, arrayType, sort).asExpr(sort)
    }

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

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputArrayId<ArrayType, Sort> {
        check(contextHeap == null) { "contextHeap is not null in composition" }
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
        contextHeap.readArrayLength(key, arrayType)
    }

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
        check(contextHeap == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<*, ArrayType>)
    }
    override fun toString(): String {
        return "length($arrayType)"
    }
}

data class UAllocatedSymbolicMapId<KeySort : USort, Reg : Region<Reg>, Sort : USort> internal constructor(
    override val descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
    override val defaultValue: UExpr<Sort>,
    val address: UConcreteHeapAddress,
    val contextHeap: USymbolicHeap<*, *>?,
) : USymbolicMapId<UExpr<KeySort>, KeySort, Reg, Sort, UAllocatedSymbolicMapId<KeySort, Reg, Sort>> {
    override val sort: Sort get() = descriptor.valueSort

    override fun instantiate(
        region: USymbolicMemoryRegion<UAllocatedSymbolicMapId<KeySort, Reg, Sort>, UExpr<KeySort>, Sort>,
        key: UExpr<KeySort>
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkAllocatedSymbolicMapReading(region, key)
    } else {
        region.applyTo(contextHeap)
        val ref = key.uctx.mkConcreteHeapRef(address)
        contextHeap.readSymbolicMap(descriptor, ref, key).asExpr(sort)
    }

    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: UExpr<KeySort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        heap.writeSymbolicMap(descriptor, ref, key, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<UExpr<KeySort>> = { transformer.apply(it) }


    override fun <Field, CArrayType> map(
        composer: UComposer<Field, CArrayType>
    ): UAllocatedSymbolicMapId<KeySort, Reg, Sort> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextHeap == null) { "contextHeap is not null in composition" }
        return copy(
            contextHeap = composer.heapEvaluator.toMutableHeap(),
            defaultValue = composedDefaultValue
        )
    }

    override fun toString(): String = "allocatedMap[$descriptor]($address)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedSymbolicMapId<*, *, *>

        if (address != other.address) return false
        if (descriptor != other.descriptor) return false

        return true
    }

    override fun hashCode(): Int = address * 31 + descriptor.hashCode()
}

interface UInputSymbolicMapUpdatesVisitor<KeySort : USort, ValueSort : USort, Res> {
    fun visitInputValue(): Res

    fun visitBaseValue(value: UExpr<ValueSort>): Res

    fun visitUpdate(previous: Res, key: USymbolicMapKey<KeySort>, value: UExpr<ValueSort>): Res
}

data class UInputSymbolicMapId<KeySort : USort, Reg : Region<Reg>, Sort : USort> internal constructor(
    override val descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
    val contextHeap: USymbolicHeap<*, *>?,
) : USymbolicMapId<USymbolicMapKey<KeySort>, KeySort, Reg, Sort, UInputSymbolicMapId<KeySort, Reg, Sort>> {
    override val sort: Sort get() = descriptor.valueSort
    override val defaultValue: UExpr<Sort>? get() = null

    override fun instantiate(
        region: USymbolicMemoryRegion<UInputSymbolicMapId<KeySort, Reg, Sort>, USymbolicMapKey<KeySort>, Sort>,
        key: USymbolicMapKey<KeySort>
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputSymbolicMapReading(region, key.first, key.second)
    } else {
        region.applyTo(contextHeap)
        contextHeap.readSymbolicMap(descriptor, key.first, key.second).asExpr(sort)
    }

    // todo: remove this hack. maybe add method in UHeap?
    fun <R> visitInputMapUpdates(visitor: UInputSymbolicMapUpdatesVisitor<KeySort, Sort, R>): R {
        if (contextHeap == null) return visitor.visitInputValue()

        when (contextHeap) {
            is ULazyHeapModel<*, *> -> return contextHeap.visitInputMapUpdates(descriptor, visitor)
        }

        TODO("No hack for $contextHeap")
    }

    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: USymbolicMapKey<KeySort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        heap.writeSymbolicMap(descriptor, key.first, key.second, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<USymbolicMapKey<KeySort>> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <Field, CArrayType> map(
        composer: UComposer<Field, CArrayType>
    ): UInputSymbolicMapId<KeySort, Reg, Sort> {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as USymbolicHeap<*, *>)
    }

    override fun toString(): String = "inputMap($descriptor)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapId<*, *, *>

        return descriptor == other.descriptor
    }

    override fun hashCode(): Int = descriptor.hashCode()
}

data class UInputSymbolicMapLengthId internal constructor(
    val descriptor: USymbolicMapDescriptor<*, *, *>,
    override val sort: USizeSort,
    val contextHeap: USymbolicHeap<*, *>?,
) : URegionId<UHeapRef, USizeSort, UInputSymbolicMapLengthId> {
    override val defaultValue: UExpr<USizeSort>? get() = null

    override fun instantiate(
        region: USymbolicMemoryRegion<UInputSymbolicMapLengthId, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = if (contextHeap == null) {
        sort.uctx.mkInputSymbolicMapLengthReading(region, key)
    } else {
        region.applyTo(contextHeap)
        contextHeap.readSymbolicMapLength(descriptor, key)
    }

    override fun <Field, ArrayType> write(
        heap: USymbolicHeap<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        heap.writeSymbolicMapLength(descriptor, key, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyMapper<UHeapRef> = { transformer.apply(it) }

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputSymbolicMapLengthId {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap())
    }

    override fun toString(): String = "length($descriptor)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapLengthId

        return descriptor == other.descriptor
    }

    override fun hashCode(): Int = descriptor.hashCode()
}
