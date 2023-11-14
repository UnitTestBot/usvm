package org.usvm.collection.set.primitive

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.UAllocatedSetUpdatesTranslator
import org.usvm.collection.set.UInputSetUpdatesTranslator
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UMemory2DArray
import org.usvm.regions.Region
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import java.util.IdentityHashMap

class USetRegionDecoder<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>,
    private val exprTranslator: UExprTranslator<*, *>
) : URegionDecoder<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    private val allocatedRegionTranslator =
        mutableMapOf<UAllocatedSetId<SetType, ElementSort, Reg>, UAllocatedSetTranslator<SetType, ElementSort, Reg>>()

    private var inputRegionTranslator: UInputSetTranslator<SetType, ElementSort, Reg>? = null

    fun allocatedSetTranslator(
        collectionId: UAllocatedSetId<SetType, ElementSort, Reg>
    ): URegionTranslator<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> =
        allocatedRegionTranslator.getOrPut(collectionId) {
            UAllocatedSetTranslator(exprTranslator)
        }

    fun inputSetTranslator(
        collectionId: UInputSetId<SetType, ElementSort, Reg>
    ): URegionTranslator<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputSetTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort>? =
        inputRegionTranslator?.let { USetLazyModelRegion(regionId, model, mapping, it) }
}

private class UAllocatedSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> {
    override fun translateReading(
        region: USymbolicCollection<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>,
        key: UExpr<ElementSort>
    ): KExpr<UBoolSort> {
        val updatesTranslator = UAllocatedSetUpdatesTranslator(exprTranslator, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }
}

private class UInputSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    collectionId: UInputSetId<SetType, ElementSort, Reg>,
    private val exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
    UCollectionDecoder<USymbolicSetElement<ElementSort>, UBoolSort> {
    private val initialFunction = with(collectionId.sort.uctx) {
        mkFuncDecl(collectionId.toString(), boolSort, listOf(addressSort, collectionId.elementSort))
    }

    override fun translateReading(
        region: USymbolicCollection<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
        key: USymbolicSetElement<ElementSort>
    ): KExpr<UBoolSort> {
        val updatesTranslator = UInputSetUpdatesTranslator(exprTranslator, initialFunction, key)
        return region.updates.accept(updatesTranslator, IdentityHashMap())
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicSetElement<ElementSort>, UBoolSort> =
        UMemory2DArray.fromFunction(initialFunction, model, mapping, defaultValue = exprTranslator.ctx.falseExpr)
}
