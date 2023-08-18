package org.usvm.memory.collections

import io.ksmt.cache.hash
import io.ksmt.utils.asExpr
import io.ksmt.utils.sampleValue
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.toPersistentMap
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
import org.usvm.memory.UArrayIndexRef
import org.usvm.memory.UArrayLengthRef
import org.usvm.memory.UFieldRef
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree

typealias KeyTransformer<Key> = (Key) -> Key
typealias KeyMapper<Key, MappedKey> = (Key) -> MappedKey?

data class DecomposedKey<Key, Sort : USort>(val collectionId: USymbolicCollectionId<Key, Sort, *>, val key: Key)

/**
 * Represents any possible type of symbolic collections that can be used in symbolic memory.
 */
interface USymbolicCollectionId<Key, Sort : USort, out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> {
    //: UMemoryRegionId<Key, Sort> {
    /*override */val sort: Sort

    val defaultValue: UExpr<Sort>?

    /**
     * Performs a reading from a [collection] by a [key]. Inheritors use context heap in symbolic collection composition.
     */
    fun instantiate(collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>, key: Key): UExpr<Sort>

    fun <Type> write(
        memory: UWritableMemory<Type>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    fun <Type> keyMapper(transformer: UExprTransformer<Type>): KeyTransformer<Key>

    fun <Type, MappedKey> keyFilterMapper(
        transformer: UExprTransformer<Type>,
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

    fun <Type> map(composer: UComposer<Type>): CollectionId

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
    fun keyInfo(): USymbolicCollectionKeyInfo<Key, *>

    fun <R> accept(visitor: UCollectionIdVisitor<R>): R
}

interface UCollectionIdVisitor<R> {
    fun <Key, Sort : USort, CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> visit(
        collectionId: USymbolicCollectionId<Key, Sort, CollectionId>
    ): Any? =
        error("You must provide visit implementation for ${collectionId::class} in ${this::class}")

    fun <Field, Sort : USort> visit(collectionId: UInputFieldId<Field, Sort>): R

    fun <Field, Sort : USort> visit(collectionId: UAllocatedFieldId<Field, Sort>): R

    fun <ArrayType, Sort : USort> visit(collectionId: UAllocatedArrayId<ArrayType, Sort>): R

    fun <ArrayType, Sort : USort> visit(collectionId: UInputArrayId<ArrayType, Sort>): R

    fun <ArrayType> visit(collectionId: UInputArrayLengthId<ArrayType>): R

    fun <MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> visit(collectionId: UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>): R

    fun <MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> visit(collectionId: UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>): R

    fun <MapType> visit(collectionId: UInputSymbolicMapLengthId<MapType>): R
}

/**
 * An id for a collection storing the concretely allocated [field] at heap address [address].
 * This id cannot be used directly but can be obtained temporary while mapping [UInputFieldId].
 * See [UInputFieldId.rebindKey]
 */
data class UAllocatedFieldId<Field, Sort : USort> internal constructor(
    val field: Field,
    val address: UConcreteHeapAddress,
    override val sort: Sort
) : USymbolicCollectionId<Unit, Sort, UAllocatedFieldId<Field, Sort>> {

    override val defaultValue: UExpr<Sort> =
        sort.sampleValue()

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: Unit,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) = {
        TODO("Not yet implemented")
    }

    override fun <Type> keyMapper(transformer: UExprTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <Type> map(composer: UComposer<Type>): UAllocatedFieldId<Field, Sort> =
        error("This should not be called")

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> =
        error("This should not be called")

    override fun <R> accept(visitor: UCollectionIdVisitor<R>) =
        visitor.visit(this)

//    override fun emptyRegion(): UMemoryRegion<Unit, Sort> {
//        TODO("Not yet implemented")
//    }

    override fun rebindKey(key: Unit): DecomposedKey<*, Sort>? =
        null

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort>,
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
    val contextMemory: UWritableMemory<*>?,
) : USymbolicCollectionId<UHeapRef, Sort, UInputFieldId<Field, Sort>> {

    override val defaultValue: UExpr<Sort>? get() = null

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<Sort>, guard: UBoolExpr) {
        TODO("Not yet implemented")
    }

    override fun instantiate(
        collection: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef
    ): UExpr<Sort> = if (contextMemory == null) {
        sort.uctx.mkInputFieldReading(collection, key)
    } else {
        collection.applyTo(contextMemory)
        contextMemory.read(UFieldRef(sort, key, field))
    }

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputFieldId<Field, Sort> {
        check(contextMemory == null) { "contextHeap is not null in composition" }
        return copy(contextMemory = composer.memory.toWritableMemory())
    }

    override fun keyInfo() =
        UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, Sort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(UAllocatedFieldId(field, key.address, sort), Unit)
            else -> null
        }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyRegion(): USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String {
        return "inputField($field)"
    }
}

