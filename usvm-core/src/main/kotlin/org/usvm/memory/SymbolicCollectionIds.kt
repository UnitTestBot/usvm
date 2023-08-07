package org.usvm.memory

import io.ksmt.utils.asExpr
import io.ksmt.utils.sampleValue
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.uctx
import org.usvm.util.Region
import org.usvm.util.TrivialRegion

typealias KeyTransformer<Key> = (Key) -> Key
typealias KeyMapper<Key, MappedKey> = (Key) -> MappedKey?

data class DecomposedKey<Key, Sort : USort>(val collectionId: USymbolicCollectionId<Key, Sort, *>, val key: Key)

/**
 * Represents any possible type of symbolic collections that can be used in symbolic memory.
 */
interface USymbolicCollectionId<Key, Sort : USort, out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>>
    : UMemoryRegionId<Sort> {
    override val sort: Sort

    val defaultValue: UExpr<Sort>?

    /**
     * Performs a reading from a [collection] by a [key]. Inheritors use context heap in symbolic collection composition.
     */
    fun instantiate(collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>, key: Key): UExpr<Sort>

    fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    fun <Field, ArrayType> keyMapper(transformer: UExprTransformer<Field, ArrayType>): KeyTransformer<Key>

    fun <Field, ArrayType, MappedKey> keyFilterMapper(
        transformer: UExprTransformer<Field, ArrayType>,
        expectedId: USymbolicCollectionId<MappedKey, Sort, *>
    ): KeyMapper<Key, MappedKey> {
        val mapper = keyMapper(transformer)
        return filter@{
            val transformedKey = mapper(it)
            val decomposedKey = rebindKey(transformedKey)
            if (decomposedKey == null || decomposedKey.collectionId != expectedId)
                return@filter null
            @Suppress("UNCHECKED_CAST")
            return@filter decomposedKey.key as MappedKey
        }
    }

    fun <Field, ArrayType> map(composer: UComposer<Field, ArrayType>): CollectionId

    /**
     * Checks that [key] still belongs to symbolic collection with this id. If yes, then returns null.
     * If [key] belongs to some new memory region, returns lvalue for this new region.
     * The implementation might assume that [key] is obtained by [keyMapper] from some key of symbolic collection with this id.
     */
    fun rebindKey(key: Key): DecomposedKey<*, Sort>?

    /**
     * Returns information about the key of this collection.
     * TODO: pass here context in the form of path constraints here.
     */
    fun keyInfo(): USymbolicCollectionKeyInfo<Key>

    fun <R> accept(visitor: UCollectionIdVisitor<R>): R
}

interface UCollectionIdVisitor<R> {
    fun <Key, Sort : USort, CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> visit(
        collectionId: USymbolicCollectionId<Key, Sort, CollectionId>
    ): Any? =
        error("You must provide visit implementation for ${collectionId::class} in ${this::class}")

    fun <Field, Sort : USort> visit(collectionId: UInputFieldId<Field, Sort>): R

    fun <Field, Sort : USort> visit(collectionId: UAllocatedFieldCollectionId<Field, Sort>): R

    fun <ArrayType, Sort : USort> visit(collectionId: UAllocatedArrayId<ArrayType, Sort>): R

    fun <ArrayType, Sort : USort> visit(collectionId: UInputArrayId<ArrayType, Sort>): R

    fun <ArrayType> visit(collectionId: UInputArrayLengthId<ArrayType>): R

    fun <KeySort : USort, Sort : USort> visit(collectionId: UAllocatedSymbolicMapId<KeySort, Sort>): R

    fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> visit(collectionId: UInputSymbolicMapId<KeySort, Reg, Sort>): R

    fun visit(collectionId: UInputSymbolicMapLengthId): R
}

/**
 * An id for a collection storing the concretely allocated [field] at heap address [address].
 * This id cannot be used directly but can be obtained temporary while mapping [UInputFieldId].
 * See [UInputFieldId.rebindKey]
 */
