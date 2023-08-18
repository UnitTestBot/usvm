package org.usvm.memory

import kotlinx.collections.immutable.PersistentMap
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexedMocker
import org.usvm.UMockEvaluator
import org.usvm.UMocker
import org.usvm.USort
import org.usvm.constraints.UTypeConstraints
import org.usvm.constraints.UTypeEvaluator

interface UMemoryRegionId<Key, Sort : USort> {
    val sort: Sort

    fun emptyRegion(): UMemoryRegion<Key, Sort>
}

interface UReadOnlyMemoryRegion<Key, Sort : USort> {
    fun read(key: Key): UExpr<Sort>
}

interface UMemoryRegion<Key, Sort : USort> : UReadOnlyMemoryRegion<Key, Sort> {
    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryRegion<Key, Sort>
}

interface ULValue<Key, Sort : USort> {
    val sort: Sort
    val memoryRegionId: UMemoryRegionId<Key, Sort>
    val key: Key
}

/**
 * Current heap address holder. Calling [freshAddress] advances counter globally.
 * That is, allocation of an object in one state advances counter in all states.
 * This would help to avoid overlapping addresses in merged states.
 * Copying is prohibited.
 */
class UAddressCounter {
    private var lastAddress = INITIAL_CONCRETE_ADDRESS
    fun freshAddress(): UConcreteHeapAddress = lastAddress++

    companion object {
        // We split all addresses into three parts:
        //     * input values: [Int.MIN_VALUE..0),
        //     * null value: [0]
        //     * allocated values: (0..Int.MAX_VALUE]
        const val NULL_ADDRESS = 0
        const val INITIAL_INPUT_ADDRESS = NULL_ADDRESS - 1
        const val INITIAL_CONCRETE_ADDRESS = NULL_ADDRESS + 1
    }
}

interface UReadOnlyMemory<Type> {
    val stack: UReadOnlyRegistersStack
    val mocker: UMockEvaluator
    val types: UTypeEvaluator<Type>
    private fun <Key, Sort : USort> read(regionId: UMemoryRegionId<Key, Sort>, key: Key): UExpr<Sort> {
        val region = getRegion(regionId)
        return region.read(key)
    }

    fun <Key, Sort : USort> read(lvalue: ULValue<Key, Sort>) =
        read(lvalue.memoryRegionId, lvalue.key)

    fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort>

    fun nullRef(): UHeapRef

    fun toWritableMemory(): UWritableMemory<Type>
}

interface UWritableMemory<Type> : UReadOnlyMemory<Type> {
    fun <Key, Sort : USort> setRegion(regionId: UMemoryRegionId<Key, Sort>, newRegion: UMemoryRegion<Key, Sort>)

    fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr)
}

//@Suppress("UNCHECKED_CAST")
//fun <Key, Sort : USort, CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> UReadOnlyMemory<*>.getCollection(
//    collectionId: CollectionId
//): USymbolicCollection<CollectionId, Key, Sort> =
//    getRegion(collectionId) as USymbolicCollection<CollectionId, Key, Sort>

