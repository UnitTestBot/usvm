package org.usvm.memory

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.*
import org.usvm.util.Region

interface UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType, Guard> {
    fun <Sort : USort> readField(ref: Ref, field: Field, sort: Sort): Value
    fun <Sort : USort> readArrayIndex(ref: Ref, index: SizeT, arrayType: ArrayType, sort: Sort): Value
    fun readArrayLength(ref: Ref, arrayType: ArrayType): SizeT

    fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> readSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        ref: Ref,
        key: UExpr<KeySort>
    ): Value

    fun readSymbolicMapLength(descriptor: USymbolicMapDescriptor<*, *, *>, ref: Ref): SizeT

    /**
     * Returns a copy of the current map to be able to modify it without changing the original one.
     */
    fun toMutableHeap(): UHeap<Ref, Value, SizeT, Field, ArrayType, Guard>

    fun nullRef(): Ref
}

typealias UReadOnlySymbolicHeap<Field, ArrayType> = UReadOnlyHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr>

interface UHeap<Ref, Value, SizeT, Field, ArrayType, Guard> :
    UReadOnlyHeap<Ref, Value, SizeT, Field, ArrayType, Guard> {
    fun <Sort : USort> writeField(ref: Ref, field: Field, sort: Sort, value: Value, guard: Guard)
    fun <Sort : USort> writeArrayIndex(
        ref: Ref,
        index: SizeT,
        type: ArrayType,
        sort: Sort,
        value: Value,
        guard: Guard,
    )

    fun writeArrayLength(ref: Ref, size: SizeT, arrayType: ArrayType)

    fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> writeSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        ref: Ref,
        key: UExpr<KeySort>,
        value: Value,
        guard: Guard
    )

    fun writeSymbolicMapLength(descriptor: USymbolicMapDescriptor<*, *, *>, ref: Ref, size: SizeT)

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

    fun <Reg : Region<Reg>, Sort : USort> copySymbolicMap(
        descriptor: USymbolicMapDescriptor<USizeSort, Sort, Reg>,
        srcRef: Ref,
        dstRef: Ref,
        fromSrcKey: SizeT,
        fromDstKey: SizeT,
        toDstKey: SizeT,
        guard: Guard,
    )

    fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> mergeSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        keyContainsDescriptor: USymbolicMapDescriptor<KeySort, UBoolSort, Reg>,
        srcRef: Ref,
        dstRef: Ref,
        guard: Guard,
    )

    fun allocate(): UConcreteHeapRef
    fun allocateArray(count: SizeT): UConcreteHeapRef
    fun <Sort : USort> allocateArrayInitialized(
        type: ArrayType,
        sort: Sort,
        contents: Sequence<Value>
    ): UConcreteHeapRef
}

typealias USymbolicHeap<Field, ArrayType> = UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr>

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

data class ConcreteTaggedMapDescriptor(
    val descriptor: USymbolicMapDescriptor<*, *, *>,
    val tag: Any?
)

