package org.usvm

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.solver.KModel
import org.ksmt.utils.asExpr

interface UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType, Guard> {
    fun <Sort : USort> readField(ref: Ref, field: Field, sort: Sort): Value
    fun <Sort : USort> readArrayIndex(ref: Ref, index: SizeT, arrayType: ArrayType, elementSort: Sort): Value
    fun readArrayLength(ref: Ref, arrayType: ArrayType): SizeT

    /**
     * Returns a copy of the current map to be able to modify it without changing the original one.
     */
    fun toMutableHeap(): UHeap<Ref, Value, SizeT, Field, ArrayType, Guard>
}

typealias UReadOnlySymbolicHeap<Field, ArrayType> = UReadOnlyHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr>

class UEmptyHeap<Field, ArrayType>(private val ctx: UContext) : UReadOnlySymbolicHeap<Field, ArrayType> {
    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> =
        ctx.mkDefault(sort)

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: Sort
    ): UExpr<Sort> = ctx.mkDefault(elementSort)

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType) =
        ctx.zeroSize

    override fun toMutableHeap(): UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr> = URegionHeap(ctx)
}

interface UHeap<Ref, Value, SizeT, Field, ArrayType, Guard> : UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType, Guard> {
    fun <Sort : USort> writeField(ref: Ref, field: Field, sort: Sort, value: Value, guard: Guard)
    fun <Sort : USort> writeArrayIndex(
        ref: Ref,
        index: SizeT,
        type: ArrayType,
        elementSort: Sort,
        value: Value,
        guard: Guard
    )

    fun writeArrayLength(ref: Ref, size: SizeT, arrayType: ArrayType)

    fun <Sort : USort> memset(ref: Ref, type: ArrayType, sort: Sort, contents: Sequence<Value>)
    fun <Sort : USort> memcpy(
        srcRef: Ref,
        dstRef: Ref,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: SizeT,
        fromDstIdx: SizeT,
        toDstIdx: SizeT,
        guard: Guard
    )

    fun allocate(): UConcreteHeapAddress
    fun allocateArray(count: SizeT): UConcreteHeapAddress

    fun decode(model: KModel): UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType, Guard>

    fun clone(): UHeap<Ref, Value, SizeT, Field, ArrayType, Guard>
}

typealias USymbolicHeap<Field, ArrayType> = UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr>

/**
 * Current heap address holder. Calling [freshAddress] advances counter globally.
 * That is, allocation of an object in one state advances counter in all states.
 * This would help to avoid overlapping addresses in merged states.
 * Copying is prohibited.
 */
class UAddressCounter {
    private var lastAddress = nullAddress
    fun freshAddress(): UConcreteHeapAddress = lastAddress++
}

