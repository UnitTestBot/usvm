package org.usvm.collection.set.primitive

import io.ksmt.solver.KModel
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder
import org.usvm.regions.Region

abstract class USetModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    abstract val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<ElementSort>, UBoolSort>

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }
}

class USetLazyModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    regionId: USetRegionId<SetType, ElementSort, Reg>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputSetDecoder: UCollectionDecoder<USymbolicSetElement<ElementSort>, UBoolSort>
) : USetModelRegion<SetType, ElementSort, Reg>(regionId) {
    override val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<ElementSort>, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, addressesMapping)
    }
}

class USetEagerModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    regionId: USetRegionId<SetType, ElementSort, Reg>,
    override val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<ElementSort>, UBoolSort>
) : USetModelRegion<SetType, ElementSort, Reg>(regionId)
