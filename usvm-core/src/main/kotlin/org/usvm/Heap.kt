package org.usvm

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.solver.KModel

interface UReadOnlyHeap<in Ref, Value, SizeT, Field, ArrayType> {
    fun readField(ref: Ref, field: Field, sort: USort): Value
    fun readArrayIndex(ref: Ref, index: SizeT, arrayType: ArrayType, elementSort: USort): Value
    fun readArrayLength(ref: Ref, arrayType: ArrayType): SizeT
}

typealias UReadOnlySymbolicHeap<Field, ArrayType> = UReadOnlyHeap<UHeapRef, UExpr<USort>, USizeExpr, Field, ArrayType>

class UEmptyHeap<Field, ArrayType>(private val ctx: UContext): UReadOnlySymbolicHeap<Field, ArrayType> {
    override fun readField(ref: UHeapRef, field: Field, sort: USort): UExpr<USort> =
        ctx.mkDefault(sort)

    override fun readArrayIndex(ref: UHeapRef, index: USizeExpr, arrayType: ArrayType, elementSort: USort): UExpr<USort> =
        ctx.mkDefault(elementSort)

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType) =
        ctx.zeroSize
}

interface UHeap<Ref, Value, SizeT, Field, ArrayType>: UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType> {
    fun writeField(ref: Ref, field: Field, sort: USort, value: Value)
    fun writeArrayIndex(ref: Ref, index: SizeT, type: ArrayType, elementSort: USort, value: Value)

    fun memset(ref: Ref, type: ArrayType, sort: USort, contents: Sequence<Value>)
    fun memcpy(src: Ref, dst: Ref, type: ArrayType, fromSrc: SizeT, fromDst: SizeT, length: SizeT)

    fun allocate(): UHeapAddress
    fun allocateArray(count: SizeT): UHeapAddress

    fun decode(model: KModel): UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType>

    fun clone(): UHeap<Ref, Value, SizeT, Field, ArrayType>
}

typealias USymbolicHeap<Field, ArrayType> = UHeap<UHeapRef, UExpr<USort>, USizeExpr, Field, ArrayType>

/**
 * Current heap address holder. Calling [freshAddress] advances counter globally.
 * That is, allocation of an object in one state advances counter in all states.
 * This would help to avoid overlapping addresses in merged states.
 * Copying is prohibited.
 */
class UAddressCounter {
    private var lastAddress = nullAddress
    fun freshAddress(): UHeapAddress = lastAddress++
}

data class URegionHeap<Field, ArrayType>(
    private val ctx: UContext,
    private var lastAddress: UAddressCounter = UAddressCounter(),
    private var allocatedFields: PersistentMap<Pair<UHeapAddress, Field>, UExpr<USort>> = persistentMapOf(),
    private var inputFields: PersistentMap<Field, UVectorMemoryRegion<USort>> = persistentMapOf(),
    private var allocatedArrays: PersistentMap<UHeapAddress, UAllocatedArrayMemoryRegion<USort>> = persistentMapOf(),
    private var inputArrays: PersistentMap<ArrayType, UInputArrayMemoryRegion<USort>> = persistentMapOf(),
    private var allocatedLengths: PersistentMap<UHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputLengths: PersistentMap<ArrayType, UArrayLengthMemoryRegion> = persistentMapOf()
)
    : USymbolicHeap<Field, ArrayType>
{
    private fun fieldsRegion(field: Field, sort: USort) =
        inputFields[field] ?: emptyFlatRegion(sort, null)
            { key, region -> UFieldReading(ctx, region, key, field) }  // TODO: allocate all expr via UContext

    private fun allocatedArrayRegion(arrayType: ArrayType, address: UHeapAddress, elementSort: USort) =
        allocatedArrays[address] ?: emptyAllocatedArrayRegion(elementSort)
            { index, region -> UAllocatedArrayReading(ctx, region, address, index, arrayType) }  // TODO: allocate all expr via UContext

    private fun inputArrayRegion(arrayType: ArrayType, elementSort: USort) =
        inputArrays[arrayType] ?: emptyInputArrayRegion(elementSort)
            { pair, region -> UInputArrayReading(ctx, region, pair.first, pair.second, arrayType) } // TODO: allocate all expr via UContext

    private fun arrayLengthRegion(arrayType: ArrayType) =
        inputLengths[arrayType] ?: emptyArrayLengthRegion(ctx)
            { ref, region -> UArrayLength(ctx, region, ref, arrayType) } // TODO: allocate all expr via UContext

    override fun readField(ref: UHeapRef, field: Field, sort: USort): UExpr<USort> =
        when(ref) {
            is UConcreteHeapRef -> allocatedFields[Pair(ref.address, field)] ?: sort.defaultValue()
            else -> fieldsRegion(field, sort).read(ref)
        }

    override fun readArrayIndex(ref: UHeapRef, index: USizeExpr, arrayType: ArrayType, elementSort: USort): UExpr<USort> =
        when(ref) {
            is UConcreteHeapRef -> allocatedArrayRegion(arrayType, ref.address, elementSort).read(index)
            else -> inputArrayRegion(arrayType, elementSort).read(Pair(ref, index))
        }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr =
        when(ref) {
            is UConcreteHeapRef -> allocatedLengths[ref.address] ?: ctx.zeroSize
            else -> arrayLengthRegion(arrayType).read(ref)
        }

    // TODO: Either prohibit merging concrete and symbolic heap addresses, or fork state by ite-refs here
    override fun writeField(ref: UHeapRef, field: Field, sort: USort, value: UExpr<USort>) =
        when(ref) {
            is UConcreteHeapRef -> {
                allocatedFields = allocatedFields.put(Pair(ref.address, field), value)
            }
            else -> {
                val oldRegion = fieldsRegion(field, sort)
                val newRegion = oldRegion.write(ref, value)
                inputFields = inputFields.put(field, newRegion)
            }
        }

    override fun writeArrayIndex(ref: UHeapRef, index: USizeExpr, type: ArrayType, elementSort: USort, value: UExpr<USort>) =
        when(ref) {
            is UConcreteHeapRef -> {
                val oldRegion = allocatedArrayRegion(type, ref.address, elementSort)
                val newRegion = oldRegion.write(index, value)
                allocatedArrays = allocatedArrays.put(ref.address, newRegion)
            }
            else -> {
                val region = inputArrayRegion(type, elementSort)
                val newRegion = region.write(Pair(ref, index), value)
                inputArrays = inputArrays.put(type, newRegion)
            }
        }

    override fun memset(ref: UHeapRef, type: ArrayType, sort: USort, contents: Sequence<UExpr<USort>>) {
        TODO("Not yet implemented")
    }

    override fun memcpy(src: UHeapRef, dst: UHeapRef, type: ArrayType,
                        fromSrc: USizeExpr, fromDst: USizeExpr, length: USizeExpr) {
        TODO()
    }

    override fun allocate() = lastAddress.freshAddress()

    override fun allocateArray(count: USizeExpr): UHeapAddress {
        val address = lastAddress.freshAddress()
        allocatedLengths = allocatedLengths.put(address, count)
        return address
    }

    override fun decode(model: KModel): UReadOnlySymbolicHeap<Field, ArrayType> {
        TODO("Not yet implemented")
    }

    override fun clone(): UHeap<UHeapRef, UExpr<USort>, USizeExpr, Field, ArrayType> =
        URegionHeap(ctx, lastAddress,
                    allocatedFields, inputFields,
                    allocatedArrays, inputArrays,
                    allocatedLengths, inputLengths)
}