data class UAllocatedFieldCollectionId<Field, Sort : USort> internal constructor(
    val field: Field,
    val address: UConcreteHeapAddress,
    override val sort: Sort
) : USymbolicCollectionId<Unit, Sort, UAllocatedFieldCollectionId<Field, Sort>> {

    override val defaultValue: UExpr<Sort> =
        sort.sampleValue()

    override fun <Field, ArrayType> keyMapper(transformer: UExprTransformer<Field, ArrayType>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <CField, ArrayType> map(composer: UComposer<CField, ArrayType>): UAllocatedFieldCollectionId<Field, Sort> =
        error("This should not be called")

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit> =
        error("This should not be called")

    override fun <R> accept(visitor: UCollectionIdVisitor<R>) =
        visitor.visit(this)

    override fun rebindKey(key: Unit): DecomposedKey<*, Sort>? =
        null

    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: Unit,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        TODO("Not yet implemented")
    }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedFieldCollectionId<Field, Sort>, Unit, Sort>,
        key: Unit
    ): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return "allocatedField($field)"
    }
}

/**
 * An id for a collection storing the specific [field].
 */
data class UInputFieldId<Field, Sort : USort> internal constructor(
    val field: Field,
    override val sort: Sort,
    val contextHeap: UHeap<Field, *>?,
) : USymbolicCollectionId<UHeapRef, Sort, UInputFieldId<Field, Sort>> {

    override val defaultValue: UExpr<Sort>? get() = null

    override fun instantiate(
        collection: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputFieldReading(collection, key)
    } else {
        collection.applyTo(contextHeap)
        contextHeap.readField(key, field, sort).asExpr(sort)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = heap.writeField(key, field as Field, sort, value, guard)

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <CField, ArrayType> map(composer: UComposer<CField, ArrayType>): UInputFieldId<Field, Sort> {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as UHeap<Field, *>)
    }

    override fun keyInfo(): UHeapRefKeyInfo =
        UHeapRefKeyInfo()

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, Sort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(UAllocatedFieldCollectionId(field, key.address, sort), Unit)
            else -> null
        }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    override fun toString(): String {
        return "inputField($field)"
    }
}

interface UArrayId<ArrayType, Key, Sort : USort, out ArrayId : UArrayId<ArrayType, Key, Sort, ArrayId>> :
    USymbolicCollectionId<Key, Sort, ArrayId> {
    val arrayType: ArrayType
}

interface USymbolicSetId<Element, ElementSort : USort, out SetId : USymbolicSetId<Element, ElementSort, SetId>>
    : USymbolicCollectionId<Element, UBoolSort, SetId>

interface USymbolicMapId<
        Key,
        KeySort : USort,
        ValueSort : USort,
        out KeysSetId : USymbolicSetId<Key, KeySort, KeysSetId>,
        out MapId : USymbolicMapId<Key, KeySort, ValueSort, KeysSetId, MapId>>
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
}

/**
 * A collection id for a collection storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
data class UAllocatedArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    override val defaultValue: UExpr<Sort>,
    val address: UConcreteHeapAddress,
    val contextHeap: UHeap<*, ArrayType>?,
) : UArrayId<ArrayType, USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>> {

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkAllocatedArrayReading(collection, key)
    } else {
        collection.applyTo(contextHeap)
        val ref = key.uctx.mkConcreteHeapRef(address)
        contextHeap.readArrayIndex(ref, key, arrayType, sort).asExpr(sort)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: USizeExpr,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        heap.writeArrayIndex(ref, key, arrayType as ArrayType, sort, value, guard)
    }


    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<USizeExpr> = { transformer.apply(it) }


    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UAllocatedArrayId<ArrayType, Sort> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextHeap == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(
            contextHeap = composer.heapEvaluator.toMutableHeap() as UHeap<*, ArrayType>,
            defaultValue = composedDefaultValue
        )
    }

    override fun keyInfo(): USizeExprKeyInfo =
        USizeExprKeyInfo()

    override fun rebindKey(key: USizeExpr): DecomposedKey<*, Sort>? =
        null

    // we don't include arrayType into hashcode and equals, because [address] already defines unambiguously
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayId<*, *>

        if (address != other.address) return false

        return true
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    override fun hashCode(): Int {
        return address
    }

    override fun toString(): String {
        return "allocatedArray($address)"
    }
}

/**
 * A collection id for a collection storing arrays retrieved as a symbolic value, contains only its [arrayType].
 */