data class URegionHeap<Field, ArrayType>(
    private val ctx: UContext,
    private var lastAddress: UAddressCounter = UAddressCounter(),
    private var allocatedFields: PersistentMap<Pair<UConcreteHeapAddress, Field>, UExpr<out USort>> = persistentMapOf(),
    private var inputFields: PersistentMap<Field, UInputFieldMemoryRegion<Field, out USort>> = persistentMapOf(),
    private var allocatedArrays: PersistentMap<UConcreteHeapAddress, UAllocatedArrayMemoryRegion<ArrayType, out USort>> = persistentMapOf(),
    private var inputArrays: PersistentMap<ArrayType, UInputArrayMemoryRegion<ArrayType, out USort>> = persistentMapOf(),
    private var allocatedLengths: PersistentMap<UConcreteHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputLengths: PersistentMap<ArrayType, UInputArrayLengthMemoryRegion<ArrayType>> = persistentMapOf()
) : USymbolicHeap<Field, ArrayType> {
    private fun <Sort : USort> fieldsRegion(
        field: Field,
        sort: Sort
    ): UInputFieldMemoryRegion<Field, Sort> =
        inputFields[field].inputFieldsRegionUncheckedCast()
            ?: emptyInputFieldRegion(field, sort) { key, region ->
                ctx.mkInputFieldReading(region, key)
            }

    private fun <Sort : USort> allocatedArrayRegion(
        arrayType: ArrayType,
        address: UConcreteHeapAddress,
        elementSort: Sort
    ): UAllocatedArrayMemoryRegion<ArrayType, Sort> =
        allocatedArrays[address].allocatedArrayRegionUncheckedCast()
            ?: emptyAllocatedArrayRegion(arrayType, address, elementSort) { index, region ->
                ctx.mkAllocatedArrayReading(region, index)
            }

    private fun <Sort : USort> inputArrayRegion(
        arrayType: ArrayType,
        elementSort: Sort
    ): UInputArrayMemoryRegion<ArrayType, Sort> =
        inputArrays[arrayType].inputArrayRegionUncheckedCast()
            ?: emptyInputArrayRegion(arrayType, elementSort) { pair, region ->
                ctx.mkInputArrayReading(region, pair.first, pair.second)
            }

    private fun inputArrayLengthRegion(
        arrayType: ArrayType
    ): UInputArrayLengthMemoryRegion<ArrayType> =
        inputLengths[arrayType]
            ?: emptyArrayLengthRegion(arrayType, ctx) { ref, region ->
                ctx.mkInputArrayLength(region, ref)
            }

    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> =
        when (ref) {
            is UConcreteHeapRef -> allocatedFields[Pair(ref.address, field)]?.asExpr(sort) ?: sort.defaultValue()
            else -> fieldsRegion(field, sort).read(ref)
        }

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: Sort
    ): UExpr<Sort> =
        when (ref) {
            is UConcreteHeapRef -> allocatedArrayRegion(arrayType, ref.address, elementSort).read(index)
            else -> inputArrayRegion(arrayType, elementSort).read(Pair(ref, index))
        }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr =
        when (ref) {
            is UConcreteHeapRef -> allocatedLengths[ref.address] ?: ctx.zeroSize
            else -> inputArrayLengthRegion(arrayType).read(ref)
        }

    // TODO: Either prohibit merging concrete and symbolic heap addresses, or fork state by ite-refs here
    override fun <Sort : USort> writeField(
        ref: UHeapRef,
        field: Field,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr
    ) {
        // A write operation that never succeeds
        if (guard.isFalse) return

        val valueToWrite = value.asExpr(sort)

        when (ref) {
            is UConcreteHeapRef -> {
                val key = ref.address to field
                val allocatedFieldValue = if (guard.isTrue) {
                    valueToWrite
                } else {
                    ctx.mkIte(guard, valueToWrite, readField(ref, field, sort))
                }

                allocatedFields = allocatedFields.put(key, allocatedFieldValue)
            }

            else -> {
                val oldRegion = fieldsRegion(field, sort)
                val newRegion = oldRegion.write(ref, valueToWrite, guard)
                inputFields = inputFields.put(field, newRegion)
            }
        }
    }

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        elementSort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr
    ) {
        // A write operation that never succeeds
        if (guard.isFalse) return

        val valueToWrite = value.asExpr(elementSort)

        when (ref) {
            is UConcreteHeapRef -> {
                val oldRegion = allocatedArrayRegion(type, ref.address, elementSort)
                val newRegion = oldRegion.write(index, valueToWrite, guard)
                allocatedArrays = allocatedArrays.put(ref.address, newRegion)
            }

            else -> {
                val region = inputArrayRegion(type, elementSort)
                val newRegion = region.write(ref to index, valueToWrite, guard)
                inputArrays = inputArrays.put(type, newRegion)
            }
        }
    }

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) {
        when (ref) {
            is UConcreteHeapRef -> allocatedLengths = allocatedLengths.put(ref.address, size)
            else -> {
                val region = inputArrayLengthRegion(arrayType)
                val newRegion = region.write(ref, size, ctx.trueExpr)
                inputLengths = inputLengths.put(arrayType, newRegion)
            }
        }
    }

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>
    ) {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr
    ) {
        // A copy operation that never succeeds
        if (guard.isFalse) return

        val src = srcRef to fromSrcIdx
        val dst = dstRef to fromDstIdx

        when (srcRef) {
            is UConcreteHeapRef -> {
                val srcRegion = allocatedArrayRegion(type, srcRef.address, elementSort)

                when (dstRef) {
                    is UConcreteHeapRef -> {
                        val dstRegion = allocatedArrayRegion(type, dstRef.address, elementSort)
                        val keyConverter = UAllocatedToAllocatedKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.memcpy(srcRegion, fromDstIdx, toDstIdx, keyConverter, guard)
                        allocatedArrays = allocatedArrays.put(dstRef.address, newDstRegion)
                    }

                    is UIteExpr -> TODO()
                    else -> {
                        val dstRegion = inputArrayRegion(type, elementSort)
                        val keyConverter = UAllocatedToInputKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.memcpy(srcRegion, dst, dstRef to toDstIdx, keyConverter, guard)
                        inputArrays = inputArrays.put(type, newDstRegion)
                    }
                }
            }

            is UIteExpr -> TODO()

            else -> {
                val srcRegion = inputArrayRegion(type, elementSort)

                when (dstRef) {
                    is UConcreteHeapRef -> {
                        val dstRegion = allocatedArrayRegion(type, dstRef.address, elementSort)
                        val keyConverter = UInputToAllocatedKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.memcpy(srcRegion, fromDstIdx, toDstIdx, keyConverter, guard)
                        allocatedArrays = allocatedArrays.put(dstRef.address, newDstRegion)
                    }

                    is UIteExpr -> TODO()
                    else -> {
                        val dstRegion = inputArrayRegion(type, elementSort)
                        val keyConverter = UInputToInputKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.memcpy(srcRegion, dst, dstRef to toDstIdx, keyConverter, guard)
                        inputArrays = inputArrays.put(type, newDstRegion)
                    }
                }
            }
        }
    }

    override fun allocate() = lastAddress.freshAddress()

    override fun allocateArray(count: USizeExpr): UConcreteHeapAddress {
        val address = lastAddress.freshAddress()
        allocatedLengths = allocatedLengths.put(address, count)
        return address
    }

    override fun decode(model: KModel): UReadOnlySymbolicHeap<Field, ArrayType> {
        TODO("Not yet implemented")
    }

    override fun clone(): UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr> =
        URegionHeap(
            ctx, lastAddress,
            allocatedFields, inputFields,
            allocatedArrays, inputArrays,
            allocatedLengths, inputLengths
        )

    override fun toMutableHeap() = clone()
}

@Suppress("UNCHECKED_CAST")
fun <Field, Sort : USort> UInputFieldMemoryRegion<Field, *>?.inputFieldsRegionUncheckedCast(): UInputFieldMemoryRegion<Field, Sort>? =
    this as? UInputFieldMemoryRegion<Field, Sort>

@Suppress("UNCHECKED_CAST")
fun <ArrayType, Sort : USort> UAllocatedArrayMemoryRegion<ArrayType, *>?.allocatedArrayRegionUncheckedCast(): UAllocatedArrayMemoryRegion<ArrayType, Sort>? =
    this as? UAllocatedArrayMemoryRegion<ArrayType, Sort>

@Suppress("UNCHECKED_CAST")
fun <ArrayType, Sort : USort> UInputArrayMemoryRegion<ArrayType, *>?.inputArrayRegionUncheckedCast(): UInputArrayMemoryRegion<ArrayType, Sort>? =
    this as? UInputArrayMemoryRegion<ArrayType, Sort>
