package org.usvm.memory

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.memory.collections.UAllocatedArrayId
import org.usvm.memory.collections.UAllocatedFieldId
import org.usvm.memory.collections.UAllocatedSymbolicMapId
import org.usvm.memory.collections.UHeapRefKeyInfo
import org.usvm.memory.collections.UInputArrayId
import org.usvm.memory.collections.UInputArrayLengthId
import org.usvm.memory.collections.UInputFieldId
import org.usvm.memory.collections.UInputSymbolicMapId
import org.usvm.memory.collections.UInputSymbolicMapLengthId
import org.usvm.memory.collections.USizeExprKeyInfo
import org.usvm.memory.collections.USymbolicArrayIndex
import org.usvm.memory.collections.USymbolicArrayIndexKeyInfo
import org.usvm.memory.collections.USymbolicCollection
import org.usvm.memory.collections.USymbolicCollectionKeyInfo
import org.usvm.memory.collections.USymbolicMapKey
import org.usvm.sampleUValue
import org.usvm.uctx
import org.usvm.util.Region

//region Fields

data class UFieldRef<Field, Sort : USort>(override val sort: Sort, val ref: UHeapRef, val field: Field) :
    ULValue<UFieldRef<Field, Sort>, Sort> {
    override val memoryRegionId: UMemoryRegionId<UFieldRef<Field, Sort>, Sort> =
        UFieldsRegionId(field, sort)

    override val key: UFieldRef<Field, Sort> = this
}

data class UFieldsRegionId<Field, Sort : USort>(val field: Field, override val sort: Sort) :
    UMemoryRegionId<UFieldRef<Field, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UFieldRef<Field, Sort>, Sort> =
        UFieldsRegion()
}

typealias UAllocatedFields<Field, Sort> = PersistentMap<UAllocatedFieldId<Field, Sort>, UExpr<Sort>>
typealias UInputFields<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>

class UFieldsRegion<Field, Sort : USort>(
    private val allocatedFields: UAllocatedFields<Field, Sort> = persistentMapOf(),
    private var inputFields: UInputFields<Field, Sort>? = null
) : UMemoryRegion<UFieldRef<Field, Sort>, Sort> {

    private fun readAllocated(address: UConcreteHeapAddress, field: Field, sort: Sort) =
        allocatedFields
            .get(UAllocatedFieldId(field, address, sort)) ?: sort.sampleUValue() // sampleUValue is important
            .asExpr(sort)

    private fun getInputFields(ref: UFieldRef<Field, Sort>): UInputFields<Field, Sort> {
        if (inputFields == null)
            inputFields = UInputFieldId(ref.field, ref.sort, null).emptyRegion()
        return inputFields!!
    }

    override fun read(key: UFieldRef<Field, Sort>): UExpr<Sort> =
        key.ref.map(
            { concreteRef -> readAllocated(concreteRef.address, key.field, key.sort) },
            { symbolicRef -> getInputFields(key).read(symbolicRef) }
        )

    override fun write(
        key: UFieldRef<Field, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UFieldRef<Field, Sort>, Sort> =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            { region, (concreteRef, innerGuard) ->
                val concreteKey = UAllocatedFieldId(key.field, concreteRef.address, key.sort)
                val newValue =
                    guard.uctx.mkIte(
                        innerGuard,
                        { value },
                        { region.readAllocated(concreteRef.address, key.field, key.sort) })
                UFieldsRegion(allocatedFields = region.allocatedFields.put(concreteKey, newValue), inputFields)
            },
            { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputFields(key)
                val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
                UFieldsRegion(region.allocatedFields, inputFields = newRegion)
            }
        )
}

//endregion

//region Arrays

data class UArrayIndexRef<ArrayType, Sort : USort>(
    override val sort: Sort,
    val ref: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType,
) : ULValue<UArrayIndexRef<ArrayType, Sort>, Sort> {

    override val memoryRegionId: UMemoryRegionId<UArrayIndexRef<ArrayType, Sort>, Sort> =
        UArrayRegionId(arrayType, sort)

    override val key: UArrayIndexRef<ArrayType, Sort> =
        this
}


data class UArrayRegionId<ArrayType, Sort : USort>(private val type: ArrayType, override val sort: Sort) :
    UMemoryRegionId<UArrayIndexRef<ArrayType, Sort>, Sort> {

    override fun emptyRegion(): UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> =
        UArrayRegion()
}

typealias UAllocatedArray<ArrayType, Sort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>
typealias UInputArray<ArrayType, Sort> = USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>