data class UInputArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    val contextHeap: UHeap<*, ArrayType>?,
) : UArrayId<ArrayType, USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>> {
    override val defaultValue: UExpr<Sort>? get() = null
    override fun instantiate(
        collection: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputArrayReading(collection, key.first, key.second)
    } else {
        collection.applyTo(contextHeap)
        contextHeap.readArrayIndex(key.first, key.second, arrayType, sort).asExpr(sort)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: USymbolicArrayIndex,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = heap.writeArrayIndex(key.first, key.second, arrayType as ArrayType, sort, value, guard)

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<USymbolicArrayIndex> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputArrayId<ArrayType, Sort> {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as UHeap<*, ArrayType>)
    }

    override fun keyInfo(): USymbolicArrayIndexKeyInfo =
        USymbolicArrayIndexKeyInfo()

    override fun rebindKey(key: USymbolicArrayIndex): DecomposedKey<*, Sort>? {
        val heapRef = key.first
        return when (heapRef) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedArrayId(
                    arrayType,
                    sort,
                    sort.sampleValue(),
                    heapRef.address,
                    contextHeap
                ), key.second
            )

            else -> null
        }
    }

    override fun toString(): String {
        return "inputArray($arrayType)"
    }
}

/**
 * A collection id for a collection storing array lengths for arrays of a specific [arrayType].
 */
data class UInputArrayLengthId<ArrayType> internal constructor(
    val arrayType: ArrayType,
    override val sort: USizeSort,
    val contextHeap: UHeap<*, ArrayType>?,
) : USymbolicCollectionId<UHeapRef, USizeSort, UInputArrayLengthId<ArrayType>> {
    override val defaultValue: UExpr<USizeSort>? get() = null
    override fun instantiate(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = if (contextHeap == null) {
        sort.uctx.mkInputArrayLengthReading(collection, key)
    } else {
        collection.applyTo(contextHeap)
        contextHeap.readArrayLength(key, arrayType)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        assert(guard.isTrue)
        heap.writeArrayLength(key, value.asExpr(key.uctx.sizeSort), arrayType as ArrayType)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputArrayLengthId<ArrayType> {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as UHeap<*, ArrayType>)
    }

    override fun keyInfo(): UHeapRefKeyInfo =
        UHeapRefKeyInfo()

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    override fun toString(): String {
        return "length($arrayType)"
    }
}

data class UAllocatedSymbolicSetId<Element, ElementSort : USort>()
    : USymbolicSetId<Element, ElementSort, UAllocatedSymbolicSetId<Element, ElementSort>> {
    override val sort: ElementSort
        get() = TODO("Not yet implemented")
    override val defaultValue: UExpr<ElementSort>?
        get() = TODO("Not yet implemented")

    override fun <Field, ArrayType> keyMapper(transformer: UExprTransformer<Field, ArrayType>): KeyTransformer<Element> {
        TODO("Not yet implemented")
    }

    override fun <Field, ArrayType> map(composer: UComposer<Field, ArrayType>): UAllocatedSymbolicSetId<Element, ElementSort> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element> {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, ElementSort>? {
        TODO("Not yet implemented")
    }

    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: Element,
        value: UExpr<ElementSort>,
        guard: UBoolExpr
    ) {
        TODO("Not yet implemented")
    }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedSymbolicSetId<Element, ElementSort>, Element, ElementSort>,
        key: Element
    ): UExpr<ElementSort> {
        TODO("Not yet implemented")
    }

}

