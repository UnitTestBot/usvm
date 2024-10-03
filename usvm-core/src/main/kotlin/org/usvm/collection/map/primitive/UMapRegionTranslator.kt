package org.usvm.collection.map.primitive

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.model.UModelEvaluator
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.U2DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import org.usvm.regions.Region
import java.util.IdentityHashMap

class UMapRegionDecoder<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: UMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val exprTranslator: UExprTranslator<*, *>
) : URegionDecoder<UMapEntryLValue<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    private val allocatedRegions =
        mutableMapOf<UConcreteHeapAddress, UAllocatedMapTranslator<MapType, KeySort, ValueSort, Reg>>()

    private var inputRegionTranslator: UInputMapTranslator<MapType, KeySort, ValueSort, Reg>? = null

    fun allocatedMapTranslator(
        collectionId: UAllocatedMapId<MapType, KeySort, ValueSort, Reg>
    ): URegionTranslator<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort> =
        allocatedRegions.getOrPut(collectionId.address) {
            UAllocatedMapTranslator(collectionId, exprTranslator)
        }

    fun inputMapTranslator(
        collectionId: UInputMapId<MapType, KeySort, ValueSort, Reg>
    ): URegionTranslator<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputMapTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ) = inputRegionTranslator?.let { UMapLazyModelRegion(regionId, model, it) }
}

private class UAllocatedMapTranslator<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    collectionId: UAllocatedMapId<MapType, KeySort, ValueSort, Reg>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(collectionId.keySort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<KeySort, ValueSort>>>()
    private val updatesTranslator = UAllocatedMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputMapTranslator<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    collectionId: UInputMapId<MapType, KeySort, ValueSort, Reg>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
    UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, collectionId.keySort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>>()
    private val updatesTranslator = UInputMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: UModelEvaluator<*>
    ): UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort> =
        model.evalAndCompleteArray2DMemoryRegion(initialValue.decl)
}

private class UAllocatedMapUpdatesTranslator<KeySort : USort, ValueSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArraySort<KeySort, ValueSort>>
) : U1DUpdatesTranslator<KeySort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, UExpr<KeySort>, *, ValueSort>
    ): KExpr<KArraySort<KeySort, ValueSort>> {
        check(update.adapter is USymbolicMapMergeAdapter<*, *, UExpr<KeySort>, *, *>) {
            "Unexpected adapter: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateMapMerge(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, ValueSort, *>, Any, ValueSort>,
            update.adapter as USymbolicMapMergeAdapter<*, Any, UExpr<KeySort>, *, ValueSort>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, ValueSort, CollectionId>, SrcKey> KContext.translateMapMerge(
        previous: KExpr<KArraySort<KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, UExpr<KeySort>, *, ValueSort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>,
        adapter: USymbolicMapMergeAdapter<*, SrcKey, UExpr<KeySort>, *, ValueSort>
    ): KExpr<KArraySort<KeySort, ValueSort>> {
        val key = mkFreshConst("k", previous.sort.domain)

        val srcKeyInfo = sourceCollection.collectionId.keyInfo()
        val convertedKey = srcKeyInfo.mapKey(adapter.convertKey(key, composer = null), exprTranslator)

        val isInside = update.includesSymbolically(key, composer = null).translated // already includes guard

        val result = sourceCollection.collectionId.instantiate(
            sourceCollection, convertedKey, composer = null
        ).translated

        val ite = mkIte(isInside, result, previous.select(key))
        return mkArrayLambda(key.decl, ite)
    }
}

private class UInputMapUpdatesTranslator<KeySort : USort, ValueSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>
) : U2DUpdatesTranslator<UAddressSort, KeySort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, USymbolicMapKey<KeySort>, ValueSort, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>> {
        check(update.adapter is USymbolicMapMergeAdapter<*, *, USymbolicMapKey<KeySort>, *, ValueSort>) {
            "Unexpected adapter: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateMapMerge(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, ValueSort, *>, Any, ValueSort>,
            update.adapter as USymbolicMapMergeAdapter<*, Any, USymbolicMapKey<KeySort>, *, ValueSort>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, ValueSort, CollectionId>, SrcKey> KContext.translateMapMerge(
        previous: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, USymbolicMapKey<KeySort>, ValueSort, ValueSort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>,
        adapter: USymbolicMapMergeAdapter<*, SrcKey, USymbolicMapKey<KeySort>, *, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>> {
        val key1 = mkFreshConst("k1", previous.sort.domain0)
        val key2 = mkFreshConst("k2", previous.sort.domain1)

        val srcKeyInfo = sourceCollection.collectionId.keyInfo()
        val convertedKey = srcKeyInfo.mapKey(adapter.convertKey(key1 to key2, composer = null), exprTranslator)

        val isInside = update.includesSymbolically(key1 to key2, composer = null).translated // already includes guard

        val result = sourceCollection.collectionId.instantiate(
            sourceCollection, convertedKey, composer = null
        ).translated

        val ite = mkIte(isInside, result, previous.select(key1, key2))
        return mkArrayLambda(key1.decl, key2.decl, ite)
    }
}
