package org.usvm.collection.set.ref

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.collection.set.UAllocatedSetUpdatesTranslator
import org.usvm.collection.set.UInputSetUpdatesTranslator
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UMemory2DArray
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import java.util.IdentityHashMap

class URefSetRegionDecoder<SetType>(
    private val regionId: URefSetRegionId<SetType>,
    private val exprTranslator: UExprTranslator<*, *>
) : URegionDecoder<URefSetEntryLValue<SetType>, UBoolSort> {
    private val allocatedWithInputRegionTranslator =
        mutableMapOf<UAllocatedRefSetWithInputElementsId<SetType>, UAllocatedRefSetWithInputElementsTranslator<SetType>>()

    private val inputWithAllocatedRegionTranslator =
        mutableMapOf<UInputRefSetWithAllocatedElementsId<SetType>, UInputRefSetWithAllocatedElementsTranslator<SetType>>()

    private var inputWithInputRegionTranslator: UInputRefSetWithInputElementsTranslator<SetType>? = null

    fun allocatedRefSetWithInputElementsTranslator(
        collectionId: UAllocatedRefSetWithInputElementsId<SetType>
    ): URegionTranslator<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort> =
        allocatedWithInputRegionTranslator.getOrPut(collectionId) {
            UAllocatedRefSetWithInputElementsTranslator(exprTranslator)
        }

    fun inputRefSetWithAllocatedElementsTranslator(
        collectionId: UInputRefSetWithAllocatedElementsId<SetType>
    ): URegionTranslator<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort> =
        inputWithAllocatedRegionTranslator.getOrPut(collectionId) {
            UInputRefSetWithAllocatedElementsTranslator(exprTranslator)
        }

    fun inputRefSetTranslator(
        collectionId: UInputRefSetWithInputElementsId<SetType>
    ): URegionTranslator<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort> {
        if (inputWithInputRegionTranslator == null) {
            inputWithInputRegionTranslator = UInputRefSetWithInputElementsTranslator(collectionId, exprTranslator)
        }
        return inputWithInputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>? =
        inputWithInputRegionTranslator?.let { URefSetLazyModelRegion(regionId, model, mapping, it) }
}

private class UAllocatedRefSetWithInputElementsTranslator<SetType>(
    private val exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort> {
    override fun translateReading(
        region: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef
    ): KExpr<UBoolSort> {
        val updatesTranslator = UAllocatedSetUpdatesTranslator(exprTranslator, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }
}

private class UInputRefSetWithAllocatedElementsTranslator<SetType>(
    private val exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort> {
    override fun translateReading(
        region: USymbolicCollection<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef
    ): KExpr<UBoolSort> {
        val updatesTranslator = UAllocatedSetUpdatesTranslator(exprTranslator, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }
}

private class UInputRefSetWithInputElementsTranslator<SetType>(
    collectionId: UInputRefSetWithInputElementsId<SetType>,
    private val exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
    UCollectionDecoder<USymbolicSetElement<UAddressSort>, UBoolSort> {
    private val initialFunction = with(collectionId.sort.uctx) {
        mkFuncDecl(collectionId.toString(), boolSort, listOf(addressSort, addressSort))
    }

    override fun translateReading(
        region: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        key: USymbolicSetElement<UAddressSort>
    ): KExpr<UBoolSort> {
        val updatesTranslator = UInputSetUpdatesTranslator(exprTranslator, initialFunction, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicSetElement<UAddressSort>, UBoolSort> =
        UMemory2DArray.fromFunction(initialFunction, model, mapping, defaultValue = exprTranslator.ctx.falseExpr)
}