class UArrayRegion<ArrayType, Sort : USort>(
    private var allocatedArray: UAllocatedArray<ArrayType, Sort>? = null,
    private var inputArray: UInputArray<ArrayType, Sort>? = null,
    private val defaultValue: UExpr<Sort>? = null
) : UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> {

    private fun getAllocatedArray(
        arrayType: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress
    ): UAllocatedArray<ArrayType, Sort> {
        if (allocatedArray == null)
            allocatedArray = UAllocatedArrayId(arrayType, sort, defaultValue ?: sort.sampleUValue(), address, null).emptyArray()
        return allocatedArray!!
    }

    private fun getInputArray(arrayType: ArrayType, sort: Sort): UInputArray<ArrayType, Sort> {
        if (inputArray == null)
            inputArray = UInputArrayId(arrayType, sort, null).emptyRegion()
        return inputArray!!
    }

    override fun read(key: UArrayIndexRef<ArrayType, Sort>): UExpr<Sort> =
        key.ref.map(
            { concreteRef -> getAllocatedArray(key.arrayType, key.sort, concreteRef.address).read(key.index) },
            { symbolicRef -> getInputArray(key.arrayType, key.sort).read(symbolicRef to key.index) }
        )

    override fun write(
        key: UArrayIndexRef<ArrayType, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<UArrayIndexRef<ArrayType, Sort>, Sort> =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            { region, (concreteRef, innerGuard) ->
                val oldRegion = region.getAllocatedArray(key.arrayType, key.sort, concreteRef.address)
                val newRegion = oldRegion.write(key.index, value, innerGuard)
                UArrayRegion(allocatedArray = newRegion, region.inputArray)
            },
            { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputArray(key.arrayType, key.sort)
                val newRegion = oldRegion.write(symbolicRef to key.index, value, innerGuard)
                UArrayRegion(region.allocatedArray, inputArray = newRegion)
            }
        )

    fun memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ) =
        foldHeapRef(
            srcRef,
            this,
            guard,
            blockOnConcrete = { outerRegion, (srcRef, guard) ->
                foldHeapRef(
                    dstRef,
                    outerRegion,
                    guard,
                    blockOnConcrete = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getAllocatedArray(type, elementSort, srcRef.address)
                        val dstCollection = region.getAllocatedArray(type, elementSort, dstRef.address)
                        val adapter = USymbolicArrayAdapter(fromSrcIdx, fromDstIdx, toDstIdx, USizeExprKeyInfo)
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        UArrayRegion(newDstCollection, region.inputArray)
                    },
                    blockOnSymbolic = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getAllocatedArray(type, elementSort, srcRef.address)
                        val dstCollection = region.getInputArray(type, elementSort)
                        val adapter = USymbolicArrayAdapter(fromSrcIdx, dstRef to fromDstIdx, dstRef to toDstIdx, USymbolicArrayIndexKeyInfo)
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        UArrayRegion(region.allocatedArray, newDstCollection)
                    },
                )
            },
            blockOnSymbolic = { outerRegion, (srcRef, guard) ->
                foldHeapRef(
                    dstRef,
                    outerRegion,
                    guard,
                    blockOnConcrete = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getInputArray(type, elementSort)
                        val dstCollection = region.getAllocatedArray(type, elementSort, dstRef.address)
                        val adapter = USymbolicArrayAdapter(srcRef to fromSrcIdx, fromDstIdx, toDstIdx, USizeExprKeyInfo)
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        UArrayRegion(newDstCollection, region.inputArray)
                    },
                    blockOnSymbolic = { region, (dstRef, deepGuard) ->
                        val srcCollection = region.getInputArray(type, elementSort)
                        val dstCollection = region.getInputArray(type, elementSort)
                        val adapter = USymbolicArrayAdapter(srcRef to fromSrcIdx, dstRef to fromDstIdx, dstRef to toDstIdx, USymbolicArrayIndexKeyInfo)
                        val newDstCollection = dstCollection.copyRange(srcCollection, adapter, deepGuard)
                        UArrayRegion(region.allocatedArray, newDstCollection)
                    },
                )
            },
        )
}

internal fun <ArrayType, Sort: USort> UMemory<*, *>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrcIdx: USizeExpr,
    fromDstIdx: USizeExpr,
    toDstIdx: USizeExpr,
    guard: UBoolExpr,
) {
    val regionId = UArrayRegionId(type, elementSort)
    val region = getRegion(regionId) as UArrayRegion<ArrayType, Sort>
    val newRegion = region.memcpy(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard)
    setRegion(regionId, newRegion)
}