interface USymbolicArrayId<ArrayType, Key, Sort : USort, out ArrayId : USymbolicArrayId<ArrayType, Key, Sort, ArrayId>> :
    USymbolicCollectionId<Key, Sort, ArrayId> {
    val arrayType: ArrayType
}

interface USymbolicSetId<Element, out SetId : USymbolicSetId<Element, SetId>>
    : USymbolicCollectionId<Element, UBoolSort, SetId> {


    fun <Reg: Region<Reg>> defaultRegion(): Reg {
        if (contextMemory == null) {
            return SetRegion.empty<Int>() as Reg
        }
        // TODO: get corresponding collection from contextMemory, recursively eval its region
        return contextMemory.
    }

    /**
     * Returns over-approximation of keys collection set.
     */
    fun <Reg: Region<Reg>> region(updates: USymbolicCollectionUpdates<Element, UBoolSort>): Reg {
        val regionBuilder = SymbolicSetRegionBuilder<Element, Reg>(this)
        @Suppress("UNCHECKED_CAST")
        return updates.accept(regionBuilder, regionCache as MutableMap<Any?, Reg>)
    }


}

private class SymbolicSetRegionBuilder<Key, Reg : Region<Reg>>(
    private val collectionId: USymbolicSetId<Key, *>
) : UMemoryUpdatesVisitor<Key, UBoolSort, Reg> {

    private val keyInfo = collectionId.keyInfo()

    override fun visitSelect(result: Reg, key: Key): UBoolExpr {
        error("Unexpected reading")
    }

    override fun visitInitialValue(): Reg =
        collectionId.defaultRegion()

    override fun visitUpdate(previous: Reg, update: UUpdateNode<Key, UBoolSort>): Reg = when (update) {
        is UPinpointUpdateNode -> {
            // TODO: removed keys
            val keyReg = keyInfo.keyToRegion(update.key)
            previous.union(keyReg.uncheckedCast())
        }

        is URangedUpdateNode<*, *, *, UBoolSort> -> {
            val updatedKeys: Reg = update.adapter.region()
            previous.union(updatedKeys)
        }
    }
}

