package org.usvm.collection.map.ref

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UHeapRef
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
import java.util.IdentityHashMap

class URefMapRegionDecoder<MapType, ValueSort : USort>(
    private val regionId: URefMapRegionId<MapType, ValueSort>,
    private val exprTranslator: UExprTranslator<*, *>
) : URegionDecoder<URefMapEntryLValue<MapType, ValueSort>, ValueSort> {
    private val allocatedWithInputKeysRegions =
        mutableMapOf<UConcreteHeapAddress, UAllocatedRefMapWithInputKeysTranslator<MapType, ValueSort>>()

    private val inputWithAllocatedKeysRegions =
        mutableMapOf<UConcreteHeapAddress, UInputRefMapWithAllocatedKeysTranslator<MapType, ValueSort>>()

    private var inputRegionTranslator: UInputRefMapTranslator<MapType, ValueSort>? = null

    fun allocatedRefMapWithInputKeysTranslator(
        collectionId: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>
    ): URegionTranslator<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort> =
        allocatedWithInputKeysRegions.getOrPut(collectionId.mapAddress) {
            UAllocatedRefMapWithInputKeysTranslator(collectionId, exprTranslator)
        }

    fun inputRefMapWithAllocatedKeysTranslator(
        collectionId: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>
    ): URegionTranslator<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> =
        inputWithAllocatedKeysRegions.getOrPut(collectionId.keyAddress) {
            UInputRefMapWithAllocatedKeysTranslator(collectionId, exprTranslator)
        }

    fun inputRefMapTranslator(
        collectionId: UInputRefMapWithInputKeysId<MapType, ValueSort>
    ): URegionTranslator<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputRefMapTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ) = inputRegionTranslator?.let { URefMapLazyModelRegion(regionId, model, it) }
}

private class UAllocatedRefMapWithInputKeysTranslator<MapType, ValueSort : USort>(
    collectionId: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, ValueSort>>>()
    private val updatesTranslator = UAllocatedRefMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputRefMapWithAllocatedKeysTranslator<MapType, ValueSort : USort>(
    collectionId: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, ValueSort>>>()
    private val updatesTranslator = UAllocatedRefMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputRefMapTranslator<MapType, ValueSort : USort>(
    collectionId: UInputRefMapWithInputKeysId<MapType, ValueSort>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
    UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, addressSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>>()
    private val updatesTranslator = UInputRefMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
        key: USymbolicMapKey<UAddressSort>
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: UModelEvaluator<*>
    ): UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort> =
        model.evalAndCompleteArray2DMemoryRegion(initialValue.decl)
}

private class UAllocatedRefMapUpdatesTranslator<ValueSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArraySort<UAddressSort, ValueSort>>
) : U1DUpdatesTranslator<UAddressSort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, ValueSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, ValueSort, ValueSort>
    ): KExpr<KArraySort<UAddressSort, ValueSort>> {
        check(update.adapter is USymbolicRefMapMergeAdapter<*, *, UHeapRef, *, ValueSort>) {
            "Unexpected adapter: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateRefMapMerge(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, ValueSort, *>, Any, ValueSort>,
            update.adapter as USymbolicRefMapMergeAdapter<*, Any, UHeapRef, *, ValueSort>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, ValueSort, CollectionId>, SrcKey> KContext.translateRefMapMerge(
        previous: KExpr<KArraySort<UAddressSort, ValueSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, ValueSort, ValueSort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>,
        adapter: USymbolicRefMapMergeAdapter<*, SrcKey, UHeapRef, *, ValueSort>
    ): KExpr<KArraySort<UAddressSort, ValueSort>> {
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

private class UInputRefMapUpdatesTranslator<ValueSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>
) : U2DUpdatesTranslator<UAddressSort, UAddressSort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>,
        update: URangedUpdateNode<*, *, USymbolicMapKey<UAddressSort>, ValueSort, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>> {
        check(update.adapter is USymbolicRefMapMergeAdapter<*, *, USymbolicMapKey<UAddressSort>, *, ValueSort>) {
            "Unexpected adapter: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateRefMapMerge(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, ValueSort, *>, Any, ValueSort>,
            update.adapter as USymbolicRefMapMergeAdapter<*, Any, USymbolicMapKey<UAddressSort>, *, ValueSort>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, ValueSort, CollectionId>, SrcKey> KContext.translateRefMapMerge(
        previous: KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>,
        update: URangedUpdateNode<*, *, USymbolicMapKey<UAddressSort>, ValueSort, ValueSort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>,
        adapter: USymbolicRefMapMergeAdapter<*, SrcKey, USymbolicMapKey<UAddressSort>, *, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>> {
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