//endregion

//region Array lengths

data class UArrayLengthRef<ArrayType>(override val sort: USizeSort, val ref: UHeapRef, val arrayType: ArrayType) :
    ULValue<UArrayLengthRef<ArrayType>, USizeSort> {

    override val memoryRegionId: UMemoryRegionId<UArrayLengthRef<ArrayType>, USizeSort> =
        UArrayLengthsRegionId(sort, arrayType)

    override val key: UArrayLengthRef<ArrayType> = this
}

data class UArrayLengthsRegionId<ArrayType>(override val sort: USizeSort, val arrayType: ArrayType) :
    UMemoryRegionId<UArrayLengthRef<ArrayType>, USizeSort> {

    override fun emptyRegion(): UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort> =
        UArrayLengthsRegion()
}

typealias UAllocatedArrayLengths = PersistentMap<UConcreteHeapAddress, USizeExpr>
typealias UInputArrayLengths<ArrayType> = USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>

class UArrayLengthsRegion<ArrayType>(
    private val allocatedLengths: UAllocatedArrayLengths = persistentMapOf(),
    private var inputLengths: UInputArrayLengths<ArrayType>? = null
) : UMemoryRegion<UArrayLengthRef<ArrayType>, USizeSort> {

    private fun readAllocated(address: UConcreteHeapAddress, sort: USizeSort) =
        allocatedLengths
            .getOrDefault(address, sort.sampleUValue()) // sampleUValue is important
            .asExpr(sort)

    private fun getInputFields(ref: UArrayLengthRef<ArrayType>): UInputArrayLengths<ArrayType> {
        if (inputLengths == null)
            inputLengths = UInputArrayLengthId(ref.arrayType, ref.sort, null).emptyRegion()
        return inputLengths!!
    }

    override fun read(key: UArrayLengthRef<ArrayType>): USizeExpr =
        key.ref.map(
            { concreteRef -> readAllocated(concreteRef.address, key.sort) },
            { symbolicRef -> getInputFields(key).read(symbolicRef) }
        )

    override fun write(
        key: UArrayLengthRef<ArrayType>,
        value: USizeExpr,
        guard: UBoolExpr
    ) =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            { region, (concreteRef, innerGuard) ->
                val newValue =
                    guard.uctx.mkIte(innerGuard, { value }, { region.readAllocated(concreteRef.address, key.sort) })
                UArrayLengthsRegion(
                    allocatedLengths = region.allocatedLengths.put(concreteRef.address, newValue),
                    region.inputLengths
                )
            },
            { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputFields(key)
                val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
                UArrayLengthsRegion(region.allocatedLengths, inputLengths = newRegion)
            }
        )
}

//endregion

//region Symbolic maps

data class USymbolicMapEntryRef<MapType, KeySort : USort, ValueSort : USort>(
    val keySort: KeySort,
    override val sort: ValueSort,
    val mapRef: UHeapRef,
    val mapKey: UExpr<KeySort>,
    val mapType: MapType
) : ULValue<USymbolicMapEntryRef<MapType, KeySort, ValueSort>, ValueSort> {

    override val memoryRegionId: UMemoryRegionId<USymbolicMapEntryRef<MapType, KeySort, ValueSort>, ValueSort> =
        USymbolicMapRegionId(keySort, sort)

    override val key: USymbolicMapEntryRef<MapType, KeySort, ValueSort> = this
}

data class USymbolicMapRegionId<MapType, KeySort : USort, ValueSort : USort>(
    val keySort: KeySort,
    override val sort: ValueSort,
) :
    UMemoryRegionId<USymbolicMapEntryRef<MapType, KeySort, ValueSort>, ValueSort> {

    override fun emptyRegion(): UMemoryRegion<USymbolicMapEntryRef<MapType, KeySort, ValueSort>, ValueSort> =
        USymbolicMapRegion()
}

typealias UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg> =
        USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>

typealias UInputSymbolicMap<MapType, KeySort, ValueSort, Reg> =
        USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>

