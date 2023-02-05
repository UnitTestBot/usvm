package org.usvm

import java.lang.IllegalArgumentException

interface UMemory<LValue, RValue, SizeT, HeapRef, Type> {
    /**
     * Reads value referenced by [lvalue]. Might lazily initialize symbolic values.
     */
    fun read(lvalue: LValue): RValue

    /**
     * Writes [rvalue] into memory cell referenced by [lvalue].
     */
    fun write(lvalue: LValue, rvalue: RValue)

    /**
     * Allocates dictionary-based structure in heap.
     * @return Concrete heap address of an allocated object.
     */
    fun alloc(type: Type): HeapRef

    /**
     * Allocates array in heap.
     * @return Concrete heap address of an allocated array.
     */
    fun malloc(arrayType: Type, count: SizeT): HeapRef

    /**
     * Optimized writing of many concretely-indexed entries at a time.
     * @param contents Sequence of elements to be written.
     *                 First element will be written to index 0, second -- to index 1, etc.
     */
    fun memset(ref: HeapRef, arrayType: Type, elementSort: USort, contents: Sequence<RValue>)

    /**
     * Copies range of elements [[fromSrc]:[fromSrc] + [length] - 1] from an array with address [src]
     * to range of elements [[fromDst]:[fromDst] + [length] - 1] of array with address [dst].
     * Both arrays must have type [arrayType].
     */
    fun memcpy(src: HeapRef, dst: HeapRef, arrayType: Type, fromSrc: SizeT, fromDst: SizeT, length: SizeT)

    /**
     * Returns length of an array
     */
    fun length(ref: HeapRef, arrayType: Type): SizeT

    fun <Sort: USort> compose(expr: UExpr<Sort>): UExpr<Sort>

    /**
     * Creates new instance of program memory.
     * @warning: symbolic engine may use this operation potentially large amount of times.
     * Implementation must use persistent data structures to speed it up.
     */
    fun clone(): UMemory<LValue, RValue, SizeT, HeapRef, Type>
}

typealias USymbolicMemory<Type> = UMemory<ULValue, UExpr<USort>, USizeExpr, UHeapRef, Type>

open class UMemoryBase<Field, Type, Method>(
    protected val ctx: UContext,
    protected val typeSystem: UTypeSystem<Type>,
    protected var stack: UStack = UStack(ctx),
    protected var heap: USymbolicHeap<Field, Type> = URegionHeap(ctx),
    protected var types: UTypeStorage<Type> = UTypeStorage(ctx, typeSystem),
    protected var mocker: UMocker<Method> = UIndexedMocker(ctx)
    // TODO: we can eliminate mocker by moving compose to UState?
)
    : USymbolicMemory<Type>
{
    @Suppress("UNCHECKED_CAST")
    override fun read(lvalue: ULValue): UExpr<USort> =
        when(lvalue) {
            is URegisterRef -> stack.readRegister(lvalue.idx, lvalue.sort)
            is UFieldRef<*> -> heap.readField(lvalue.ref, lvalue.field as Field, lvalue.sort)
            is UArrayIndexRef<*> -> heap.readArrayIndex(lvalue.ref, lvalue.index, lvalue.arrayType as Type, lvalue.sort)
            else -> throw IllegalArgumentException("Unexpected lvalue $lvalue")
        }

    @Suppress("UNCHECKED_CAST")
    override fun write(lvalue: ULValue, rvalue: UExpr<USort>) {
        when(lvalue) {
            is URegisterRef -> stack.writeRegister(lvalue.idx, rvalue)
            is UFieldRef<*> -> heap.writeField(lvalue.ref, lvalue.field as Field, lvalue.sort, rvalue)
            is UArrayIndexRef<*> -> heap.writeArrayIndex(lvalue.ref, lvalue.index, lvalue.arrayType as Type, lvalue.sort, rvalue)
            else -> throw IllegalArgumentException("Unexpected lvalue $lvalue")
        }
    }

    override fun alloc(type: Type): UHeapRef {
        val address = heap.allocate()
        types.allocate(address, type)
        return UConcreteHeapRef(address, ctx) // TODO: allocate all expr via UContext
    }

    override fun malloc(arrayType: Type, count: USizeExpr): UHeapRef {
        val address = heap.allocateArray(count)
        types.allocate(address, arrayType)
        return UConcreteHeapRef(address, ctx) // TODO: allocate all expr via UContext
    }

    override fun memset(ref: UHeapRef, arrayType: Type, elementSort: USort, contents: Sequence<UExpr<USort>>) =
        heap.memset(ref, arrayType, elementSort, contents)

    override fun memcpy(src: UHeapRef, dst: UHeapRef, arrayType: Type,
                        fromSrc: USizeExpr, fromDst: USizeExpr, length: USizeExpr) =
        heap.memcpy(src, dst, arrayType, fromSrc, fromDst, length)

    override fun length(ref: UHeapRef, arrayType: Type): USizeExpr = heap.readArrayLength(ref, arrayType)

    override fun <Sort: USort> compose(expr: UExpr<Sort>): UExpr<Sort> {
        val composer = UComposer(ctx, stack, heap, types, mocker)
        return composer.compose(expr)
    }

    override fun clone(): USymbolicMemory<Type> =
        UMemoryBase(ctx, typeSystem, stack.clone(), heap, types, mocker)
}
