package org.usvm.collection.set.primitive

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.regions.Region

abstract class USetModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>,
    USetReadOnlyRegion<SetType, ElementSort, Reg> {
    abstract val inputSet: UMemory2DArray<UAddressSort, ElementSort, UBoolSort>

    override fun read(key: USetEntryLValue<SetType, ElementSort, Reg>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }

    override fun setEntries(ref: UHeapRef): UPrimitiveSetEntries<SetType, ElementSort, Reg> = with(regionId) {
        val setRef = modelEnsureConcreteInputRef(ref)

        check(inputSet.constValue.isFalse) { "Set model is not complete" }

        val result = UPrimitiveSetEntries<SetType, ElementSort, Reg>()
        inputSet.values.keys().forEach {
            if (it.first == setRef) {
                result.add(USetEntryLValue(elementSort, setRef, it.second, setType, elementInfo))
            }
        }

        return result
    }
}

class USetLazyModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    regionId: USetRegionId<SetType, ElementSort, Reg>,
    model: UModelEvaluator<*>,
    assertions: List<KExpr<KBoolSort>>,
    inputSetDecoder: USetCollectionDecoder<ElementSort>
) : USetModelRegion<SetType, ElementSort, Reg>(regionId) {
    override val inputSet: UMemory2DArray<UAddressSort, ElementSort, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, assertions)
    }
}

class USetEagerModelRegion<SetType, ElementSort : USort, Reg : Region<Reg>>(
    regionId: USetRegionId<SetType, ElementSort, Reg>,
    override val inputSet: UMemory2DArray<UAddressSort, ElementSort, UBoolSort>
) : USetModelRegion<SetType, ElementSort, Reg>(regionId)