class USymbolicMapRegion<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val keySort: KeySort,
    private val valueSort: ValueSort,
    private val mapType: MapType,
    private val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,

    // Used only for maps with reference type keys
    private var allocatedMapWithAllocatedKeys: PersistentMap<Pair<UConcreteHeapAddress, UConcreteHeapAddress>, UExpr<ValueSort>> = persistentMapOf(),
    private var inputMapWithAllocatedKeys: PersistentMap<UConcreteHeapAddress, UAllocatedSymbolicMap<MapType, UAddressSort, ValueSort, Reg>> = persistentMapOf(),

    // Used for maps with both primitive-type keys and reference type keys
    private var allocatedMapWithInputKeys: PersistentMap<UConcreteHeapAddress, UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg>> = persistentMapOf(),
    private var inputMapWithInputKeys: UInputSymbolicMap<MapType, KeySort, ValueSort, Reg>? = null,
) : UMemoryRegion<USymbolicMapEntryRef<MapType, KeySort, ValueSort>, ValueSort> {

    private fun emptyInputMapWithAllocatedKeys(keyAddress: UConcreteHeapAddress) =
        UAllocatedSymbolicMapId(
            valueSort.sampleUValue(),
            keySort.uctx.addressSort,
            valueSort,
            mapType,
            UHeapRefKeyInfo,
            keyAddress,
            null
        ).emptyMap()

    private fun emptyAllocatedMapWithInputKeys(mapAddress: UConcreteHeapAddress): UAllocatedSymbolicMap<MapType, KeySort, ValueSort, Reg> =
        UAllocatedSymbolicMapId(
            valueSort.sampleUValue(),
            keySort,
            valueSort,
            mapType,
            keyInfo,
            mapAddress,
            null
        ).emptyMap()

    private fun getInputMapWithInputKeys(): UInputSymbolicMap<MapType, KeySort, ValueSort, Reg> {
        if (inputMapWithInputKeys == null)
            inputMapWithInputKeys = UInputSymbolicMapId(
                keySort,
                valueSort,
                mapType,
                keyInfo,
                null
            ).emptyMap()
        return inputMapWithInputKeys!!
    }

    override fun read(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort>): UExpr<ValueSort> =
        if (keySort == keySort.uctx.addressSort) {
            @Suppress("UNCHECKED_CAST")
            readRefKeyMap(key as USymbolicMapEntryRef<MapType, UAddressSort, ValueSort>)
        } else {
            readNonRefKeyMap(key)
        }

    private fun readNonRefKeyMap(key: USymbolicMapEntryRef<MapType, KeySort, ValueSort>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                (allocatedMapWithInputKeys[concreteRef.address] ?: emptyAllocatedMapWithInputKeys(
                    concreteRef.address
                )).read(key.mapKey)
            },
            { symbolicRef -> getInputMapWithInputKeys().read(symbolicRef to key.mapKey) }
        )

    private fun readRefKeyMap(key: USymbolicMapEntryRef<MapType, UAddressSort, ValueSort>): UExpr<ValueSort> =
        key.mapRef.map(
            { concreteRef ->
                key.mapKey.map(
                    { concreteKey ->
                        allocatedMapWithAllocatedKeys[concreteRef.address to concreteKey.address]
                            ?: valueSort.sampleUValue()
                    },
                    { symbolicKey ->
                        (
                            allocatedMapWithInputKeys[concreteRef.address]
                                ?: emptyAllocatedMapWithInputKeys(concreteRef.address)
                        ).read(symbolicKey.uncheckedCast())
                    }
                )
            },
            { symbolicRef ->
                key.mapKey.map(
                    { concreteKey ->
                        (
                            inputMapWithAllocatedKeys[concreteKey.address]
                                ?: emptyInputMapWithAllocatedKeys(concreteKey.address)
                        ).read(symbolicRef)
                    },
                    { symbolicKey -> getInputMapWithInputKeys().read(symbolicRef to symbolicKey.uncheckedCast()) }
                )
            }
        )


    override fun write(
        key: USymbolicMapEntryRef<MapType, KeySort, ValueSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) =
        foldHeapRef(
            key.ref,
            initial = this,
            initialGuard = guard,
            { region, (concreteRef, innerGuard) ->
                val oldRegion = region.getAllocatedMap(concreteRef.address)
                val newRegion = oldRegion.write(concreteRef, value, innerGuard)
                USymbolicMapRegion(keySort, valueSort, type, allocatedKeyInfo, inputKeyInfo, newRegion, region.inputMap)
            },
            { region, (symbolicRef, innerGuard) ->
                val oldRegion = region.getInputMap()
                val newRegion = oldRegion.write(symbolicRef, value, innerGuard)
                USymbolicMapRegion(
                    keySort,
                    valueSort,
                    type,
                    allocatedKeyInfo,
                    inputKeyInfo,
                    region.allocatedMap,
                    newRegion
                )
            }
        )
}

//endregion

//region Symbolic map lengths


typealias UInputSymbolicMapLengthCollection<MapType> = USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>

//endregion