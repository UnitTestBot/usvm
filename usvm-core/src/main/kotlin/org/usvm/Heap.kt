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

    fun memset(ref: Ref, type: ArrayType, sort: USort, contents: Iterable<Value>)
    fun memcpy(src: Ref, dst: Ref, type: ArrayType, fromSrc: SizeT, fromDst: SizeT, length: SizeT)

    fun allocate(): UHeapAddress
    fun allocateArray(count: SizeT): UHeapAddress

    fun decode(model: KModel): UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType>
}

typealias USymbolicHeap<Field, ArrayType> = UHeap<UHeapRef, UExpr<USort>, USizeExpr, Field, ArrayType>

/**
 * Current heap address holder. Calling @freshAddress advances counter globally.
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
    var allocatedFields: PersistentMap<Pair<UHeapAddress, Field>, UExpr<USort>> = persistentMapOf(),
    var inputFields: PersistentMap<Field, UVectorMemoryRegion> = persistentMapOf(),
    var allocatedArrays: PersistentMap<UHeapAddress, UVectorMemoryRegion> = persistentMapOf(),
    var inputArrays: PersistentMap<ArrayType, UArrayMemoryRegion> = persistentMapOf(),
    var allocatedLengths: PersistentMap<UHeapAddress, USizeExpr> = persistentMapOf(),
    var inputLengths: PersistentMap<ArrayType, UArrayLengthMemoryRegion> = persistentMapOf()
)
    : USymbolicHeap<Field, ArrayType>
{
    override fun readField(ref: UHeapRef, field: Field, sort: USort): UExpr<USort> =
        when(ref) {
            is UConcreteHeapRef ->
                allocatedFields[Pair(ref.address, field)] ?: sort.defaultValue()
            else -> {
                val fieldsRegion = inputFields[field] ?: emptyRegion(sort)
                fieldsRegion.read(UHeapAddressKey(ref)) { UFieldReading(ctx, it, ref, field) } // TODO: allocate all expr via UContext
            }
        }

    override fun readArrayIndex(ref: UHeapRef, index: USizeExpr, arrayType: ArrayType, elementSort: USort): UExpr<USort> =
        when(ref) {
            is UConcreteHeapRef -> {
                val region = allocatedArrays[ref.address] ?: emptyRegion(elementSort)
                region.read(UHeapAddressKey(ref)) { elementSort.defaultValue() }
            }
            else -> {
                val region = inputArrays[arrayType] ?: emptyRegion(elementSort)
                region.read(UArrayIndexKey(ref, index)) { UArrayIndexReading(ctx, it, ref, index, arrayType) } // TODO: allocate all expr via UContext
            }
        }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr =
        when(ref) {
            is UConcreteHeapRef -> allocatedLengths[ref.address] ?: ctx.zeroSize
            else -> {
                val region = inputLengths[arrayType] ?: emptyRegion(ctx.sizeSort)
                region.read(UHeapAddressKey(ref)) { UArrayLength(ctx, it, ref, arrayType) } // TODO: allocate all expr via UContext
            }
        }

    // TODO: Either prohibit merging concrete and symbolic heap addresses, or fork state by ite-refs here
    override fun writeField(ref: UHeapRef, field: Field, sort: USort, value: UExpr<USort>) =
        when(ref) {
            is UConcreteHeapRef -> {
                allocatedFields = allocatedFields.put(Pair(ref.address, field), value)
            }
            else -> {
                val oldRegion = inputFields[field] ?: emptyRegion(sort)
                val newRegion = oldRegion.write(UHeapAddressKey(ref), value)
                inputFields = inputFields.put(field, newRegion)
            }
        }

    override fun writeArrayIndex(ref: UHeapRef, index: USizeExpr, type: ArrayType, elementSort: USort, value: UExpr<USort>) =
        when(ref) {
            is UConcreteHeapRef -> {
                val oldRegion = allocatedArrays[ref.address] ?: emptyRegion(elementSort)
                val newRegion = oldRegion.write(UHeapAddressKey(ref), value)
                allocatedArrays = allocatedArrays.put(ref.address, newRegion)
            }
            else -> {
                val region = inputArrays[type] ?: emptyRegion(elementSort)
                val newRegion = region.write(UArrayIndexKey(ref, index), value)
                inputArrays = inputArrays.put(type, newRegion)
            }
        }

    override fun memset(ref: UHeapRef, type: ArrayType, sort: USort, contents: Iterable<UExpr<USort>>) {
        TODO("Not yet implemented")
    }

    override fun memcpy(
        src: UHeapRef,
        dst: UHeapRef,
        type: ArrayType,
        fromSrc: USizeExpr,
        fromDst: USizeExpr,
        length: USizeExpr
    ) {
        TODO("Discuss copying and implement ranged memory keys")
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
}
