package org.usvm.collection.set.ref

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UHeapRef
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.foldHeapRef
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.guardedWrite
import org.usvm.memory.map
import org.usvm.uctx

data class URefSetEntryLValue<SetType>(
    val setRef: UHeapRef,
    val setElement: UHeapRef,
    val setType: SetType
) : ULValue<URefSetEntryLValue<SetType>, UBoolSort> {
    override val sort: UBoolSort
        get() = setRef.uctx.boolSort

    override val memoryRegionId: UMemoryRegionId<URefSetEntryLValue<SetType>, UBoolSort>
        get() = URefSetRegionId(setType, sort)

    override val key: URefSetEntryLValue<SetType>
        get() = this
}

data class URefSetRegionId<SetType>(
    val setType: SetType,
    override val sort: UBoolSort
) : UMemoryRegionId<URefSetEntryLValue<SetType>, UBoolSort> {
    override fun emptyRegion(): UMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> =
        URefSetMemoryRegion(setType, sort)
}

internal data class UAllocatedRefSetWithAllocatedElementId(
    val setAddress: UConcreteHeapAddress,
    val elementAddress: UConcreteHeapAddress
)

typealias UAllocatedRefSetWithInputElements<SetType> =
        USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>

typealias UInputRefSetWithAllocatedElements<SetType> =
        USymbolicCollection<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>

typealias UInputRefSetWithInputElements<SetType> =
        USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>

interface URefSetRegion<SetType> :
    UMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    fun allocatedSetWithInputElements(setRef: UConcreteHeapAddress): UAllocatedRefSetWithInputElements<SetType>
    fun inputSetWithInputElements(): UInputRefSetWithInputElements<SetType>

    fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
    ): URefSetRegion<SetType>
}

