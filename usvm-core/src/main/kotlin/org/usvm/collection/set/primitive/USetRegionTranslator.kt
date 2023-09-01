package org.usvm.collection.set.primitive

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UMemory2DArray
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.U2DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import org.usvm.util.Region
import java.util.IdentityHashMap

class USetRegionDecoder<SetType, ElementSort : USort, Reg : Region<Reg>>(
    private val regionId: USetRegionId<SetType, ElementSort, Reg>,
    private val exprTranslator: UExprTranslator<*>
) : URegionDecoder<USetEntryLValue<SetType, ElementSort, Reg>, UBoolSort> {
    private val allocatedRegionTranslator =
        mutableMapOf<UAllocatedSetId<SetType, ElementSort, Reg>, UAllocatedSetTranslator<SetType, ElementSort, Reg>>()

    private var inputRegionTranslator: UInputSetTranslator<SetType, ElementSort, Reg>? = null

    fun allocatedSetTranslator(
        collectionId: UAllocatedSetId<SetType, ElementSort, Reg>
    ): URegionTranslator<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> =
        allocatedRegionTranslator.getOrPut(collectionId) {
            UAllocatedSetTranslator(collectionId, exprTranslator)
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

// todo: use uninterpreted functions instead of arrays
private class UAllocatedSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    collectionId: UAllocatedSetId<SetType, ElementSort, Reg>,
    exprTranslator: UExprTranslator<*>
) : URegionTranslator<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(collectionId.elementSort, boolSort)
        mkArrayConst(sort, falseExpr)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<ElementSort, UBoolSort>>>()
    private val updatesTranslator = UAllocatedSetUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedSetId<SetType, ElementSort, Reg>, UExpr<ElementSort>, UBoolSort>,
        key: UExpr<ElementSort>
    ): KExpr<UBoolSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputSetTranslator<SetType, ElementSort : USort, Reg : Region<Reg>>(
    collectionId: UInputSetId<SetType, ElementSort, Reg>,
    exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
    UCollectionDecoder<USymbolicSetElement<ElementSort>, UBoolSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, collectionId.elementSort, boolSort)
        mkArrayConst(sort, falseExpr)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, ElementSort, UBoolSort>>>()
    private val updatesTranslator = UInputSetUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputSetId<SetType, ElementSort, Reg>, USymbolicSetElement<ElementSort>, UBoolSort>,
        key: USymbolicSetElement<ElementSort>
    ): KExpr<UBoolSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicSetElement<ElementSort>, UBoolSort> =
        UMemory2DArray(initialValue, model, mapping)
}

private class UAllocatedSetUpdatesTranslator<ElementSort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArraySort<ElementSort, UBoolSort>>
) : U1DUpdatesTranslator<ElementSort, UBoolSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<ElementSort, UBoolSort>>,
        update: URangedUpdateNode<*, *, UExpr<ElementSort>, UBoolSort>
    ): KExpr<KArraySort<ElementSort, UBoolSort>> {
        check(update.adapter is USymbolicSetUnionAdapter<*, *, UExpr<ElementSort>, *>) {
            "Unexpected adapter: ${update.adapter}"
        }

        val key = mkFreshConst("k", previous.sort.domain)
        val contains = update.includesSymbolically(key, composer = null).translated // already includes guard
        val ite = mkIte(contains, trueExpr, previous.select(key))
        return mkArrayLambda(key.decl, ite)
    }
}

private class UInputSetUpdatesTranslator<ElementSort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArray2Sort<UAddressSort, ElementSort, UBoolSort>>
) : U2DUpdatesTranslator<UAddressSort, ElementSort, UBoolSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, ElementSort, UBoolSort>>,
        update: URangedUpdateNode<*, *, USymbolicSetElement<ElementSort>, UBoolSort>
    ): KExpr<KArray2Sort<UAddressSort, ElementSort, UBoolSort>> {
        check(update.adapter is USymbolicSetUnionAdapter<*, *, USymbolicSetElement<ElementSort>, *>) {
            "Unexpected adapter: ${update.adapter}"
        }

        val key1 = mkFreshConst("k1", previous.sort.domain0)
        val key2 = mkFreshConst("k2", previous.sort.domain1)
        val key = key1 to key2

        val contains = update.includesSymbolically(key, composer = null).translated // already includes guard
        val ite = mkIte(contains, trueExpr, previous.select(key1, key2))
        return mkArrayLambda(key1.decl, key2.decl, ite)
    }
}