data class URegionHeap<Field, ArrayType>(
    private val ctx: UContext,
    private var lastAddress: UAddressCounter = UAddressCounter(),
    private var allocatedFields: PersistentMap<Pair<UConcreteHeapAddress, Field>, UExpr<out USort>> = persistentMapOf(),
    private var inputFields: PersistentMap<Field, UInputFieldRegion<Field, out USort>> = persistentMapOf(),
    private var allocatedArrays: PersistentMap<UConcreteHeapAddress, UAllocatedArrayRegion<ArrayType, out USort>> = persistentMapOf(),
    private var inputArrays: PersistentMap<ArrayType, UInputArrayRegion<ArrayType, out USort>> = persistentMapOf(),
    private var allocatedLengths: PersistentMap<UConcreteHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputLengths: PersistentMap<ArrayType, UInputArrayLengthRegion<ArrayType>> = persistentMapOf(),
    private var allocatedMaps: PersistentMap<ConcreteTaggedMapDescriptor, PersistentMap<UConcreteHeapAddress, UAllocatedSymbolicMapRegion<USort, *, *>>> = persistentMapOf(),
    private var inputMaps: PersistentMap<USymbolicMapDescriptor<*, *, *>, UInputSymbolicMapRegion<USort, *, *>> = persistentMapOf(),
    private var allocatedMapsLengths: PersistentMap<UConcreteHeapAddress, USizeExpr> = persistentMapOf(),
    private var inputMapsLengths: PersistentMap<USymbolicMapDescriptor<*, *, *>, UInputSymbolicMapLengthRegion> = persistentMapOf(),
) : USymbolicHeap<Field, ArrayType> {
    private fun <Sort : USort> inputFieldRegion(
        field: Field,
        sort: Sort,
    ): UInputFieldRegion<Field, Sort> =
        inputFields[field]
            ?.inputFieldsRegionUncheckedCast()
            ?: emptyInputFieldRegion(field, sort)
                .also { inputFields = inputFields.put(field, it) } // to increase cache usage

    private fun <Sort : USort> allocatedArrayRegion(
        arrayType: ArrayType,
        address: UConcreteHeapAddress,
        elementSort: Sort,
    ): UAllocatedArrayRegion<ArrayType, Sort> =
        allocatedArrays[address]
            ?.allocatedArrayRegionUncheckedCast()
            ?: emptyAllocatedArrayRegion(arrayType, address, elementSort).also { region ->
                allocatedArrays = allocatedArrays.put(address, region)
            } // to increase cache usage

    private fun <Sort : USort> inputArrayRegion(
        arrayType: ArrayType,
        elementSort: Sort,
    ): UInputArrayRegion<ArrayType, Sort> =
        inputArrays[arrayType]
            ?.inputArrayRegionUncheckedCast()
            ?: emptyInputArrayRegion(arrayType, elementSort).also { region ->
                inputArrays = inputArrays.put(arrayType, region)
            } // to increase cache usage

    private fun inputArrayLengthRegion(
        arrayType: ArrayType,
    ): UInputArrayLengthRegion<ArrayType> =
        inputLengths[arrayType]
            ?: emptyInputArrayLengthRegion(arrayType, ctx.sizeSort).also { region ->
                inputLengths = inputLengths.put(arrayType, region)
            } // to increase cache usage

    private fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> allocatedMapRegion(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        address: UConcreteHeapAddress,
        tag: Any? = null
    ): UAllocatedSymbolicMapRegion<KeySort, Reg, Sort> {
        val taggedKey = ConcreteTaggedMapDescriptor(descriptor, tag)
        val allocatedConcreteMap = allocatedMaps[taggedKey] ?: persistentMapOf()
        return allocatedConcreteMap[address]
            ?.allocatedMapRegionUncheckedCast()
            ?: emptyAllocatedSymbolicMapRegion(descriptor, address).also { region ->
                val concreteMap = allocatedConcreteMap.put(address, region.uncheckedCast())
                allocatedMaps = allocatedMaps.put(taggedKey, concreteMap)
            }
    }

    private fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> storeAllocatedMapRegion(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        address: UConcreteHeapAddress,
        newRegion: UAllocatedSymbolicMapRegion<KeySort, Reg, Sort>,
        tag: Any? = null
    ) {
        val taggedKey = ConcreteTaggedMapDescriptor(descriptor, tag)
        val allocatedConcreteMap = allocatedMaps[taggedKey] ?: persistentMapOf()
        val modifiedAllocatedMap = allocatedConcreteMap.put(address, newRegion.uncheckedCast())
        allocatedMaps = allocatedMaps.put(taggedKey, modifiedAllocatedMap)
    }

    private fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> inputMapRegion(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>
    ): UInputSymbolicMapRegion<KeySort, Reg, Sort> =
        inputMaps[descriptor]
            ?.inputMapRegionUncheckedCast()
            ?: emptyInputSymbolicMapRegion(descriptor).also { region ->
                inputMaps = inputMaps.put(descriptor, region.uncheckedCast())
            }

    private fun inputMapLengthRegion(
        descriptor: USymbolicMapDescriptor<*, *, *>
    ): UInputSymbolicMapLengthRegion {
        return inputMapsLengths[descriptor]
            ?: emptyInputSymbolicMapLengthRegion(descriptor, ctx.sizeSort).also { region ->
                inputMapsLengths = inputMapsLengths.put(descriptor, region)
            }
    }

    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> =
        ref.map(
            { concreteRef ->
                allocatedFields
                    .getOrDefault(concreteRef.address to field, sort.sampleUValue()) // sampleUValue is important
                    .asExpr(sort)
            },
            { symbolicRef -> inputFieldRegion(field, sort).read(symbolicRef) }
        )

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        sort: Sort,
    ): UExpr<Sort> =
        ref.map(
            { concreteRef -> allocatedArrayRegion(arrayType, concreteRef.address, sort).read(index) },
            { symbolicRef -> inputArrayRegion(arrayType, sort).read(symbolicRef to index) }
        )

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> readSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        ref: UHeapRef,
        key: UExpr<KeySort>
    ): UExpr<Sort> = if (key.sort == key.uctx.addressSort) {
        @Suppress("UNCHECKED_CAST")
        readSymbolicRefMap(
            descriptor = descriptor as USymbolicMapDescriptor<UAddressSort, Sort, Reg>,
            ref = ref,
            key = key.asExpr(key.uctx.addressSort)
        )
    } else {
        ref.map(
            { concreteRef -> allocatedMapRegion(descriptor, concreteRef.address).read(key) },
            { symbolicRef -> inputMapRegion(descriptor).read(symbolicRef to key) }
        )
    }

    // Reorder map ref and key
    private object ConcreteKeySymbolicRefAllocatedMap

    // Reorder map ref and key
    private object ConcreteKeyConcreteRefAllocatedMap

    // Normal order
    private object SymbolicKeyConcreteRefAllocatedMap

    private fun <Reg : Region<Reg>, Sort : USort> readSymbolicRefMap(
        descriptor: USymbolicMapDescriptor<UAddressSort, Sort, Reg>,
        ref: UHeapRef,
        key: UHeapRef
    ): UExpr<Sort> = ref.map(
        { concreteMapRef ->
            key.map(
                { concreteKeyRef ->
                    allocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteKeyRef.address,
                        tag = ConcreteKeyConcreteRefAllocatedMap
                    ).read(concreteMapRef)
                },
                { symbolicKeyRef ->
                    allocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteMapRef.address,
                        tag = SymbolicKeyConcreteRefAllocatedMap
                    ).read(symbolicKeyRef)
                }
            )
        },
        { symbolicMapRef ->
            key.map(
                { concreteKeyRef ->
                    allocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteKeyRef.address,
                        tag = ConcreteKeySymbolicRefAllocatedMap
                    ).read(symbolicMapRef)
                },
                { symbolicKeyRef ->
                    inputMapRegion(descriptor).read(symbolicMapRef to symbolicKeyRef)
                }
            )
        }
    )

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr =
        ref.map(
            { concreteRef -> allocatedLengths.getOrDefault(concreteRef.address, ctx.sizeSort.sampleUValue()) },
            { symbolicRef -> inputArrayLengthRegion(arrayType).read(symbolicRef) }
        )

    override fun readSymbolicMapLength(descriptor: USymbolicMapDescriptor<*, *, *>, ref: UHeapRef): USizeExpr =
        ref.map(
            { concreteRef -> allocatedMapsLengths.getOrDefault(concreteRef.address, ctx.sizeSort.sampleUValue()) },
            { symbolicRef -> inputMapLengthRegion(descriptor).read(symbolicRef) }
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
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        val valueToWrite = value.asExpr(sort)

        withHeapRef(
            ref,
            guard,
            { (concreteRef, innerGuard) ->
                val oldRegion = allocatedArrayRegion(type, concreteRef.address, sort)
                val newRegion = oldRegion.write(index, valueToWrite, innerGuard)
                allocatedArrays = allocatedArrays.put(concreteRef.address, newRegion)
            },
            { (symbolicRef, innerGuard) ->
                val oldRegion = inputArrayRegion(type, sort)
                val newRegion = oldRegion.write(symbolicRef to index, valueToWrite, innerGuard)
                inputArrays = inputArrays.put(type, newRegion)
            }
        )
    }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> writeSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        ref: UHeapRef,
        key: UExpr<KeySort>,
        value: UExpr<out USort>,
        guard: UBoolExpr
    ) {
        val valueToWrite = value.asExpr(descriptor.valueSort)

        if (key.sort == key.uctx.addressSort) {
            @Suppress("UNCHECKED_CAST")
            writeSymbolicRefMap(
                descriptor = descriptor as USymbolicMapDescriptor<UAddressSort, Sort, Reg>,
                ref = ref,
                key = key.asExpr(key.uctx.addressSort),
                value = valueToWrite,
                guard = guard
            )
        } else {
            withHeapRef(
                ref = ref,
                initialGuard = guard,
                blockOnConcrete = { (concreteRef, innerGuard) ->
                    val oldRegion = allocatedMapRegion(descriptor, concreteRef.address)
                    val newRegion = oldRegion.write(key, valueToWrite, innerGuard)
                    storeAllocatedMapRegion(descriptor, concreteRef.address, newRegion)
                },
                blockOnSymbolic = { (symbolicRef, innerGuard) ->
                    val oldRegion = inputMapRegion(descriptor)
                    val newRegion = oldRegion.write(symbolicRef to key, valueToWrite, innerGuard)
                    inputMaps = inputMaps.put(descriptor, newRegion.uncheckedCast())
                }
            )
        }
    }

    private fun <Reg : Region<Reg>, Sort : USort> writeSymbolicRefMap(
        descriptor: USymbolicMapDescriptor<UAddressSort, Sort, Reg>,
        ref: UHeapRef,
        key: UHeapRef,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) = withHeapRef(
        ref = ref,
        initialGuard = guard,
        blockOnConcrete = { (concreteMapRef, mapGuard) ->
            withHeapRef(
                ref = key,
                initialGuard = mapGuard,
                blockOnConcrete = { (concreteKeyRef, keyGuard) ->
                    val oldRegion = allocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteKeyRef.address,
                        tag = ConcreteKeyConcreteRefAllocatedMap
                    )

                    val newRegion = oldRegion.write(concreteMapRef, value, keyGuard)

                    storeAllocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteKeyRef.address,
                        newRegion = newRegion,
                        tag = ConcreteKeyConcreteRefAllocatedMap
                    )
                },
                blockOnSymbolic = { (symbolicKeyRef, keyGuard) ->
                    val oldRegion = allocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteMapRef.address,
                        tag = SymbolicKeyConcreteRefAllocatedMap
                    )

                    val newRegion = oldRegion.write(symbolicKeyRef, value, keyGuard)

                    storeAllocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteMapRef.address,
                        newRegion = newRegion,
                        tag = SymbolicKeyConcreteRefAllocatedMap
                    )
                }
            )
        },
        blockOnSymbolic = { (symbolicMapRef, mapGuard) ->
            withHeapRef(
                ref = key,
                initialGuard = mapGuard,
                blockOnConcrete = { (concreteKeyRef, keyGuard) ->
                    val oldRegion = allocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteKeyRef.address,
                        tag = ConcreteKeySymbolicRefAllocatedMap
                    )

                    val newRegion = oldRegion.write(symbolicMapRef, value, keyGuard)

                    storeAllocatedMapRegion(
                        descriptor = descriptor,
                        address = concreteKeyRef.address,
                        newRegion = newRegion,
                        tag = ConcreteKeySymbolicRefAllocatedMap
                    )
                },
                blockOnSymbolic = { (symbolicKeyRef, keyGuard) ->
                    val oldRegion = inputMapRegion(descriptor)
                    val newRegion = oldRegion.write(symbolicMapRef to symbolicKeyRef, value, keyGuard)
                    inputMaps = inputMaps.put(descriptor, newRegion.uncheckedCast())
                }
            )
        }
    )

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

    override fun writeSymbolicMapLength(descriptor: USymbolicMapDescriptor<*, *, *>, ref: UHeapRef, size: USizeExpr) {
        withHeapRef(
            ref = ref,
            initialGuard = ctx.trueExpr,
            blockOnConcrete = { (concreteRef, guard) ->
                val oldSize = readSymbolicMapLength(descriptor, ref)
                val newSize = ctx.mkIte(guard, size, oldSize)
                allocatedMapsLengths = allocatedMapsLengths.put(concreteRef.address, newSize)
            },
            blockOnSymbolic = { (symbolicRef, guard) ->
                val region = inputMapLengthRegion(descriptor)
                val newRegion = region.write(symbolicRef, size, guard)
                inputMapsLengths = inputMapsLengths.put(descriptor, newRegion)
            }
        )
    }

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ) {
        val tmpArrayRef = allocateArrayInitialized(type, sort, contents)
        val contentLength = allocatedLengths.getValue(tmpArrayRef.address)
        memcpy(
            srcRef = tmpArrayRef,
            dstRef = ref,
            type = type,
            elementSort = sort,
            fromSrcIdx = ctx.mkSizeExpr(0),
            fromDstIdx = ctx.mkSizeExpr(0),
            toDstIdx = contentLength,
            guard = ctx.trueExpr
        )
        writeArrayLength(ref, contentLength, type)
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

    override fun <Reg : Region<Reg>, Sort : USort> copySymbolicMap(
        descriptor: USymbolicMapDescriptor<USizeSort, Sort, Reg>,
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        fromSrcKey: USizeExpr,
        fromDstKey: USizeExpr,
        toDstKey: USizeExpr,
        guard: UBoolExpr
    ) {
        withHeapRef(
            srcRef,
            guard,
            blockOnConcrete = { (srcRef, guard) ->
                val srcRegion = allocatedMapRegion(descriptor, srcRef.address)
                val src = srcRef to fromSrcKey

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstKey

                        val dstRegion = allocatedMapRegion(descriptor, dstRef.address)
                        val keyConverter = UAllocatedToAllocatedKeyConverter(src, dst, toDstKey)
                        val newDstRegion = dstRegion.copyRange(
                            fromRegion = srcRegion,
                            fromKey = fromDstKey,
                            toKey = toDstKey,
                            keyConverter = keyConverter,
                            guard = deepGuard
                        )
                        storeAllocatedMapRegion(descriptor, dstRef.address, newDstRegion)
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstKey

                        val dstRegion = inputMapRegion(descriptor)
                        val keyConverter = UAllocatedToInputKeyConverter(src, dst, toDstKey)
                        val newDstRegion = dstRegion.copyRange(
                            fromRegion = srcRegion,
                            fromKey = src,
                            toKey = dst,
                            keyConverter = keyConverter,
                            guard = deepGuard
                        )
                        inputMaps = inputMaps.put(descriptor, newDstRegion.uncheckedCast())
                    },
                )
            },
            blockOnSymbolic = { (srcRef, guard) ->
                val srcRegion = inputMapRegion(descriptor)
                val src = srcRef to fromSrcKey

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstKey

                        val dstRegion = allocatedMapRegion(descriptor, dstRef.address)
                        val keyConverter = UInputToAllocatedKeyConverter(src, dst, toDstKey)
                        val newDstRegion = dstRegion.copyRange(
                            fromRegion = srcRegion,
                            fromKey = fromDstKey,
                            toKey = toDstKey,
                            keyConverter = keyConverter,
                            guard = deepGuard
                        )
                        storeAllocatedMapRegion(descriptor, dstRef.address, newDstRegion)
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dst = dstRef to fromDstKey

                        val dstRegion = inputMapRegion(descriptor)
                        val keyConverter = UInputToInputKeyConverter(src, dst, toDstKey)
                        val newDstRegion = dstRegion.copyRange(
                            fromRegion = srcRegion,
                            fromKey = dst,
                            toKey = dstRef to toDstKey,
                            keyConverter = keyConverter,
                            guard = deepGuard
                        )
                        inputMaps = inputMaps.put(descriptor, newDstRegion.uncheckedCast())
                    },
                )
            },
        )
    }

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> mergeSymbolicMap(
        descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
        keyContainsDescriptor: USymbolicMapDescriptor<KeySort, UBoolSort, Reg>,
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        guard: UBoolExpr
    ) {
        if (descriptor.keySort == descriptor.keySort.uctx.addressSort) {
            @Suppress("UNCHECKED_CAST")
            return mergeSymbolicRefMap(
                descriptor as USymbolicMapDescriptor<UAddressSort, Sort, Reg>,
                keyContainsDescriptor as USymbolicMapDescriptor<UAddressSort, UBoolSort, Reg>,
                srcRef,
                dstRef,
                guard
            )
        }

        withHeapRef(
            srcRef,
            guard,
            blockOnConcrete = { (srcRef, guard) ->
                val srcRegion = allocatedMapRegion(descriptor, srcRef.address)
                val srcKeyContainsRegion = allocatedMapRegion(keyContainsDescriptor, srcRef.address)

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dstRegion = allocatedMapRegion(descriptor, dstRef.address)

                        val newDstRegion = dstRegion.mergeWithRegion(
                            fromRegion = srcRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(keyContainsDescriptor, srcKeyContainsRegion),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { it }
                        )

                        storeAllocatedMapRegion(descriptor, dstRef.address, newDstRegion)
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dstRegion = inputMapRegion(descriptor)

                        val newDstRegion = dstRegion.mergeWithRegion(
                            fromRegion = srcRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(keyContainsDescriptor, srcKeyContainsRegion),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { it.second }
                        )

                        inputMaps = inputMaps.put(descriptor, newDstRegion.uncheckedCast())
                    },
                )
            },
            blockOnSymbolic = { (srcRef, guard) ->
                val srcRegion = inputMapRegion(descriptor)
                val srcKeyContainsRegion = inputMapRegion(keyContainsDescriptor)

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dstRegion = allocatedMapRegion(descriptor, dstRef.address)

                        val newDstRegion = dstRegion.mergeWithRegion(
                            fromRegion = srcRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(keyContainsDescriptor, srcKeyContainsRegion),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { this.srcRef to it }
                        )

                        storeAllocatedMapRegion(descriptor, dstRef.address, newDstRegion)
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dstRegion = inputMapRegion(descriptor)

                        val newDstRegion = dstRegion.mergeWithRegion(
                            fromRegion = srcRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(keyContainsDescriptor, srcKeyContainsRegion),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { this.srcRef to it.second }
                        )

                        inputMaps = inputMaps.put(descriptor, newDstRegion.uncheckedCast())
                    },
                )
            },
        )
    }

    /**
     * Concrete src -> Symbolic dst
     * sck, ssk        dck, dsk
     *
     * rck = dck (map concrete -> symbolic region) + sck (concrete region)
     * for key in sck: write(dst, key, ite(include(src, key) read(src, key) read(dst, key)))
     *
     * rsk = dsk + ssk | merge node
     *
     * -----------------
     *
     * Symbolic src -> Symbolic dst
     * sck, ssk        dck, dsk
     *
     * rck = dck (map concrete -> symbolic region) + sck (map concrete -> symbolic region)
     * for key in sck: write(dst, key, ite(include(src, key) read(src, key) read(dst, key)))
     *
     * rsk = dsk + ssk | merge node
     *
     * ------------------
     *
     * Symbolic src -> Concrete dst
     * sck, ssk        dck, dsk
     *
     * rck = dck (concrete region) + sck (map concrete -> symbolic region)
     * for key in sck: write(dst, key, ite(include(src, key) read(src, key) read(dst, key)))
     *
     * rsk = dsk + ssk | merge node
     * */
    private fun <Reg : Region<Reg>, Sort : USort> mergeSymbolicRefMap(
        descriptor: USymbolicMapDescriptor<UAddressSort, Sort, Reg>,
        keyContainsDescriptor: USymbolicMapDescriptor<UAddressSort, UBoolSort, Reg>,
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        guard: UBoolExpr
    ) {
        withHeapRef(
            srcRef,
            guard,
            blockOnConcrete = { (srcRef, guard) ->
                val srcSymbolicKeysRegion = allocatedMapRegion(
                    descriptor = descriptor,
                    address = srcRef.address,
                    tag = SymbolicKeyConcreteRefAllocatedMap
                )

                val srcSymbolicKeyContainsRegion = allocatedMapRegion(
                    descriptor = keyContainsDescriptor,
                    address = srcRef.address,
                    tag = SymbolicKeyConcreteRefAllocatedMap
                )

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dstSymbolicKeysRegion = allocatedMapRegion(
                            descriptor = descriptor,
                            address = dstRef.address,
                            tag = SymbolicKeyConcreteRefAllocatedMap
                        )

                        val mergedSymbolicKeysRegion = dstSymbolicKeysRegion.mergeWithRegion(
                            fromRegion = srcSymbolicKeysRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(
                                descriptor = keyContainsDescriptor,
                                region = srcSymbolicKeyContainsRegion
                            ),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { it }
                        )

                        storeAllocatedMapRegion(
                            descriptor = descriptor,
                            address = dstRef.address,
                            newRegion = mergedSymbolicKeysRegion,
                            tag = SymbolicKeyConcreteRefAllocatedMap
                        )
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dstSymbolicKeysRegion = inputMapRegion(descriptor)

                        val mergedSymbolicKeysRegion = dstSymbolicKeysRegion.mergeWithRegion(
                            fromRegion = srcSymbolicKeysRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(
                                descriptor = keyContainsDescriptor,
                                region = srcSymbolicKeyContainsRegion
                            ),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { it.second }
                        )

                        inputMaps = inputMaps.put(descriptor, mergedSymbolicKeysRegion.uncheckedCast())
                    },
                )


                val srcKeysTag = ConcreteTaggedMapDescriptor(
                    descriptor = descriptor,
                    tag = ConcreteKeyConcreteRefAllocatedMap
                )
                val possibleSrcConcreteKeys = allocatedMaps[srcKeysTag]?.keys ?: emptySet()

                for (key in possibleSrcConcreteKeys) {
                    val keyRef = ctx.mkConcreteHeapRef(key)

                    val include = readSymbolicRefMap(keyContainsDescriptor, srcRef, keyRef)
                    check(include === ctx.trueExpr || include === ctx.falseExpr) {
                        "Include check on concrete ref and key must return a constant expression"
                    }

                    if (include.isTrue) {
                        val srcValue = readSymbolicRefMap(descriptor, srcRef, keyRef)
                        writeSymbolicRefMap(descriptor, dstRef, keyRef, srcValue, guard)
                    }
                }
            },
            blockOnSymbolic = { (srcRef, guard) ->
                val srcSymbolicKeysRegion = inputMapRegion(descriptor)
                val srcSymbolicKeysContainsRegion = inputMapRegion(keyContainsDescriptor)

                withHeapRef(
                    dstRef,
                    guard,
                    blockOnConcrete = { (dstRef, deepGuard) ->
                        val dstSymbolicKeysRegion = allocatedMapRegion(
                            descriptor = descriptor,
                            address = dstRef.address,
                            tag = SymbolicKeyConcreteRefAllocatedMap
                        )

                        val mergedSymbolicKeysRegion = dstSymbolicKeysRegion.mergeWithRegion(
                            fromRegion = srcSymbolicKeysRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(
                                descriptor = keyContainsDescriptor,
                                region = srcSymbolicKeysContainsRegion
                            ),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { this.srcRef to it }
                        )

                        storeAllocatedMapRegion(
                            descriptor = descriptor,
                            address = dstRef.address,
                            newRegion = mergedSymbolicKeysRegion,
                            tag = SymbolicKeyConcreteRefAllocatedMap
                        )
                    },
                    blockOnSymbolic = { (dstRef, deepGuard) ->
                        val dstSymbolicKeysRegion = inputMapRegion(descriptor)

                        val mergedSymbolicKeysRegion = dstSymbolicKeysRegion.mergeWithRegion(
                            fromRegion = srcSymbolicKeysRegion,
                            guard = deepGuard,
                            keyIncludesCheck = UMergeKeyIncludesCheck(
                                descriptor = keyContainsDescriptor,
                                region = srcSymbolicKeysContainsRegion
                            ),
                            keyConverter = UMergeKeyConverter(srcRef, dstRef) { this.srcRef to it.second }
                        )

                        inputMaps = inputMaps.put(descriptor, mergedSymbolicKeysRegion.uncheckedCast())
                    },
                )

                val srcKeysTag = ConcreteTaggedMapDescriptor(
                    descriptor = descriptor,
                    tag = ConcreteKeySymbolicRefAllocatedMap
                )
                val possibleSrcConcreteKeys = allocatedMaps[srcKeysTag]?.keys ?: emptySet()

                for (key in possibleSrcConcreteKeys) {
                    val keyRef = ctx.mkConcreteHeapRef(key)
                    val srcValue = readSymbolicRefMap(descriptor, srcRef, keyRef)
                    val dstValue = readSymbolicRefMap(descriptor, dstRef, keyRef)
                    val include = readSymbolicRefMap(keyContainsDescriptor, srcRef, keyRef)
                    val mergedValue = ctx.mkIte(include, srcValue, dstValue)
                    writeSymbolicRefMap(descriptor, dstRef, keyRef, mergedValue, guard)
                }
            },
        )
    }

    override fun allocate() = ctx.mkConcreteHeapRef(lastAddress.freshAddress())

    override fun allocateArray(count: USizeExpr): UConcreteHeapRef {
        val address = lastAddress.freshAddress()
        allocatedLengths = allocatedLengths.put(address, count)
        return ctx.mkConcreteHeapRef(address)
    }

    override fun <Sort : USort> allocateArrayInitialized(
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>
    ): UConcreteHeapRef {
        val arrayValues = contents.mapTo(mutableListOf()) { it.asExpr(sort) }
        val arrayLength = ctx.mkSizeExpr(arrayValues.size)

        val address = allocateArray(arrayLength)

        val initializedArrayRegion = allocateInitializedArrayRegion(type, sort, address.address, arrayValues)
        allocatedArrays = allocatedArrays.put(address.address, initializedArrayRegion)

        return address
    }

    private fun <Sort : USort> allocateInitializedArrayRegion(
        type: ArrayType,
        sort: Sort,
        address: UConcreteHeapAddress,
        values: List<UExpr<Sort>>
    ): UAllocatedArrayRegion<ArrayType, Sort> = initializedAllocatedArrayRegion(
        arrayType = type,
        address = address,
        sort = sort,
        content = values.mapIndexed { idx, value ->
            ctx.mkSizeExpr(idx) to value
        }.toMap(),
        guard = ctx.trueExpr
    )

    override fun nullRef(): UHeapRef = ctx.nullRef

    override fun toMutableHeap() = URegionHeap(
        ctx, lastAddress,
        allocatedFields, inputFields,
        allocatedArrays, inputArrays,
        allocatedLengths, inputLengths,
        allocatedMaps, inputMaps,
        allocatedMapsLengths, inputMapsLengths,
    )
}

