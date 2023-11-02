package org.usvm.collection.set.primitive

import io.ksmt.solver.KModel
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.AddressesMapping
import org.usvm.model.UMemory2DArray
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.regions.Region
import org.usvm.solver.UCollectionDecoder

abstract class USetModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>,
    USetReadOnlyRegion<SetType, ElementSort, Reg> {
    abstract val inputSet: UReadOnlyMemoryRegion<USymbolicSetElement<ElementSort>, UBoolSort>

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }

    override fun setEntries(ref: UHeapRef): USetEntries<SetType, ElementSort, Reg> {
        val setRef = modelEnsureConcreteInputRef(ref)

        // todo: remove this cast
        val inputSetUpdates = inputSet as UMemory2DArray<UAddressSort, ElementSort, UBoolSort>

        val result = USetEntries<SetType, ElementSort, Reg>()
        if (!inputSetUpdates.constValue.isFalse) {
            result.markAsInput()
        }

        inputSetUpdates.values.keys.forEach {
            if (it.first == setRef) {
                result.add(
                    USetEntryLValue(
                        regionId.elementSort,
                        setRef,
                        it.second,
                        regionId.setType,
                        regionId.elementInfo
                    )
                )
            }
        }

        return result
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
