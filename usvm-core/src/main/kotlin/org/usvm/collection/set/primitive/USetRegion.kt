package org.usvm.collection.set.primitive

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.foldHeapRef
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.map
import org.usvm.regions.Region
import org.usvm.uctx

data class USetEntryLValue<SetType, ElementSort : USort, Reg : Region<Reg>>(
    val elementSort: ElementSort,
    val setRef: UHeapRef,
    val setElement: UExpr<ElementSort>,
    val setType: SetType,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>
) : ULValue<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = elementSort.uctx.boolSort

    override val memoryRegionId: UMemoryRegionId<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>
        get() = USetRegionId(elementSort, setType, elementInfo)

    override val key: USetEntryLValue<SetType, ElementSort, Reg>
        get() = this
}

data class USetRegionId<SetType, ElementSort : USort, Reg : Region<Reg>>(
    val elementSort: ElementSort,
    val setType: SetType,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>
) : UMemoryRegionId<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    override val sort: UBoolSort
        get() = elementSort.uctx.boolSort

    override fun emptyRegion(): USetRegion<SetType, ElementSort, Reg> =
        USetMemoryRegion(setType, elementSort, elementInfo)
}

typealias UAllocatedSet<SetType, ElementSort, Reg> =
        USymbolicCollection<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>

typealias UInputSet<SetType, ElementSort, Reg> =
        USymbolicCollection<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>

interface USetRegion<SetType, ElementSort : USort, Reg : Region<Reg>> :
    UMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {

    fun allocatedSetElements(address: UConcreteHeapAddress): UAllocatedSet<SetType, ElementSort, Reg>

    fun inputSetElements(): UInputSet<SetType, ElementSort, Reg>

    fun union(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        operationGuard: UBoolExpr,
    ): USetRegion<SetType, ElementSort, Reg>
}

internal class USetMemoryRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val setType: SetType,
    private val elementSort: ElementSort,
    private val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>,
    private var allocatedSets: PersistentMap<UAllocatedSetId<SetType, ElementSort, Reg>, UAllocatedSet<SetType, ElementSort, Reg>> = persistentMapOf(),
    private var inputSet: UInputSet<SetType, ElementSort, Reg>? = null,
) : USetRegion<SetType, ElementSort, Reg> {
    init {
        check(elementSort != elementSort.uctx.addressSort) {
            "Ref set must be used to handle sets with ref elements"
        }
    }

    override fun allocatedSetElements(address: UConcreteHeapAddress): UAllocatedSet<SetType, ElementSort, Reg> =
        getAllocatedSet(UAllocatedSetId(address, elementSort, setType, elementInfo))

    private fun getAllocatedSet(
        id: UAllocatedSetId<SetType, ElementSort, Reg>
    ): UAllocatedSet<SetType, ElementSort, Reg> {
        var collection = allocatedSets[id]
        if (collection == null) {
            collection = id.emptyRegion()
            allocatedSets = allocatedSets.put(id, collection)
        }
        return collection
    }

    private fun updateAllocatedSet(
        id: UAllocatedSetId<SetType, ElementSort, Reg>,
        updated: UAllocatedSet<SetType, ElementSort, Reg>
    ) = USetMemoryRegion(setType, elementSort, elementInfo, allocatedSets.put(id, updated), inputSet)

    override fun inputSetElements(): UInputSet<SetType, ElementSort, Reg> {
        if (inputSet == null) {
            inputSet = UInputSetId(elementSort, setType, elementInfo).emptyRegion()
        }
        return inputSet!!
    }

    private fun updateInputSet(updated: UInputSet<SetType, ElementSort, Reg>) =
        USetMemoryRegion(setType, elementSort, elementInfo, allocatedSets, updated)

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UExpr<UBoolSort> =
        key.setRef.map(
            { concreteRef -> allocatedSetElements(concreteRef.address).read(key.setElement) },
            { symbolicRef -> inputSetElements().read(symbolicRef to key.setElement) }
        )

    override fun write(
        key: USetEntryLValue<SetType, ElementSort, Reg>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr
    ) = foldHeapRef(
        ref = key.setRef,
        initial = this,
        initialGuard = guard,
        blockOnConcrete = { region, (concreteRef, guard) ->
            val id = UAllocatedSetId(concreteRef.address, elementSort, setType, elementInfo)
            val newCollection = region.getAllocatedSet(id)
                .write(key.setElement, value, guard)
            region.updateAllocatedSet(id, newCollection)
        },
        blockOnSymbolic = { region, (symbolicRef, guard) ->
            val newCollection = region.inputSetElements()
                .write(symbolicRef to key.setElement, value, guard)
            region.updateInputSet(newCollection)
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
            val srcId = UAllocatedSetId(srcConcrete.address, elementSort, setType, elementInfo)
            val srcCollection = region.getAllocatedSet(srcId)

            val dstId = UAllocatedSetId(dstConcrete.address, elementSort, setType, elementInfo)
            val dstCollection = region.getAllocatedSet(dstId)

            val adapter = UAllocatedToAllocatedSymbolicSetUnionAdapter(srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedSet(dstId, updated)
        },
        blockOnConcrete0Symbolic1 = { region, srcConcrete, dstSymbolic, guard ->
            val srcId = UAllocatedSetId(srcConcrete.address, elementSort, setType, elementInfo)
            val srcCollection = region.getAllocatedSet(srcId)

            val dstCollection = region.inputSetElements()

            val adapter = UAllocatedToInputSymbolicSetUnionAdapter(dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputSet(updated)
        },
        blockOnSymbolic0Concrete1 = { region, srcSymbolic, dstConcrete, guard ->
            val srcCollection = region.inputSetElements()

            val dstId = UAllocatedSetId(dstConcrete.address, elementSort, setType, elementInfo)
            val dstCollection = region.getAllocatedSet(dstId)

            val adapter = UInputToAllocatedSymbolicSetUnionAdapter(srcSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateAllocatedSet(dstId, updated)
        },
        blockOnSymbolic0Symbolic1 = { region, srcSymbolic, dstSymbolic, guard ->
            val srcCollection = region.inputSetElements()
            val dstCollection = region.inputSetElements()

            val adapter = UInputToInputSymbolicSetUnionAdapter(srcSymbolic, dstSymbolic, srcCollection)
            val updated = dstCollection.copyRange(srcCollection, adapter, guard)
            region.updateInputSet(updated)
        },
    )
}
