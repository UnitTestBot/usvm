package org.usvm.collection.set.ref

import io.ksmt.solver.KModel
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.solver.UCollectionDecoder

abstract class URefSetModelRegion<SetType>(
    private val regionId: URefSetRegionId<SetType>
) : UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    abstract val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<UAddressSort>, UBoolSort>

    override fun read(key: URefSetEntryLValue<SetType>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }
}

class URefSetLazyModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>,
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val inputSetDecoder: UCollectionDecoder<USymbolicSetElement<UAddressSort>, UBoolSort>
) : URefSetModelRegion<SetType>(regionId) {
    override val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<UAddressSort>, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, addressesMapping)
    }
}

class URefSetEagerModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>,
    override val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<UAddressSort>, UBoolSort>
) : URefSetModelRegion<SetType>(regionId)