interface USymbolicMapId<
        Key,
        ValueSort : USort,
        out KeysSetId : USymbolicSetId<Key, KeysSetId>,
        out MapId : USymbolicMapId<Key, ValueSort, KeysSetId, MapId>>
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
    val contextMemory: UWritableMemory<ArrayType>?,
) : USymbolicArrayId<ArrayType, USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>> {

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): UExpr<Sort> = if (contextMemory == null) {
        sort.uctx.mkAllocatedArrayReading(collection, key)
    } else {
        collection.applyTo(contextMemory)
        val ref = key.uctx.mkConcreteHeapRef(address)
        contextMemory.read(UArrayIndexRef(sort, ref, key, arrayType))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USizeExpr,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        memory.write(UArrayIndexRef(sort, ref, key, arrayType), value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<USizeExpr> = { transformer.apply(it) }


    override fun <Type> map(composer: UComposer<Type>): UAllocatedArrayId<ArrayType, Sort> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextMemory == null) { "contextHeap is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(
            contextMemory = composer.memory.toWritableMemory() as UWritableMemory<ArrayType>,
            defaultValue = composedDefaultValue
        )
    }

    override fun keyInfo() =
        USizeExprKeyInfo


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

    fun emptyArray(): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
        val updates = UTreeUpdates<USizeExpr, USizeRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    fun initializedArray(
        content: Map<USizeExpr, UExpr<Sort>>,
        guard: UBoolExpr
    ): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
        val emptyRegionTree = emptyRegionTree<UUpdateNode<USizeExpr, Sort>, USizeRegion>()

        val entries = content.entries.associate { (key, value) ->
            val region = USizeExprKeyInfo.keyToRegion(key)
            val update = UPinpointUpdateNode(key, keyInfo(), value, guard)
            region to (update to emptyRegionTree)
        }

        val updates = UTreeUpdates(
            updates = RegionTree(entries.toPersistentMap()),
            keyInfo()
        )

        return USymbolicCollection(this, updates)
    }


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
    val contextMemory: UWritableMemory<ArrayType>?,
) : USymbolicArrayId<ArrayType, USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>> {
    override val defaultValue: UExpr<Sort>? get() = null
    override fun instantiate(
        collection: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): UExpr<Sort> = if (contextMemory == null) {
        sort.uctx.mkInputArrayReading(collection, key.first, key.second)
    } else {
        collection.applyTo(contextMemory)
        contextMemory.read(UArrayIndexRef(sort, key.first, key.second, arrayType)).asExpr(sort)
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicArrayIndex,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = memory.write(UArrayIndexRef(sort, key.first, key.second, arrayType), value, guard)

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<USymbolicArrayIndex> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyRegion(): USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort> {
        val updates = UTreeUpdates<USymbolicArrayIndex, USymbolicArrayIndexRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayId<ArrayType, Sort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextMemory = composer.memory.toWritableMemory() as UWritableMemory<ArrayType>)
    }

    override fun keyInfo(): USymbolicArrayIndexKeyInfo =
        USymbolicArrayIndexKeyInfo

    override fun rebindKey(key: USymbolicArrayIndex): DecomposedKey<*, Sort>? {
        val heapRef = key.first
        return when (heapRef) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedArrayId(
                    arrayType,
                    sort,
                    sort.sampleValue(),
                    heapRef.address,
                    contextMemory
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
    val contextMemory: UWritableMemory<ArrayType>?,
) : USymbolicCollectionId<UHeapRef, USizeSort, UInputArrayLengthId<ArrayType>> {
    override val defaultValue: UExpr<USizeSort>? get() = null
    override fun instantiate(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = if (contextMemory == null) {
        sort.uctx.mkInputArrayLengthReading(collection, key)
    } else {
        collection.applyTo(contextMemory)
        contextMemory.read(UArrayLengthRef(sort, key, arrayType))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        assert(guard.isTrue)
        memory.write(UArrayLengthRef(sort, key, arrayType), value.asExpr(key.uctx.sizeSort), guard)
    }

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayLengthId<ArrayType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        @Suppress("UNCHECKED_CAST")
        return copy(contextMemory = composer.memory.toWritableMemory() as UWritableMemory<ArrayType>)
    }

    override fun keyInfo() =
        UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyRegion(): USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String {
        return "length($arrayType)"
    }
}

data class UAllocatedSymbolicSetId<Element>() :
    USymbolicSetId<Element, UAllocatedSymbolicSetId<Element>> {
    override val sort: UBoolSort
        get() = TODO("Not yet implemented")
    override val defaultValue: UBoolExpr?
        get() = TODO("Not yet implemented")

    override fun <Type> write(memory: UWritableMemory<Type>, key: Element, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        TODO("Not yet implemented")
    }

    override fun <Type> keyMapper(transformer: UExprTransformer<Type>): KeyTransformer<Element> {
        TODO("Not yet implemented")
    }

    override fun <Type> map(composer: UComposer<Type>): UAllocatedSymbolicSetId<Element> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element, *> {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R {
        TODO("Not yet implemented")
    }

    fun emptyRegion(): UMemoryRegion<Element, UBoolSort> {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, UBoolSort>? {
        TODO("Not yet implemented")
    }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedSymbolicSetId<Element>, Element, UBoolSort>,
        key: Element
    ): UBoolExpr {
        TODO("Not yet implemented")
    }

}

data class UInputSymbolicSetId<Element>() :
    USymbolicSetId<Element, UInputSymbolicSetId<Element>> {
    override val sort: UBoolSort
        get() = TODO("Not yet implemented")
    override val defaultValue: UBoolExpr?
        get() = TODO("Not yet implemented")

    override fun <Type> write(memory: UWritableMemory<Type>, key: Element, value: UExpr<UBoolSort>, guard: UBoolExpr) {
        TODO("Not yet implemented")
    }

    override fun <Type> keyMapper(transformer: UExprTransformer<Type>): KeyTransformer<Element> {
        TODO("Not yet implemented")
    }

    override fun <Type> map(composer: UComposer<Type>): UInputSymbolicSetId<Element> {
        TODO("Not yet implemented")
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Element, *> {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R {
        TODO("Not yet implemented")
    }

    override fun rebindKey(key: Element): DecomposedKey<*, UBoolSort>? {
        TODO("Not yet implemented")
    }

    override fun instantiate(
        collection: USymbolicCollection<UInputSymbolicSetId<Element>, Element, UBoolSort>,
        key: Element
    ): UBoolExpr {
        TODO("Not yet implemented")
    }

}

data class UAllocatedSymbolicMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    override val defaultValue: UExpr<ValueSort>,
    val keySort: KeySort,
    val valueSort: ValueSort,
    val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    val address: UConcreteHeapAddress,
    val contextMemory: UWritableMemory<*, *>?,
) : USymbolicMapId<UExpr<KeySort>, ValueSort, UAllocatedSymbolicSetId<UExpr<KeySort>>, UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>> {
    override val keysSetId: UAllocatedSymbolicSetId<UExpr<KeySort>>
        get() = TODO("Not yet implemented")
    override val sort: ValueSort get() = valueSort

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>
    ): UExpr<ValueSort> = if (contextMemory == null) {
        sort.uctx.mkAllocatedSymbolicMapReading(collection, key)
    } else {
        collection.applyTo(contextMemory)
        val ref = key.uctx.mkConcreteHeapRef(address)
        contextMemory.readSymbolicMap(descriptor, ref, key).asExpr(sort)
    }

    override fun <Field, ArrayType> write(
        memory: UMemory<Field, ArrayType>,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        memory.writeSymbolicMap(descriptor, ref, key, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<UExpr<KeySort>> = { transformer.apply(it) }


    override fun <Type> map(
        composer: UComposer<Type>
    ): UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return copy(
            contextMemory = composer.memory.toWritableMemory(),
            defaultValue = composedDefaultValue
        )
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg> =
        keyInfo

    override fun rebindKey(key: UExpr<KeySort>): DecomposedKey<*, ValueSort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyMap(): USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort> {
        val updates = UTreeUpdates<UExpr<KeySort>, Reg, ValueSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun toString(): String = "allocatedMap<$mapType>($address)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedSymbolicMapId<*, *, *, *>

        if (address != other.address) return false
        if (keySort != other.keySort) return false
        if (valueSort != other.valueSort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int =
        hash(address, keySort, valueSort, mapType)
}

data class UInputSymbolicMapId<MapType, KeySort : USort, ValueSort : USort, Reg: Region<Reg>> internal constructor(
    val keySort: KeySort,
    val valueSort: ValueSort,
    val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    val contextMemory: UMemory<*, *>?,
) : USymbolicMapId<USymbolicMapKey<KeySort>, ValueSort, UInputSymbolicSetId<USymbolicMapKey<KeySort>>, UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>> {
    override val keysSetId: UInputSymbolicSetId<USymbolicMapKey<KeySort>> =
        UInputSymbolicSetId(...)
    override val sort: ValueSort get() = valueSort
    override val defaultValue: UExpr<ValueSort>? get() = null

    override fun instantiate(
        collection: USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>
    ): UExpr<ValueSort> = if (contextMemory == null) {
        sort.uctx.mkInputSymbolicMapReading(collection, key.first, key.second)
    } else {
        collection.applyTo(contextMemory)
        contextMemory.readSymbolicMap(descriptor, key.first, key.second).asExpr(sort)
    }

    override fun <Field, ArrayType> write(
        memory: UMemory<Field, ArrayType>,
        key: USymbolicMapKey<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        memory.writeSymbolicMap(descriptor, key.first, key.second, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<USymbolicMapKey<KeySort>> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyMap(): USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort> {
        @Suppress("RemoveExplicitTypeArguments")
        val updates = UTreeUpdates<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>, ValueSort>(
            updates = emptyRegionTree<UUpdateNode<USymbolicMapKey<KeySort>, ValueSort>, USymbolicMapKeyRegion<Reg>>(),
            keyInfo()
//        keyToRegion = { symbolicMapRefKeyRegion(descriptor, it) },
//        keyRangeToRegion = { k1, k2 -> symbolicMapRefKeyRangeRegion(descriptor, k1, k2) },
//        fullRangeRegion = { descriptor.mkKeyFullRangeRegion() },
        )
        return USymbolicCollection(this, updates)
    }


    override fun <Type> map(
        composer: UComposer<Type>
    ): UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return copy(contextMemory = composer.memory.toWritableMemory() as UMemory<*, *>)
    }

    override fun keyInfo(): USymbolicMapKeyInfo<KeySort, Reg> =
        USymbolicMapKeyInfo(keyInfo)

    override fun rebindKey(key: USymbolicMapKey<KeySort>): DecomposedKey<*, ValueSort>? {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "inputMap(keySort=$keySort, valueSort=$valueSort)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapId<*, *, *, *>

        if (keySort != other.keySort) return false
        if (valueSort != other.valueSort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int =
        hash(keySort, valueSort, mapType)
}

data class UInputSymbolicMapLengthId<MapType> internal constructor(
    val mapType: MapType,
    override val sort: USizeSort,
    val contextMemory: UMemory<*, *>?,
) : USymbolicCollectionId<UHeapRef, USizeSort, UInputSymbolicMapLengthId<MapType>> {
    override val defaultValue: UExpr<USizeSort>? get() = null

    override fun instantiate(
        collection: USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = if (contextMemory == null) {
        sort.uctx.mkInputSymbolicMapLengthReading(collection, key)
    } else {
        collection.applyTo(contextMemory)
        contextMemory.readSymbolicMapLength(key)
    }

    override fun <Field, ArrayType> write(
        memory: UMemory<Field, ArrayType>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        memory.writeSymbolicMapLength(key, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UExprTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputSymbolicMapLengthId<MapType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return copy(contextMemory = composer.memory.toWritableMemory())
    }

    override fun keyInfo() =
        UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyCollection(): USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String = "length<$mapType>"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapLengthId<*>

        return mapType == other.mapType
    }

    override fun hashCode(): Int = mapType.hashCode()
}
