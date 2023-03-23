package org.usvm

import org.ksmt.utils.asExpr
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

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
        elementSort: Sort,
    ): UExpr<Sort> = ctx.mkDefault(elementSort)

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType) =
        ctx.zeroSize

    override fun toMutableHeap(): UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr> =
        URegionHeap(ctx)
}

interface UHeap<Ref, Value, SizeT, Field, ArrayType, Guard> :
    UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType, Guard> {
    fun <Sort : USort> writeField(ref: Ref, field: Field, sort: Sort, value: Value, guard: Guard)
    fun <Sort : USort> writeArrayIndex(
        ref: Ref,
        index: SizeT,
        type: ArrayType,
        elementSort: Sort,
        value: Value,
        guard: Guard,
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
        guard: Guard,
    )

    fun allocate(): UConcreteHeapAddress
    fun allocateArray(count: SizeT): UConcreteHeapAddress

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
    private var lastAddress = 0
    fun freshAddress(): UConcreteHeapAddress = lastAddress++
}

data class URegionHeap<Field, ArrayType>(
    private val ctx: UContext,
    private var lastAddress: UAddressCounter = UAddressCounter(),
    private var allocatedFields: PersistentMap<Pair<UConcreteHeapAddress, Field>, UExpr<out USort>> = persistentMapOf(),
    private var inputFields: PersistentMap<Field, UInputFieldRegion<Field, out USort>> = persistentMapOf(),
    private var allocatedArrays: PersistentMap<UConcreteHeapAddress, UAllocatedArrayRegion<ArrayType, out USort>> = persistentMapOf(),
    private var inputArrays: PersistentMap<ArrayType, UInputArrayRegion<ArrayType, out USort>> = persistentMapOf(),
    private var allocatedLengths: PersistentMap<UConcreteHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputLengths: PersistentMap<ArrayType, UInputArrayLengthRegion<ArrayType>> = persistentMapOf(),
) : USymbolicHeap<Field, ArrayType> {
    private fun <Sort : USort> inputFieldRegion(
        field: Field,
        sort: Sort,
    ): UInputFieldRegion<Field, Sort> =
        inputFields[field].inputFieldsRegionUncheckedCast()
            ?: emptyInputFieldRegion(field, sort) { key, region ->
                ctx.mkInputFieldReading(region, key)
            }

    private fun <Sort : USort> allocatedArrayRegion(
        arrayType: ArrayType,
        address: UConcreteHeapAddress,
        elementSort: Sort,
    ): UAllocatedArrayRegion<ArrayType, Sort> =
        allocatedArrays[address].allocatedArrayRegionUncheckedCast()
            ?: emptyAllocatedArrayRegion(arrayType, address, elementSort) { index, region ->
                ctx.mkAllocatedArrayReading(region, index)
            }

    private fun <Sort : USort> inputArrayRegion(
        arrayType: ArrayType,
        elementSort: Sort,
    ): UInputArrayRegion<ArrayType, Sort> =
        inputArrays[arrayType].inputArrayRegionUncheckedCast()
            ?: emptyInputArrayRegion(arrayType, elementSort) { pair, region ->
                ctx.mkInputArrayReading(region, pair.first, pair.second)
            }

    private fun inputArrayLengthRegion(
        arrayType: ArrayType,
    ): UInputArrayLengthRegion<ArrayType> =
        inputLengths[arrayType]
            ?: emptyArrayLengthRegion(arrayType, ctx) { ref, region ->
                ctx.mkInputArrayLengthReading(region, ref)
            }

    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> =
        ref.map(
            { concreteRef ->
                allocatedFields
                    .getOrDefault(concreteRef.address to field, sort.defaultValue())
                    .asExpr(sort)
            },
            { symbolicRef -> inputFieldRegion(field, sort).read(symbolicRef) }
        )

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: Sort,
    ): UExpr<Sort> =
        ref.map(
            { concreteRef -> allocatedArrayRegion(arrayType, concreteRef.address, elementSort).read(index) },
            { symbolicRef -> inputArrayRegion(arrayType, elementSort).read(symbolicRef to index) }
        )

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr =
        ref.map(
            { concreteRef -> allocatedLengths.getOrDefault(concreteRef.address, ctx.zeroSize) },
            { symbolicRef -> inputArrayLengthRegion(arrayType).read(symbolicRef) }
        )

    override fun <Sort : USort> writeField(
        ref: UHeapRef,
        field: Field,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        val valueToWrite = value.asExpr(sort)

        withHeapRef(
            ref,
            guard,
            { (concreteRef, innerGuard) ->
                val key = concreteRef.address to field

                val oldValue = readField(concreteRef, field, sort)
                val newValue = ctx.mkIte(innerGuard, valueToWrite, oldValue)
                allocatedFields = allocatedFields.put(key, newValue)
            },
            { (symbolicRef, innerGuard) ->
                val oldRegion = inputFieldRegion(field, sort)
                val newRegion = oldRegion.write(symbolicRef, valueToWrite, innerGuard)
                inputFields = inputFields.put(field, newRegion)

            }
        )
    }

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        elementSort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        val valueToWrite = value.asExpr(elementSort)

        withHeapRef(
            ref,
            guard,
            { (concreteRef, innerGuard) ->
                val oldRegion = allocatedArrayRegion(type, concreteRef.address, elementSort)
                val newRegion = oldRegion.write(index, valueToWrite, innerGuard)
                allocatedArrays = allocatedArrays.put(concreteRef.address, newRegion)
            },
            { (symbolicRef, innerGuard) ->
                val region = inputArrayRegion(type, elementSort)
                val newRegion = region.write(symbolicRef to index, valueToWrite, innerGuard)
                inputArrays = inputArrays.put(type, newRegion)
            }
        )
    }

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) {
        withHeapRef(
            ref,
            initialGuard = ctx.trueExpr,
            { (concreteRef, guard) ->
                val oldSize = readArrayLength(ref, arrayType)
                val newSize = ctx.mkIte(guard, size, oldSize)
                allocatedLengths = allocatedLengths.put(concreteRef.address, newSize)
            },
            { (symbolicRef, guard) ->
                val region = inputArrayLengthRegion(arrayType)
                val newRegion = region.write(symbolicRef, size, guard)
                inputLengths = inputLengths.put(arrayType, newRegion)
            }
        )
    }

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
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
        guard: UBoolExpr,
    ) {
        withHeapRef(
            srcRef,
            guard,
            blockOnConcrete = { (srcRef, guard) ->
                val srcRegion = allocatedArrayRegion(type, srcRef.address, elementSort)
                val src = srcRef to fromSrcIdx

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstIdx

                        val dstRegion = allocatedArrayRegion(type, dstRef.address, elementSort)
                        val keyConverter = UAllocatedToAllocatedKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.copyRange(srcRegion, fromDstIdx, toDstIdx, keyConverter, deepGuard)
                        allocatedArrays = allocatedArrays.put(dstRef.address, newDstRegion)
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstIdx

                        val dstRegion = inputArrayRegion(type, elementSort)
                        val keyConverter = UAllocatedToInputKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.copyRange(srcRegion, src, dst, keyConverter, deepGuard)
                        inputArrays = inputArrays.put(type, newDstRegion)
                    },
                )
            },
            blockOnSymbolic = { (srcRef, guard) ->
                val srcRegion = inputArrayRegion(type, elementSort)
                val src = srcRef to fromSrcIdx

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstIdx

                        val dstRegion = allocatedArrayRegion(type, dstRef.address, elementSort)
                        val keyConverter = UInputToAllocatedKeyConverter(src, dst, toDstIdx)
                        val newDstRegion = dstRegion.copyRange(srcRegion, fromDstIdx, toDstIdx, keyConverter, deepGuard)
                        allocatedArrays = allocatedArrays.put(dstRef.address, newDstRegion)
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstIdx

                        val dstRegion = inputArrayRegion(type, elementSort)
                        val keyConverter = UInputToInputKeyConverter(src, dst, toDstIdx)
                        val newDstRegion =
                            dstRegion.copyRange(srcRegion, dst, dstRef to toDstIdx, keyConverter, deepGuard)
                        inputArrays = inputArrays.put(type, newDstRegion)
                    },
                )
            },
        )
    }

    override fun allocate() = lastAddress.freshAddress()

    override fun allocateArray(count: USizeExpr): UConcreteHeapAddress {
        val address = lastAddress.freshAddress()
        allocatedLengths = allocatedLengths.put(address, count)
        return address
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
fun <Field, Sort : USort> UInputFieldRegion<Field, *>?.inputFieldsRegionUncheckedCast(): UInputFieldRegion<Field, Sort>? =
    this as? UInputFieldRegion<Field, Sort>

@Suppress("UNCHECKED_CAST")
fun <ArrayType, Sort : USort> UAllocatedArrayRegion<ArrayType, *>?.allocatedArrayRegionUncheckedCast(): UAllocatedArrayRegion<ArrayType, Sort>? =
    this as? UAllocatedArrayRegion<ArrayType, Sort>

@Suppress("UNCHECKED_CAST")
fun <ArrayType, Sort : USort> UInputArrayRegion<ArrayType, *>?.inputArrayRegionUncheckedCast(): UInputArrayRegion<ArrayType, Sort>? =
    this as? UInputArrayRegion<ArrayType, Sort>
