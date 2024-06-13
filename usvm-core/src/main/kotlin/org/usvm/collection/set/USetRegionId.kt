package org.usvm.collection.set

import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.primitive.USetMemoryRegion
import org.usvm.collection.set.primitive.USetRegion
import org.usvm.collection.set.ref.URefSetMemoryRegion
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.regions.Region
import org.usvm.uctx

sealed interface UAnySetRegionId<SetType, Entry> : UMemoryRegionId<Entry, UBoolSort> {
    val setType: SetType
}

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
    override val setType: SetType,
    val elementInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, Reg>
) : UMemoryRegionId<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>,
    UAnySetRegionId<SetType, USetEntryLValue<SetType, ElementSort, Reg>> {
    override val sort: UBoolSort
        get() = elementSort.uctx.boolSort

    override fun emptyRegion(): USetRegion<SetType, ElementSort, Reg> =
        USetMemoryRegion(setType, elementSort, elementInfo)
}

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
    override val setType: SetType,
    override val sort: UBoolSort
) : UMemoryRegionId<URefSetEntryLValue<SetType>, UBoolSort>,
    UAnySetRegionId<SetType, URefSetEntryLValue<SetType>> {
    override fun emptyRegion(): UMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> =
        URefSetMemoryRegion(setType, sort)
}
