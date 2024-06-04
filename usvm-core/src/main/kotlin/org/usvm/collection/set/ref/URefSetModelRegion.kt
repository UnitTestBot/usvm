package org.usvm.collection.set.ref

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.isFalse
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.mkSizeExpr
import org.usvm.model.UMemory2DArray
import org.usvm.model.UModelEvaluator
import org.usvm.model.modelEnsureConcreteInputRef
import org.usvm.uctx
import org.usvm.withSizeSort

abstract class URefSetModelRegion<SetType>(
    private val regionId: URefSetRegionId<SetType>
) : UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>, URefSetReadOnlyRegion<SetType> {
    abstract val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort>

    override fun read(key: URefSetEntryLValue<SetType>): UBoolExpr {
        val setRef = modelEnsureConcreteInputRef(key.setRef)
        return inputSet.read(setRef to key.setElement)
    }

    override fun setEntries(ref: UHeapRef): URefSetEntries<SetType> {
        val setRef = modelEnsureConcreteInputRef(ref)

        check(inputSet.constValue.isFalse) { "Set model is not complete" }

        val result = URefSetEntries<SetType>()
        inputSet.values.keys.forEach {
            if (it.first == setRef) {
                result.add(URefSetEntryLValue(setRef, it.second, regionId.setType))
            }
        }

        return result
    }

    override fun <SizeSort : USort> setIntersectionSize(firstRef: UHeapRef, secondRef: UHeapRef): UExpr<SizeSort> {
        val firstElements = setEntries(firstRef).entries.map { it.setElement }
        val secondElements = setEntries(secondRef).entries.map { it.setElement }

        val elementsIntersection = firstElements.intersect(secondElements.toHashSet())
        return firstRef.uctx.withSizeSort<SizeSort>().mkSizeExpr(elementsIntersection.size)
    }
}

class URefSetLazyModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>,
    model: UModelEvaluator<*>,
    assertions: List<KExpr<KBoolSort>>,
    inputSetDecoder: USetCollectionDecoder<UAddressSort>
) : URefSetModelRegion<SetType>(regionId) {
    override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort> by lazy {
        inputSetDecoder.decodeCollection(model, assertions)
    }
}

class URefSetEagerModelRegion<SetType>(
    regionId: URefSetRegionId<SetType>, override val inputSet: UMemory2DArray<UAddressSort, UAddressSort, UBoolSort>
) : URefSetModelRegion<SetType>(regionId)
