package org.usvm.collection.set.primitive

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementsCollector
import org.usvm.collection.set.USymbolicSetEntries
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.foldHeapRef2
import org.usvm.memory.foldHeapRefWithStaticAsSymbolic
import org.usvm.memory.mapWithStaticAsSymbolic
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.regions.Region
import org.usvm.uctx
import org.usvm.withSizeSort

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

typealias UPrimitiveSetEntries<SetType, ElementSort, Reg> = USymbolicSetEntries<USetEntryLValue<SetType, ElementSort, Reg>>

interface USetReadOnlyRegion<SetType, ElementSort : USort, Reg : Region<Reg>> :
    UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg>
    fun <SizeSort : USort> setIntersectionSize(firstRef: UHeapRef, secondRef: UHeapRef): UExpr<SizeSort>
}

interface USetRegion<SetType, ElementSort : USort, Reg : Region<Reg>> :
    USetReadOnlyRegion<SetType, ElementSort, Reg>,
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
    private var allocatedSets: PersistentMap<UAllocatedSetId<SetType, ElementSort, Reg>, UAllocatedSet<SetType, ElementSort, Reg>> = persistentHashMapOf(),
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
        key.setRef.mapWithStaticAsSymbolic(
            { concreteRef -> allocatedSetElements(concreteRef.address).read(key.setElement) },
            { symbolicRef -> inputSetElements().read(symbolicRef to key.setElement) }
        )

    override fun write(
        key: USetEntryLValue<SetType, ElementSort, Reg>,
        value: UExpr<UBoolSort>,
        guard: UBoolExpr
    ) = foldHeapRefWithStaticAsSymbolic(
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

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg> =
        foldHeapRefWithStaticAsSymbolic(
            ref = ref,
            initial = UPrimitiveSetEntries(),
            initialGuard = ref.uctx.trueExpr,
            blockOnConcrete = { entries, (concreteRef, _) ->
                val setElements = allocatedSetElements(concreteRef.address)
                val elements = USymbolicSetElementsCollector.collect(setElements.updates)
                elements.elements.forEach { elem ->
                    entries.add(USetEntryLValue(elementSort, concreteRef, elem, setType, elementInfo))
                }

                if (elements.isInput) {
                    entries.markAsInput()
                }

                entries
            },
            blockOnSymbolic = { entries, (symbolicRef, _) ->
                val setElements = inputSetElements()
                val elements = USymbolicSetElementsCollector.collect(setElements.updates)
                elements.elements.forEach { elem ->
                    entries.add(USetEntryLValue(elementSort, symbolicRef, elem.second, setType, elementInfo))
                }

                entries.markAsInput()

                entries
            }
        )

    override fun <SizeSort : USort> setIntersectionSize(firstRef: UHeapRef, secondRef: UHeapRef): UExpr<SizeSort> =
        with(firstRef.uctx.withSizeSort<SizeSort>()) {
            firstRef.mapWithStaticAsSymbolic(
                concreteMapper = { firstConcrete ->
                    tryComputeConcreteIntersectionSize(firstConcrete, secondRef) { firstSetCollection ->
                        secondRef.mapWithStaticAsSymbolic(
                            concreteMapper = { secondConcrete ->
                                tryComputeConcreteIntersectionSize(
                                    secondConcrete,
                                    firstConcrete
                                ) { secondSetCollection ->
                                    mkAllocatedWithAllocatedSetIntersectionSizeExpr(
                                        firstConcrete.address, secondConcrete.address,
                                        firstSetCollection, secondSetCollection
                                    )
                                }
                            },
                            symbolicMapper = { secondSymbolic ->
                                val secondSetCollection = inputSetElements()
                                mkAllocatedWithInputSetIntersectionSizeExpr(
                                    firstConcrete.address, secondSymbolic,
                                    firstSetCollection, secondSetCollection
                                )
                            }
                        )
                    }
                },
                symbolicMapper = { firstSymbolic ->
                    secondRef.mapWithStaticAsSymbolic(
                        concreteMapper = { secondConcrete ->
                            tryComputeConcreteIntersectionSize(secondConcrete, firstSymbolic) { secondSetCollection ->
                                val firstSetCollection = inputSetElements()
                                mkAllocatedWithInputSetIntersectionSizeExpr(
                                    secondConcrete.address, firstSymbolic,
                                    secondSetCollection, firstSetCollection
                                )
                            }
                        },
                        symbolicMapper = { secondSymbolic ->
                            val firstSetCollection = inputSetElements()
                            val secondSetCollection = inputSetElements()
                            mkInputWithInputSetIntersectionSizeExpr(
                                firstSymbolic, secondSymbolic,
                                firstSetCollection, secondSetCollection
                            )
                        }
                    )
                },
            )
        }

    private inline fun <SizeSort : USort> UContext<SizeSort>.tryComputeConcreteIntersectionSize(
        firstRef: UConcreteHeapRef,
        secondRef: UHeapRef,
        onInputElements: (UAllocatedSet<SetType, ElementSort, Reg>) -> UExpr<SizeSort>
    ): UExpr<SizeSort> {
        val firstSetCollection = allocatedSetElements(firstRef.address)
        val firstSetElements = USymbolicSetElementsCollector.collect(firstSetCollection.updates)

        if (!firstSetElements.isInput) {
            return computeIntersectionSize(firstRef, secondRef, firstSetElements.elements)
        }

        return onInputElements(firstSetCollection)
    }

    private fun <SizeSort : USort> UContext<SizeSort>.computeIntersectionSize(
        firstRef: UHeapRef,
        secondRef: UHeapRef,
        elements: Iterable<UExpr<ElementSort>>
    ): UExpr<SizeSort> = elements.fold(mkSizeExpr(0)) { size, element ->
        val firstContains = read(USetEntryLValue(elementSort, firstRef, element, setType, elementInfo))
        val secondContains = read(USetEntryLValue(elementSort, secondRef, element, setType, elementInfo))
        val intersectionContains = mkAnd(firstContains, secondContains)

        mkIte(intersectionContains, mkSizeAddExpr(size, mkSizeExpr(1)), size)
    }
}