internal class URefSetMemoryRegion<SetType>(
    private val setType: SetType,
    private val sort: UBoolSort,
    private var allocatedSetWithAllocatedElements: PersistentMap<UAllocatedRefSetWithAllocatedElementId, UBoolExpr> = persistentMapOf(),
    private var allocatedSetWithInputElements: PersistentMap<UAllocatedRefSetWithInputElementsId<SetType>, UAllocatedRefSetWithInputElements<SetType>> = persistentMapOf(),
    private var inputSetWithAllocatedElements: PersistentMap<UInputRefSetWithAllocatedElementsId<SetType>, UInputRefSetWithAllocatedElements<SetType>> = persistentMapOf(),
    private var inputSetWithInputElements: UInputRefSetWithInputElements<SetType>? = null
) : URefSetRegion<SetType> {
    private fun updateAllocatedSetWithAllocatedElements(
        updated: PersistentMap<UAllocatedRefSetWithAllocatedElementId, UBoolExpr>
    ) = URefSetMemoryRegion(
        setType, sort,
        updated,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements,
        inputSetWithInputElements
    )

    private fun allocatedSetWithInputElementsId(setAddress: UConcreteHeapAddress) =
        UAllocatedRefSetWithInputElementsId(setAddress, setType, sort)

    private fun getAllocatedSetWithInputElements(
        id: UAllocatedRefSetWithInputElementsId<SetType>
    ): UAllocatedRefSetWithInputElements<SetType> {
        var collection = allocatedSetWithInputElements[id]
        if (collection == null) {
            collection = id.emptyRegion()
            allocatedSetWithInputElements = allocatedSetWithInputElements.put(id, collection)
        }
        return collection
    }

    override fun allocatedSetWithInputElements(setRef: UConcreteHeapAddress) =
        getAllocatedSetWithInputElements(allocatedSetWithInputElementsId(setRef))

    private fun updateAllocatedSetWithInputElements(
        id: UAllocatedRefSetWithInputElementsId<SetType>,
        updatedSet: UAllocatedRefSetWithInputElements<SetType>
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements.put(id, updatedSet),
        inputSetWithAllocatedElements,
        inputSetWithInputElements
    )

    private fun inputSetWithAllocatedElementsId(elementAddress: UConcreteHeapAddress) =
        UInputRefSetWithAllocatedElementsId(elementAddress, setType, sort)

    private fun getInputSetWithAllocatedElements(
        id: UInputRefSetWithAllocatedElementsId<SetType>
    ): UInputRefSetWithAllocatedElements<SetType> {
        var collection = inputSetWithAllocatedElements[id]
        if (collection == null) {
            collection = id.emptyRegion()
            inputSetWithAllocatedElements = inputSetWithAllocatedElements.put(id, collection)
        }
        return collection
    }

    private fun updateInputSetWithAllocatedElements(
        id: UInputRefSetWithAllocatedElementsId<SetType>,
        updatedSet: UInputRefSetWithAllocatedElements<SetType>
    ) = URefSetMemoryRegion(
        setType, sort,
        allocatedSetWithAllocatedElements,
        allocatedSetWithInputElements,
        inputSetWithAllocatedElements.put(id, updatedSet),
        inputSetWithInputElements
    )

    override fun inputSetWithInputElements(): UInputRefSetWithInputElements<SetType> {
        if (inputSetWithInputElements == null)
            inputSetWithInputElements = UInputRefSetWithInputElementsId(setType, sort).emptyRegion()
        return inputSetWithInputElements!!
    }

    private fun updateInputSetWithInputElements(updatedSet: UInputRefSetWithInputElements<SetType>) =
        URefSetMemoryRegion(
            setType, sort,
            allocatedSetWithAllocatedElements,
            allocatedSetWithInputElements,
            inputSetWithAllocatedElements,
            updatedSet
        )

    override fun read(key: URefSetEntryLValue<SetType>): UBoolExpr =
        key.setRef.map(
            { concreteRef ->
                key.setElement.map(
                    { concreteElem ->
                        val id = UAllocatedRefSetWithAllocatedElementId(concreteRef.address, concreteElem.address)
                        allocatedSetWithAllocatedElements[id] ?: sort.uctx.falseExpr
                    },
                    { symbolicElem ->
                        val id = allocatedSetWithInputElementsId(concreteRef.address)
                        getAllocatedSetWithInputElements(id).read(symbolicElem)
                    }
                )
            },
            { symbolicRef ->
                key.setElement.map(
                    { concreteElem ->
                        val id = inputSetWithAllocatedElementsId(concreteElem.address)
                        getInputSetWithAllocatedElements(id).read(symbolicRef)
                    },
                    { symbolicElem ->
                        inputSetWithInputElements().read(symbolicRef to symbolicElem)
                    }
                )
            }
        )

    override fun write(
        key: URefSetEntryLValue<SetType>,
        value: UBoolExpr,
        guard: UBoolExpr
    ) = foldHeapRef(
        ref = key.setRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { setRegion, (concreteSetRef, setGuard) ->
            foldHeapRef(
                ref = key.setElement,
                initial = setRegion,
                initialGuard = setGuard,
                blockOnConcrete = { region, (concreteElemRef, guard) ->
                    val id = UAllocatedRefSetWithAllocatedElementId(concreteSetRef.address, concreteElemRef.address)
                    val newMap = region.allocatedSetWithAllocatedElements.guardedWrite(id, value, guard) {
                        sort.uctx.falseExpr
                    }
                    region.updateAllocatedSetWithAllocatedElements(newMap)
                },
                blockOnSymbolic = { region, (symbolicElemRef, guard) ->
                    val id = allocatedSetWithInputElementsId(concreteSetRef.address)
                    val newMap = region.getAllocatedSetWithInputElements(id)
                        .write(symbolicElemRef, value, guard)
                    region.updateAllocatedSetWithInputElements(id, newMap)
                }
            )
        },
        blockOnSymbolic = { setRegion, (symbolicSetRef, setGuard) ->
            foldHeapRef(
                ref = key.setElement,
                initial = setRegion,
                initialGuard = setGuard,
                blockOnConcrete = { region, (concreteElemRef, guard) ->
                    val id = inputSetWithAllocatedElementsId(concreteElemRef.address)
                    val newMap = region.getInputSetWithAllocatedElements(id)
                        .write(symbolicSetRef, value, guard)
                    region.updateInputSetWithAllocatedElements(id, newMap)
                },
                blockOnSymbolic = { region, (symbolicElemRef, guard) ->
                    val newMap = region.inputSetWithInputElements()
                        .write(symbolicSetRef to symbolicElemRef, value, guard)
                    region.updateInputSetWithInputElements(newMap)
                }
            )
        }
    )

    override fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr
    ) = foldHeapRef2(
        ref0 = srcRef,
        ref1 = dstRef,
        initial = this,
        initialGuard = operationGuard,
        blockOnConcrete0Concrete1 = { region, srcConcrete, dstConcrete, guard ->
            val initialAllocatedSetState = region.allocatedSetWithAllocatedElements
            val updatedAllocatedSet = region.unionAllocatedSetAllocatedElements(
                initial = initialAllocatedSetState,
                srcAddress = srcConcrete.address,
                guard = guard,
                read = { initialAllocatedSetState[it] ?: sort.uctx.falseExpr },
                mkDstKeyId = { UAllocatedRefSetWithAllocatedElementId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g) { sort.uctx.falseExpr }
                }
            )
            val updatedRegion = region.updateAllocatedSetWithAllocatedElements(updatedAllocatedSet)

            val srcId = allocatedSetWithInputElementsId(srcConcrete.address)
            val srcCollection = updatedRegion.getAllocatedSetWithInputElements(srcId)

            val dstId = allocatedSetWithInputElementsId(dstConcrete.address)
            val dstCollection = updatedRegion.getAllocatedSetWithInputElements(dstId)

            val adapter = UAllocatedToAllocatedSymbolicRefSetUnionAdapter(srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateAllocatedSetWithInputElements(dstId, updated)
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val initialAllocatedSetState = region.allocatedSetWithAllocatedElements
            val updatedRegion = region.unionAllocatedSetAllocatedElements(
                initial = region, srcAddress = srcConcrete.address, guard = guard,
                read = { initialAllocatedSetState[it] ?: sort.uctx.falseExpr },
                mkDstKeyId = { inputSetWithAllocatedElementsId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputSetWithAllocatedElements(dstKeyId)
                        .write(dstSymbolic, value, g)
                    result.updateInputSetWithAllocatedElements(dstKeyId, newMap)
                }
            )

            val srcId = allocatedSetWithInputElementsId(srcConcrete.address)
            val srcCollection = updatedRegion.getAllocatedSetWithInputElements(srcId)

            val dstCollection = updatedRegion.inputSetWithInputElements()

            val adapter = UAllocatedToInputSymbolicRefSetUnionAdapter(dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateInputSetWithInputElements(updated)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val updatedAllocatedSet = region.unionInputSetAllocatedElements(
                initial = region.allocatedSetWithAllocatedElements,
                guard = guard,
                read = { region.getInputSetWithAllocatedElements(it).read(srcSymbolic) },
                mkDstKeyId = { UAllocatedRefSetWithAllocatedElementId(dstConcrete.address, it) },
                write = { result, dstKeyId, value, g ->
                    result.guardedWrite(dstKeyId, value, g) { sort.uctx.falseExpr }
                }
            )
            val updatedRegion = region.updateAllocatedSetWithAllocatedElements(updatedAllocatedSet)

            val srcCollection = updatedRegion.inputSetWithInputElements()

            val dstId = allocatedSetWithInputElementsId(dstConcrete.address)
            val dstCollection = updatedRegion.getAllocatedSetWithInputElements(dstId)

            val adapter = UInputToAllocatedSymbolicRefSetUnionAdapter(srcSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateAllocatedSetWithInputElements(dstId, updated)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val updatedRegion = region.unionInputSetAllocatedElements(
                initial = region, guard = guard,
                read = { region.getInputSetWithAllocatedElements(it).read(srcSymbolic) },
                mkDstKeyId = { inputSetWithAllocatedElementsId(it) },
                write = { result, dstKeyId, value, g ->
                    val newMap = result.getInputSetWithAllocatedElements(dstKeyId)
                        .write(dstSymbolic, value, g)
                    result.updateInputSetWithAllocatedElements(dstKeyId, newMap)
                }
            )
            val srcCollection = updatedRegion.inputSetWithInputElements()
            val dstCollection = updatedRegion.inputSetWithInputElements()

            val adapter = UInputToInputSymbolicRefSetUnionAdapter(srcSymbolic, dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            updatedRegion.updateInputSetWithInputElements(updated)
        },
    )

    private inline fun <R, DstKeyId> unionInputSetAllocatedElements(
        initial: R,
        guard: UBoolExpr,
        read: (UInputRefSetWithAllocatedElementsId<SetType>) -> UBoolExpr,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ) = unionAllocatedElements(
        initial,
        inputSetWithAllocatedElements.keys,
        guard,
        read,
        { mkDstKeyId(it.elementAddress) },
        write
    )

    private inline fun <R, DstKeyId> unionAllocatedSetAllocatedElements(
        initial: R,
        srcAddress: UConcreteHeapAddress,
        guard: UBoolExpr,
        read: (UAllocatedRefSetWithAllocatedElementId) -> UBoolExpr,
        mkDstKeyId: (UConcreteHeapAddress) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ) = unionAllocatedElements(
        initial,
        allocatedSetWithAllocatedElements.keys.filter { it.setAddress == srcAddress },
        guard,
        read,
        { mkDstKeyId(it.elementAddress) },
        write
    )

    private inline fun <R, SrcKeyId, DstKeyId> unionAllocatedElements(
        initial: R,
        keys: Iterable<SrcKeyId>,
        guard: UBoolExpr,
        read: (SrcKeyId) -> UBoolExpr,
        mkDstKeyId: (SrcKeyId) -> DstKeyId,
        write: (R, DstKeyId, UBoolExpr, UBoolExpr) -> R
    ): R = keys.fold(initial) { result, srcKeyId ->
        val srcContains = read(srcKeyId)

        val mergedGuard = guard.uctx.mkAnd(srcContains, guard)

        write(result, mkDstKeyId(srcKeyId), guard.uctx.trueExpr, mergedGuard)
    }
}