data class UAllocatedSymbolicMapId<KeySort : USort, ValueSort : USort> internal constructor(
    override val defaultValue: UExpr<ValueSort>,
    val address: UConcreteHeapAddress,
    val contextHeap: UHeap<*, *>?,
) : USymbolicMapId<UExpr<KeySort>, KeySort, ValueSort, UAllocatedSymbolicSetId<UExpr<KeySort>, KeySort>, UAllocatedSymbolicMapId<KeySort, ValueSort>> {
    override val sort: ValueSort get() = descriptor.valueSort

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedSymbolicMapId<KeySort, Reg, Sort>, UExpr<KeySort>, Sort>,
        key: UExpr<KeySort>
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkAllocatedSymbolicMapReading(collection, key)
    } else {
        collection.applyTo(contextHeap)
        val ref = key.uctx.mkConcreteHeapRef(address)
        contextHeap.readSymbolicMap(descriptor, ref, key).asExpr(sort)
    }

    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: UExpr<KeySort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        heap.writeSymbolicMap(descriptor, ref, key, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<UExpr<KeySort>> = { transformer.apply(it) }


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

    override fun rebindKey(key: UExpr<KeySort>): DecomposedKey<*, Sort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

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

data class UInputSymbolicMapId<KeySort : USort, Reg : Region<Reg>, Sort : USort> internal constructor(
    override val descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
    val contextHeap: UHeap<*, *>?,
) : USymbolicMapId<USymbolicMapKey<KeySort>, KeySort, Reg, Sort, UInputSymbolicMapId<KeySort, Reg, Sort>> {
    override val sort: Sort get() = descriptor.valueSort
    override val defaultValue: UExpr<Sort>? get() = null

    override fun instantiate(
        collection: USymbolicCollection<UInputSymbolicMapId<KeySort, Reg, Sort>, USymbolicMapKey<KeySort>, Sort>,
        key: USymbolicMapKey<KeySort>
    ): UExpr<Sort> = if (contextHeap == null) {
        sort.uctx.mkInputSymbolicMapReading(collection, key.first, key.second)
    } else {
        collection.applyTo(contextHeap)
        contextHeap.readSymbolicMap(descriptor, key.first, key.second).asExpr(sort)
    }

    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: USymbolicMapKey<KeySort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        heap.writeSymbolicMap(descriptor, key.first, key.second, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<USymbolicMapKey<KeySort>> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)


    override fun <Field, CArrayType> map(
        composer: UComposer<Field, CArrayType>
    ): UInputSymbolicMapId<KeySort, Reg, Sort> {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap() as UHeap<*, *>)
    }

    override fun rebindKey(key: USymbolicMapKey<KeySort>): DecomposedKey<*, Sort>? {
        TODO("Not yet implemented")
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
    val contextHeap: UHeap<*, *>?,
) : USymbolicCollectionId<UHeapRef, USizeSort, UInputSymbolicMapLengthId> {
    override val defaultValue: UExpr<USizeSort>? get() = null

    override fun instantiate(
        collection: USymbolicCollection<UInputSymbolicMapLengthId, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = if (contextHeap == null) {
        sort.uctx.mkInputSymbolicMapLengthReading(collection, key)
    } else {
        collection.applyTo(contextHeap)
        contextHeap.readSymbolicMapLength(descriptor, key)
    }

    override fun <Field, ArrayType> write(
        heap: UHeap<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        heap.writeSymbolicMapLength(descriptor, key, value, guard)
    }

    override fun <Field, ArrayType> keyMapper(
        transformer: UExprTransformer<Field, ArrayType>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Field, CArrayType> map(composer: UComposer<Field, CArrayType>): UInputSymbolicMapLengthId {
        check(contextHeap == null) { "contextHeap is not null in composition" }
        return copy(contextHeap = composer.heapEvaluator.toMutableHeap())
    }

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    override fun toString(): String = "length($descriptor)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapLengthId

        return descriptor == other.descriptor
    }

    override fun hashCode(): Int = descriptor.hashCode()
}