@Suppress("UNCHECKED_CAST")
fun <Field, Sort : USort> UInputFieldRegion<Field, *>.inputFieldsRegionUncheckedCast(): UInputFieldRegion<Field, Sort> =
    this as UInputFieldRegion<Field, Sort>

@Suppress("UNCHECKED_CAST")
fun <ArrayType, Sort : USort> UAllocatedArrayRegion<ArrayType, *>.allocatedArrayRegionUncheckedCast(): UAllocatedArrayRegion<ArrayType, Sort> =
    this as UAllocatedArrayRegion<ArrayType, Sort>

@Suppress("UNCHECKED_CAST")
fun <ArrayType, Sort : USort> UInputArrayRegion<ArrayType, *>.inputArrayRegionUncheckedCast(): UInputArrayRegion<ArrayType, Sort> =
    this as UInputArrayRegion<ArrayType, Sort>

@Suppress("UNCHECKED_CAST")
fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> UAllocatedSymbolicMapRegion<USort, *, *>.allocatedMapRegionUncheckedCast(): UAllocatedSymbolicMapRegion<KeySort, Reg, Sort> =
    this as UAllocatedSymbolicMapRegion<KeySort, Reg, Sort>

@Suppress("UNCHECKED_CAST")
fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> UInputSymbolicMapRegion<USort, *, *>.inputMapRegionUncheckedCast(): UInputSymbolicMapRegion<KeySort, Reg, Sort> =
    this as UInputSymbolicMapRegion<KeySort, Reg, Sort>
