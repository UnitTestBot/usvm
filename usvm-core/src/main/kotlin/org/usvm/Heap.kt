package org.usvm

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast

interface UReadOnlyHeap<in Ref, Value, SizeT, Field, ArrayType> {
    fun <Sort : USort> readField(ref: Ref, field: Field, sort: Sort): Value
    fun <Sort : USort> readArrayIndex(ref: Ref, index: SizeT, arrayType: ArrayType, elementSort: Sort): Value
    fun readArrayLength(ref: Ref, arrayType: ArrayType): SizeT
}

typealias UReadOnlySymbolicHeap<Field, ArrayType> = UReadOnlyHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType>

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
}

interface UHeap<Ref, Value, SizeT, Field, ArrayType> : UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType> {
    fun <Sort : USort> writeField(ref: Ref, field: Field, sort: Sort, value: Value)
    fun <Sort : USort> writeArrayIndex(ref: Ref, index: SizeT, type: ArrayType, elementSort: Sort, value: Value)

    fun <Sort : USort> memset(ref: Ref, type: ArrayType, sort: Sort, contents: Sequence<Value>)
    fun memcpy(src: Ref, dst: Ref, type: ArrayType, fromSrc: SizeT, fromDst: SizeT, length: SizeT)

    fun allocate(): UHeapAddress
    fun allocateArray(count: SizeT): UHeapAddress

    fun decode(model: KModel): UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType>

    fun clone(): UHeap<Ref, Value, SizeT, Field, ArrayType>
}

typealias USymbolicHeap<Field, ArrayType> = UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType>

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
    private var allocatedFields: PersistentMap<Pair<UHeapAddress, Field>, UExpr<out USort>> = persistentMapOf(),
    private var inputFields: PersistentMap<Field, UVectorMemoryRegion<out USort>> = persistentMapOf(),
    private var allocatedArrays: PersistentMap<UHeapAddress, UAllocatedArrayMemoryRegion<out USort>> = persistentMapOf(),
    private var inputArrays: PersistentMap<ArrayType, UInputArrayMemoryRegion<out USort>> = persistentMapOf(),
    private var allocatedLengths: PersistentMap<UHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputLengths: PersistentMap<ArrayType, UArrayLengthMemoryRegion> = persistentMapOf()
) : USymbolicHeap<Field, ArrayType> {
    private fun <Sort : USort> fieldsRegion(
        field: Field,
        sort: Sort
    ): UMemoryRegion<KExpr<UAddressSort>, Sort> =
        inputFields[field].vectorRegionUncheckedCast()
            ?: emptyFlatRegion(sort, defaultValue = null) { key, region ->
                ctx.mkFieldReading(region, key, field)
            }

    private fun <Sort : USort> allocatedArrayRegion(
        arrayType: ArrayType,
        address: UHeapAddress,
        elementSort: Sort
    ): UMemoryRegion<KExpr<USizeSort>, Sort> =
        allocatedArrays[address]?.allocatedArrayRegionUncheckedCast()
            ?: emptyAllocatedArrayRegion(elementSort) { index, region ->
                ctx.mkAllocatedArrayReading(region, address, index, arrayType, elementSort)
            }

    private fun <Sort : USort> inputArrayRegion(
        arrayType: ArrayType,
        elementSort: Sort
    ): UMemoryRegion<Pair<KExpr<UAddressSort>, KExpr<USizeSort>>, Sort> =
        inputArrays[arrayType]?.inputArrayRegionUncheckedCast()
            ?: emptyInputArrayRegion(elementSort) { pair, region ->
                ctx.mkInputArrayReading(region, pair.first, pair.second, arrayType, elementSort)
            }

    private fun arrayLengthRegion(
        arrayType: ArrayType
    ): UMemoryRegion<KExpr<UAddressSort>, USizeSort> =
        inputLengths[arrayType]
            ?: emptyArrayLengthRegion(ctx) { ref, region ->
                ctx.mkArrayLength(region, ref, arrayType)
            }

    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> =
        when (ref) {
            is UConcreteHeapRef -> allocatedFields[Pair(ref.address, field)]?.cast() ?: sort.defaultValue()
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
            else -> arrayLengthRegion(arrayType).read(ref)
        }

    // TODO: Either prohibit merging concrete and symbolic heap addresses, or fork state by ite-refs here
    override fun <Sort : USort> writeField(ref: UHeapRef, field: Field, sort: Sort, value: UExpr<out USort>) {
        val valueToWrite = value.asExpr(sort)

        when (ref) {
            is UConcreteHeapRef -> {
                allocatedFields = allocatedFields.put(Pair(ref.address, field), valueToWrite)
            }

            else -> {
                val oldRegion = fieldsRegion(field, sort)
                val newRegion = oldRegion.write(ref, valueToWrite)
                inputFields = inputFields.put(field, newRegion)
            }
        }
    }

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        elementSort: Sort,
        value: UExpr<out USort>
    ) {
        val valueToWrite = value.asExpr(elementSort)

        when (ref) {
            is UConcreteHeapRef -> {
                val oldRegion = allocatedArrayRegion(type, ref.address, elementSort)
                val newRegion = oldRegion.write(index, valueToWrite)
                allocatedArrays = allocatedArrays.put(ref.address, newRegion)
            }

            else -> {
                val region = inputArrayRegion(type, elementSort)
                val newRegion = region.write(Pair(ref, index), valueToWrite)
                inputArrays = inputArrays.put(type, newRegion)
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

    override fun memcpy(
        src: UHeapRef,
        dst: UHeapRef,
        type: ArrayType,
        fromSrc: USizeExpr,
        fromDst: USizeExpr,
        length: USizeExpr
    ) {
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

    override fun clone(): UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType> =
        URegionHeap(
            ctx, lastAddress,
            allocatedFields, inputFields,
            allocatedArrays, inputArrays,
            allocatedLengths, inputLengths
        )
}

@Suppress("UNCHECKED_CAST")
fun <Sort : USort> UVectorMemoryRegion<*>?.vectorRegionUncheckedCast(): UVectorMemoryRegion<Sort>? =
    this as? UVectorMemoryRegion<Sort>

@Suppress("UNCHECKED_CAST")
fun <Sort : USort> UAllocatedArrayMemoryRegion<*>?.allocatedArrayRegionUncheckedCast(): UAllocatedArrayMemoryRegion<Sort>? =
    this as? UAllocatedArrayMemoryRegion<Sort>

@Suppress("UNCHECKED_CAST")
fun <Sort : USort> UInputArrayMemoryRegion<*>?.inputArrayRegionUncheckedCast(): UInputArrayMemoryRegion<Sort>? =
    this as? UInputArrayMemoryRegion<Sort>