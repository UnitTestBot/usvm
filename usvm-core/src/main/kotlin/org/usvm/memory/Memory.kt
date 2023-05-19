package org.usvm.memory

import io.ksmt.utils.asExpr
import org.usvm.UArrayIndexValue
import org.usvm.UArrayLengthValue
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFieldValue
import org.usvm.UHeapRef
import org.usvm.UIndexedMocker
import org.usvm.ULValue
import org.usvm.UMocker
import org.usvm.URegisterValue
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.constraints.UTypeConstraints
import org.usvm.types.UTypeStream

interface UReadOnlyMemory<LValue, RValue> {
    /**
     * Reads value referenced by [lvalue]. Might lazily initialize symbolic values.
     */
    fun read(lvalue: LValue): RValue
}

interface UMemory<LValue, RValue, SizeT, HeapRef, Type> : UReadOnlyMemory<LValue, RValue> {
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
     * Allocates array in heap.
     * @param contents Sequence of initial array value.
     *                 First element will be written to index 0, second -- to index 1, etc.
     * @return Concrete heap address of an allocated array.
     */
    fun malloc(arrayType: Type, elementSort: USort, contents: Sequence<RValue>): HeapRef

    /**
     * Optimized writing of many concretely-indexed entries at a time.
     * @param contents Sequence of elements to be written.
     *                 First element will be written to index 0, second -- to index 1, etc.
     * Updates the length of the array to the length of [contents].
     */
    fun memset(ref: HeapRef, arrayType: Type, elementSort: USort, contents: Sequence<RValue>)

    /**
     * Copies range of elements [[fromSrc]:[fromSrc] + [length] - 1] from an array with address [src]
     * to range of elements [[fromDst]:[fromDst] + [length] - 1] of array with address [dst].
     * Both arrays must have type [arrayType].
     */
    fun memcpy(
        src: HeapRef,
        dst: HeapRef,
        arrayType: Type,
        elementSort: USort,
        fromSrc: SizeT,
        fromDst: SizeT,
        length: SizeT,
    )
}

typealias UReadOnlySymbolicMemory = UReadOnlyMemory<ULValue, UExpr<out USort>>
typealias USymbolicMemory<Type> = UMemory<ULValue, UExpr<out USort>, USizeExpr, UHeapRef, Type>

@Suppress("MemberVisibilityCanBePrivate")
open class UMemoryBase<Field, Type, Method>(
    protected open val ctx: UContext,
    open val types: UTypeConstraints<Type>,
    open val stack: URegistersStack = URegistersStack(),
    open val heap: USymbolicHeap<Field, Type> = URegionHeap(ctx),
    open val mocker: UMocker<Method> = UIndexedMocker(ctx),
    // TODO: we can eliminate mocker by moving compose to UState?
) : USymbolicMemory<Type> {
    @Suppress("UNCHECKED_CAST")
    override fun read(lvalue: ULValue): UExpr<out USort> = with(lvalue) {
        when (this) {
            is URegisterValue -> stack.readRegister(idx, sort)
            is UFieldValue<*> -> heap.readField(ref, field as Field, sort).asExpr(sort)
            is UArrayIndexValue<*> -> heap.readArrayIndex(ref, index, arrayType as Type, sort).asExpr(sort)
            is UArrayLengthValue<*> -> heap.readArrayLength(ref, arrayType as Type)
            else -> error("Unexpected lvalue $this")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun write(lvalue: ULValue, rvalue: UExpr<out USort>) = with(lvalue) {
        when (this) {
            is URegisterValue -> stack.writeRegister(idx, rvalue)
            is UFieldValue<*> -> heap.writeField(ref, field as Field, sort, rvalue, guard = ctx.trueExpr)
            is UArrayIndexValue<*> -> heap.writeArrayIndex(
                ref,
                index,
                arrayType as Type,
                sort,
                rvalue,
                guard = ctx.trueExpr
            )

            is UArrayLengthValue<*> -> heap.writeArrayLength(ref, rvalue as USizeExpr, arrayType as Type)
            else -> error("Unexpected lvalue $this")
        }
    }

    override fun alloc(type: Type): UHeapRef {
        val concreteHeapRef = heap.allocate()
        types.allocate(concreteHeapRef.address, type)
        return concreteHeapRef
    }

    override fun malloc(arrayType: Type, count: USizeExpr): UHeapRef {
        val concreteHeapRef = heap.allocateArray(count)
        types.allocate(concreteHeapRef.address, arrayType)
        return concreteHeapRef
    }

    override fun malloc(arrayType: Type, elementSort: USort, contents: Sequence<UExpr<out USort>>): UHeapRef {
        val concreteHeapRef = heap.allocateArrayInitialized(arrayType, elementSort, contents)
        types.allocate(concreteHeapRef.address, arrayType)
        return concreteHeapRef
    }

    override fun memset(ref: UHeapRef, arrayType: Type, elementSort: USort, contents: Sequence<UExpr<out USort>>) =
        heap.memset(ref, arrayType, elementSort, contents)

    override fun memcpy(
        src: UHeapRef, dst: UHeapRef, arrayType: Type, elementSort: USort,
        fromSrc: USizeExpr, fromDst: USizeExpr, length: USizeExpr,
    ) = with(src.ctx) {
        val toDst = mkBvAddExpr(fromDst, length)
        heap.memcpy(src, dst, arrayType, elementSort, fromSrc, fromDst, toDst, guard = trueExpr)
    }

    fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> {
        val composer = UComposer(ctx, stack, heap, types, mocker)
        return composer.compose(expr)
    }

    fun clone(typeConstraints: UTypeConstraints<Type>): UMemoryBase<Field, Type, Method> =
        UMemoryBase(ctx, typeConstraints, stack.clone(), heap.toMutableHeap(), mocker)
}
