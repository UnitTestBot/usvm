package org.usvm

import org.ksmt.utils.asExpr

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
    fun memcpy(src: HeapRef, dst: HeapRef, arrayType: Type, elementSort: USort, fromSrc: SizeT, fromDst: SizeT, length: SizeT)

    /**
     * Returns length of an array
     */
    fun length(ref: HeapRef, arrayType: Type): SizeT

    fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort>

    /**
     * Creates new instance of program memory.
     *
     * **Warning**: symbolic engine may use this operation potentially large number of times.
     * Implementation must use persistent data structures to speed it up.
     */
    fun clone(): UMemory<LValue, RValue, SizeT, HeapRef, Type>
}

typealias USymbolicMemory<Type> = UMemory<ULValue, UExpr<out USort>, USizeExpr, UHeapRef, Type>

@Suppress("MemberVisibilityCanBePrivate")
open class UMemoryBase<Field, Type, Method>(
    protected val ctx: UContext,
    val typeSystem: UTypeSystem<Type>,
    val stack: URegistersStack = URegistersStack(ctx),
    val heap: USymbolicHeap<Field, Type> = URegionHeap(ctx),
    val types: UTypeStorage<Type> = UTypeStorage(ctx, typeSystem),
    val mocker: UMocker<Method> = UIndexedMocker(ctx)
    // TODO: we can eliminate mocker by moving compose to UState?
) : USymbolicMemory<Type> {
    @Suppress("UNCHECKED_CAST")
    override fun read(lvalue: ULValue): UExpr<out USort> = with(lvalue) {
        when (this) {
            is URegisterRef -> stack.readRegister(idx, sort)
            is UFieldRef<*> -> heap.readField(ref, field as Field, sort).asExpr(sort)
            is UArrayIndexRef<*> -> heap.readArrayIndex(ref, index, arrayType as Type, sort).asExpr(sort)

            else -> throw IllegalArgumentException("Unexpected lvalue $this")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun write(lvalue: ULValue, rvalue: UExpr<out USort>) = with(lvalue) {
        when (this) {
            is URegisterRef -> stack.writeRegister(idx, rvalue)
            is UFieldRef<*> -> heap.writeField(ref, field as Field, sort, rvalue, guard = ctx.trueExpr)
            is UArrayIndexRef<*> -> {
                heap.writeArrayIndex(ref, index, arrayType as Type, sort, rvalue, guard = ctx.trueExpr)
            }
            else -> throw IllegalArgumentException("Unexpected lvalue $this")
        }
    }

    override fun alloc(type: Type): UHeapRef {
        val address = heap.allocate()
        types.allocate(address, type)
        return ctx.mkConcreteHeapRef(address)
    }

    override fun malloc(arrayType: Type, count: USizeExpr): UHeapRef {
        val address = heap.allocateArray(count)
        types.allocate(address, arrayType)
        return ctx.mkConcreteHeapRef(address)
    }

    override fun memset(ref: UHeapRef, arrayType: Type, elementSort: USort, contents: Sequence<UExpr<out USort>>) =
        heap.memset(ref, arrayType, elementSort, contents)

    override fun memcpy(
        src: UHeapRef, dst: UHeapRef, arrayType: Type, elementSort: USort,
        fromSrc: USizeExpr, fromDst: USizeExpr, length: USizeExpr
    ) = with(src.ctx) {
        val toDst = mkBvAddExpr(fromDst, length)
        heap.memcpy(src, dst, arrayType, elementSort, fromSrc, fromDst, toDst, guard = trueExpr)
    }

    override fun length(ref: UHeapRef, arrayType: Type): USizeExpr = heap.readArrayLength(ref, arrayType)

    override fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> {
        val composer = UComposer(ctx, stack, heap, types, mocker)
        return composer.compose(expr)
    }

    override fun clone(): UMemoryBase<Field, Type, Method> =
        UMemoryBase(ctx, typeSystem, stack.clone(), heap.clone(), types, mocker)
}
