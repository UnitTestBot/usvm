package org.usvm.collection.set.ref

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UHeapRef
import org.usvm.collection.set.UAllocatedSetUpdatesTranslator
import org.usvm.collection.set.UInputSetUpdatesTranslator
import org.usvm.collection.set.URefSetEntryLValue
import org.usvm.collection.set.URefSetRegionId
import org.usvm.collection.set.USetCollectionDecoder
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UModelEvaluator
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
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ): UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>? =
        inputWithInputRegionTranslator?.let { URefSetLazyModelRegion(regionId, model, assertions, it) }
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
    USetCollectionDecoder<UAddressSort>() {
    override val inputFunction = with(collectionId.sort.uctx) {
        mkFuncDecl(collectionId.toString(), boolSort, listOf(addressSort, addressSort))
    }

    override fun translateReading(
        region: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        key: USymbolicSetElement<UAddressSort>
    ): KExpr<UBoolSort> {
        val updatesTranslator = UInputSetUpdatesTranslator(exprTranslator, inputFunction, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }
}
