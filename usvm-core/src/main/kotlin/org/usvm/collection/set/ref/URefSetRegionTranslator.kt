package org.usvm.collection.set.ref

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
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
            UAllocatedRefSetWithInputElementsTranslator(collectionId, exprTranslator)
        }

    fun inputRefSetWithAllocatedElementsTranslator(
        collectionId: UInputRefSetWithAllocatedElementsId<SetType>
    ): URegionTranslator<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort> =
        inputWithAllocatedRegionTranslator.getOrPut(collectionId) {
            UInputRefSetWithAllocatedElementsTranslator(collectionId, exprTranslator)
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

// todo: use uninterpreted functions instead of arrays
private class UAllocatedRefSetWithInputElementsTranslator<SetType>(
    collectionId: UAllocatedRefSetWithInputElementsId<SetType>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, boolSort)
        mkArrayConst(sort, falseExpr)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, UBoolSort>>>()
    private val updatesTranslator = UAllocatedRefSetUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef
    ): KExpr<UBoolSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputRefSetWithAllocatedElementsTranslator<SetType>(
    collectionId: UInputRefSetWithAllocatedElementsId<SetType>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, boolSort)
        mkArrayConst(sort, falseExpr)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, UBoolSort>>>()
    private val updatesTranslator = UAllocatedRefSetUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputRefSetWithAllocatedElementsId<SetType>, UHeapRef, UBoolSort>,
        key: UHeapRef
    ): KExpr<UBoolSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputRefSetWithInputElementsTranslator<SetType>(
    collectionId: UInputRefSetWithInputElementsId<SetType>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
    UCollectionDecoder<USymbolicSetElement<UAddressSort>, UBoolSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, addressSort, boolSort)
        mkArrayConst(sort, falseExpr)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, UAddressSort, UBoolSort>>>()
    private val updatesTranslator = UInputRefSetUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>,
        key: USymbolicSetElement<UAddressSort>
    ): KExpr<UBoolSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicSetElement<UAddressSort>, UBoolSort> =
        UMemory2DArray(initialValue, model, mapping)
}

private class UAllocatedRefSetUpdatesTranslator(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArraySort<UAddressSort, UBoolSort>>
) : U1DUpdatesTranslator<UAddressSort, UBoolSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, UBoolSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, UBoolSort>
    ): KExpr<KArraySort<UAddressSort, UBoolSort>> {
        check(update.adapter is USymbolicRefSetUnionAdapter<*, *, UHeapRef, *>) {
            "Unexpected adapter: ${update.adapter}"
        }

        val key = mkFreshConst("k", previous.sort.domain)
        val contains = update.includesSymbolically(key, composer = null).translated // already includes guard
        val ite = mkIte(contains, trueExpr, previous.select(key))
        return mkArrayLambda(key.decl, ite)
    }
}

private class UInputRefSetUpdatesTranslator(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArray2Sort<UAddressSort, UAddressSort, UBoolSort>>
) : U2DUpdatesTranslator<UAddressSort, UAddressSort, UBoolSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, UAddressSort, UBoolSort>>,
        update: URangedUpdateNode<*, *, USymbolicSetElement<UAddressSort>, UBoolSort>
    ): KExpr<KArray2Sort<UAddressSort, UAddressSort, UBoolSort>> {
        check(update.adapter is USymbolicRefSetUnionAdapter<*, *, USymbolicSetElement<UAddressSort>, *>) {
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