@Suppress("MemberVisibilityCanBePrivate")
class UMemory<Type, Method>(
    protected val ctx: UContext,
    override val types: UTypeConstraints<Type>,
    override val stack: URegistersStack = URegistersStack(ctx),
    override val mocker: UMocker<Method> = UIndexedMocker(ctx),
    private var regions: PersistentMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>>,
    private var lastAddress: UAddressCounter = UAddressCounter(),
) : UWritableMemory<Type> {
//    @Suppress("UNCHECKED_CAST")
//    /*override*/ fun <Sort: USort> read(lvalue: ULValue<Sort>): UExpr<Sort> = with(lvalue) {
//        when (this) {
//            is URegisterRef<Sort> -> stack.readRegister(idx, sort)
//            is UFieldRef<*, Sort> -> heap.readField(ref, field as Field, sort).asExpr(sort)
//            is UArrayIndexRef<*, Sort> -> heap.readArrayIndex(ref, index, arrayType as Type, sort).asExpr(sort)
//
//            else -> throw IllegalArgumentException("Unexpected lvalue $this")
//        }
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    /*override*/ fun <Sort: USort> write (lvalue: ULValue<Sort>, rvalue: UExpr<Sort>) = with(lvalue) {
//        when (this) {
//            is URegisterRef<Sort> -> stack.writeRegister(idx, rvalue)
//            is UFieldRef<*, Sort> -> heap.writeField(ref, field as Field, sort, rvalue, guard = ctx.trueExpr)
//            is UArrayIndexRef<*, Sort> -> {
//                heap.writeArrayIndex(ref, index, arrayType as Type, sort, rvalue, guard = ctx.trueExpr)
//            }
//            else -> throw IllegalArgumentException("Unexpected lvalue $this")
//        }
//    }

//    /*override*/ fun alloc(type: Type): UHeapRef {
//        val concreteHeapRef = heap.allocate()
//        types.allocate(concreteHeapRef.address, type)
//        return concreteHeapRef
//    }
//
//    /*override*/ fun malloc(arrayType: Type, count: USizeExpr): UHeapRef {
//        val concreteHeapRef = heap.allocateArray(count)
//        types.allocate(concreteHeapRef.address, arrayType)
//        return concreteHeapRef
//    }
//
//    /*override*/ fun <Sort: USort> malloc(arrayType: Type, elementSort: Sort, contents: Sequence<UExpr<Sort>>): UHeapRef {
//        val concreteHeapRef = heap.allocateArrayInitialized(arrayType, elementSort, contents)
//        types.allocate(concreteHeapRef.address, arrayType)
//        return concreteHeapRef
//    }
//
//    /*override*/ fun <Sort: USort> memset(ref: UHeapRef, arrayType: Type, elementSort: Sort, contents: Sequence<UExpr<Sort>>) =
//        heap.memset(ref, arrayType, elementSort, contents)
//
//    /*override*/ fun memcpy(
//        src: UHeapRef, dst: UHeapRef, arrayType: Type, elementSort: USort,
//        fromSrc: USizeExpr, fromDst: USizeExpr, length: USizeExpr
//    ) = with(src.ctx) {
//        val toDst = mkBvAddExpr(fromDst, length)
//        heap.memcpy(src, dst, arrayType, elementSort, fromSrc, fromDst, toDst, guard = trueExpr)
//    }
//
//    /*override*/ fun length(ref: UHeapRef, arrayType: Type): USizeExpr = heap.readArrayLength(ref, arrayType)
//

    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UMemoryRegion<Key, Sort> =
        regions.getOrElse(regionId) { regionId.emptyRegion() } as UMemoryRegion<Key, Sort>

    override fun <Key, Sort : USort> setRegion(regionId: UMemoryRegionId<Key, Sort>, newRegion: UMemoryRegion<Key, Sort>) {
        regions = regions.put(regionId, newRegion)
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) =
        write(lvalue.memoryRegionId, lvalue.key, rvalue, guard)

    private fun <Key, Sort : USort> write(
        regionId: UMemoryRegionId<Key, Sort>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        val region = getRegion(regionId)
        val newRegion = region.write(key, value, guard)
        setRegion(regionId, newRegion)
    }

    fun freshAddress(type: Type): UConcreteHeapRef {
        val concreteHeapRef = ctx.mkConcreteHeapRef(lastAddress.freshAddress())
        types.allocate(concreteHeapRef.address, type)
        return concreteHeapRef
    }

    override fun nullRef(): UHeapRef =
        ctx.nullRef

//    fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> {
//        val composer = UComposer(ctx, this)
//        return composer.compose(expr)
//    }

    fun clone(typeConstraints: UTypeConstraints<Type>): UMemory<Type, Method> =
        UMemory(ctx, typeConstraints, stack.clone(), mocker, regions)

    override fun toWritableMemory() =
        // To be perfectly rigorous, we should clone stack and types here.
        // But in fact they should not be used, so to optimize things up, we don't touch them.
        UMemory(ctx, types, stack, mocker, regions)
}

//interface UMemory<LValue, RValue, SizeT, HeapRef, Type> {
//    /**
//     * Reads value referenced by [lvalue]. Might lazily initialize symbolic values.
//     */
//    fun read(lvalue: LValue): RValue
//
//    /**
//     * Writes [rvalue] into memory cell referenced by [lvalue].
//     */
//    fun write(lvalue: LValue, rvalue: RValue)
//
//    /**
//     * Allocates dictionary-based structure in heap.
//     * @return Concrete heap address of an allocated object.
//     */
//    fun alloc(type: Type): HeapRef
//
//    /**
//     * Allocates array in heap.
//     * @return Concrete heap address of an allocated array.
//     */
//    fun malloc(arrayType: Type, count: SizeT): HeapRef
//
//    /**
//     * Allocates array in heap.
//     * @param contents Sequence of initial array value.
//     *                 First element will be written to index 0, second -- to index 1, etc.
//     * @return Concrete heap address of an allocated array.
//     */
//    fun malloc(arrayType: Type, elementSort: USort, contents: Sequence<RValue>): HeapRef
//
//    /**
//     * Optimized writing of many concretely-indexed entries at a time.
//     * @param contents Sequence of elements to be written.
//     *                 First element will be written to index 0, second -- to index 1, etc.
//     * Updates the length of the array to the length of [contents].
//     */
//    fun memset(ref: HeapRef, arrayType: Type, elementSort: USort, contents: Sequence<RValue>)
//
//    /**
//     * Copies range of elements [[fromSrc]:[fromSrc] + [length] - 1] from an array with address [src]
//     * to range of elements [[fromDst]:[fromDst] + [length] - 1] of array with address [dst].
//     * Both arrays must have type [arrayType].
//     */
//    fun memcpy(src: HeapRef, dst: HeapRef, arrayType: Type, elementSort: USort, fromSrc: SizeT, fromDst: SizeT, length: SizeT)
//
//    /**
//     * Returns length of an array
//     */
//    fun length(ref: HeapRef, arrayType: Type): SizeT
//
//    fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort>
//}
//
//open class ULValue<Sort: USort>(val sort: USort)
//
//typealias USymbolicMemory<Type> = UMemory<ULValue<out USort>, UExpr<out USort>, USizeExpr, UHeapRef, Type>
//
//@Suppress("MemberVisibilityCanBePrivate")
//open class UMemoryBase<Field, Type, Method>(
//    protected val ctx: UContext,
//    val types: UTypeConstraints<Type>,
//    val stack: URegistersStack = URegistersStack(ctx),
//    val heap: USymbolicHeap<Field, Type> = URegionHeap(ctx),
//    val mocker: UMocker<Method> = UIndexedMocker(ctx)
//    // TODO: we can eliminate mocker by moving compose to UState?
//) : USymbolicMemory<Type> {
//    @Suppress("UNCHECKED_CAST")
//    override fun <Sort: USort> read(lvalue: ULValue<Sort>): UExpr<Sort> = with(lvalue) {
//        when (this) {
//            is URegisterRef<Sort> -> stack.readRegister(idx, sort)
//            is UFieldRef<*, Sort> -> heap.readField(ref, field as Field, sort).asExpr(sort)
//            is UArrayIndexRef<*, Sort> -> heap.readArrayIndex(ref, index, arrayType as Type, sort).asExpr(sort)
//
//            else -> throw IllegalArgumentException("Unexpected lvalue $this")
//        }
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Sort: USort> write (lvalue: ULValue<Sort>, rvalue: UExpr<Sort>) = with(lvalue) {
//        when (this) {
//            is URegisterRef<Sort> -> stack.writeRegister(idx, rvalue)
//            is UFieldRef<*, Sort> -> heap.writeField(ref, field as Field, sort, rvalue, guard = ctx.trueExpr)
//            is UArrayIndexRef<*, Sort> -> {
//                heap.writeArrayIndex(ref, index, arrayType as Type, sort, rvalue, guard = ctx.trueExpr)
//            }
//            else -> throw IllegalArgumentException("Unexpected lvalue $this")
//        }
//    }
//
//    override fun alloc(type: Type): UHeapRef {
//        val concreteHeapRef = heap.allocate()
//        types.allocate(concreteHeapRef.address, type)
//        return concreteHeapRef
//    }
//
//    override fun malloc(arrayType: Type, count: USizeExpr): UHeapRef {
//        val concreteHeapRef = heap.allocateArray(count)
//        types.allocate(concreteHeapRef.address, arrayType)
//        return concreteHeapRef
//    }
//
//    override fun malloc(arrayType: Type, elementSort: USort, contents: Sequence<UExpr<out USort>>): UHeapRef {
//        val concreteHeapRef = heap.allocateArrayInitialized(arrayType, elementSort, contents)
//        types.allocate(concreteHeapRef.address, arrayType)
//        return concreteHeapRef
//    }
//
//    override fun memset(ref: UHeapRef, arrayType: Type, elementSort: USort, contents: Sequence<UExpr<out USort>>) =
//        heap.memset(ref, arrayType, elementSort, contents)
//
//    override fun memcpy(
//        src: UHeapRef, dst: UHeapRef, arrayType: Type, elementSort: USort,
//        fromSrc: USizeExpr, fromDst: USizeExpr, length: USizeExpr
//    ) = with(src.ctx) {
//        val toDst = mkBvAddExpr(fromDst, length)
//        heap.memcpy(src, dst, arrayType, elementSort, fromSrc, fromDst, toDst, guard = trueExpr)
//    }
//
//    override fun length(ref: UHeapRef, arrayType: Type): USizeExpr = heap.readArrayLength(ref, arrayType)
//
//    override fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> {
//        val composer = UComposer(ctx, stack, heap, types, mocker)
//        return composer.compose(expr)
//    }
//
//    fun clone(typeConstraints: UTypeConstraints<Type>): UMemoryBase<Field, Type, Method> =
//        UMemoryBase(ctx, typeConstraints, stack.clone(), heap.toMutableHeap(), mocker)
//}
