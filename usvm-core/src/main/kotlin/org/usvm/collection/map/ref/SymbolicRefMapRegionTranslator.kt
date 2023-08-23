package org.usvm.collection.map.ref

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.UMemoryRegion
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

class USymbolicRefMapRegionDecoder<MapType, ValueSort : USort>(
    private val regionId: USymbolicRefMapRegionId<MapType, ValueSort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionDecoder<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> {
    private val allocatedWithInputKeysRegions =
        mutableMapOf<UConcreteHeapAddress, UAllocatedSymbolicRefMapWithInputKeysTranslator<MapType, ValueSort>>()

    private val inputWithAllocatedKeysRegions =
        mutableMapOf<UConcreteHeapAddress, UInputSymbolicRefMapWithAllocatedKeysTranslator<MapType, ValueSort>>()

    private var inputRegion: UInputSymbolicRefMapTranslator<MapType, ValueSort>? = null

    fun allocatedSymbolicRefMapWithInputKeysTranslator(
        collectionId: UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>
    ): URegionTranslator<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort> =
        allocatedWithInputKeysRegions.getOrPut(collectionId.mapAddress) {
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            UAllocatedSymbolicRefMapWithInputKeysTranslator(collectionId, exprTranslator)
        }

    fun inputSymbolicRefMapWithAllocatedKeysTranslator(
        collectionId: UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>
    ): URegionTranslator<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> =
        inputWithAllocatedKeysRegions.getOrPut(collectionId.keyAddress) {
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            UInputSymbolicRefMapWithAllocatedKeysTranslator(collectionId, exprTranslator)
        }

    fun inputSymbolicRefMapTranslator(
        collectionId: UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>
    ): URegionTranslator<UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort> {
        if (inputRegion == null) {
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            inputRegion = UInputSymbolicRefMapTranslator(collectionId, exprTranslator)
        }
        return inputRegion!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UMemoryRegion<USymbolicRefMapEntryRef<MapType, ValueSort>, ValueSort> {
        return USymbolicRefMapLazyModelRegion(regionId, model, mapping, inputRegion)
    }
}

private class UAllocatedSymbolicRefMapWithInputKeysTranslator<MapType, ValueSort : USort>(
    private val collectionId: UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, ValueSort>>>()
    private val updatesTranslator = UAllocatedSymbolicRefMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputSymbolicRefMapWithAllocatedKeysTranslator<MapType, ValueSort : USort>(
    private val collectionId: UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(addressSort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, ValueSort>>>()
    private val updatesTranslator = UAllocatedSymbolicRefMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputSymbolicRefMapTranslator<MapType, ValueSort : USort>(
    private val collectionId: UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
    UCollectionDecoder<USymbolicMapKey<UAddressSort>, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, addressSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>>()
    private val updatesTranslator = UInputSymbolicRefMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
        key: USymbolicMapKey<UAddressSort>
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort> {
        return UMemory2DArray(initialValue, model, mapping)
    }
}

private class UAllocatedSymbolicRefMapUpdatesTranslator<ValueSort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArraySort<UAddressSort, ValueSort>>
) : U1DUpdatesTranslator<UAddressSort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, ValueSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, ValueSort>
    ): KExpr<KArraySort<UAddressSort, ValueSort>> {

//            is UMergeUpdateNode<*, *, *, *, *, *> -> {
//                when(update.guard){
//                    falseExpr -> previous
//                    else -> {
//                        @Suppress("UNCHECKED_CAST")
//                        update as UMergeUpdateNode<USymbolicMapId<Any?, KeySort, *, Sort, *>, Any?, Any?, KeySort, *, Sort>
//
//                        val key = mkFreshConst("k", previous.sort.domain)
//
//                        val from = update.sourceCollection
//
//                        val keyMapper = from.collectionId.keyMapper(exprTranslator)
//                        val convertedKey = keyMapper(update.keyConverter.convert(key))
//                        val isInside = update.includesSymbolically(key).translated // already includes guard
//                        val result = exprTranslator.translateRegionReading(from, convertedKey)
//                        val ite = mkIte(isInside, result, previous.select(key))
//                        mkArrayLambda(key.decl, ite)
//                    }
//                }
//            }
        TODO("Not yet implemented")
    }
}

private class UInputSymbolicRefMapUpdatesTranslator<ValueSort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>
) : U2DUpdatesTranslator<UAddressSort, UAddressSort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>>,
        update: URangedUpdateNode<*, *, Pair<UHeapRef, UHeapRef>, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, UAddressSort, ValueSort>> {
        //            is UMergeUpdateNode<*, *, *, *, *, *> -> {
//                when(update.guard){
//                    falseExpr -> previous
//                    else -> {
//                        @Suppress("UNCHECKED_CAST")
//                        update as UMergeUpdateNode<USymbolicMapId<Any?, *, *, Sort, *>, Any?, Any?, *, *, Sort>
//
//                        val key1 = mkFreshConst("k1", previous.sort.domain0)
//                        val key2 = mkFreshConst("k2", previous.sort.domain1)
//
//                        val region = update.sourceCollection
//                        val keyMapper = region.collectionId.keyMapper(exprTranslator)
//                        val convertedKey = keyMapper(update.keyConverter.convert(key1 to key2))
//                        val isInside = update.includesSymbolically(key1 to key2).translated // already includes guard
//                        val result = exprTranslator.translateRegionReading(region, convertedKey)
//                        val ite = mkIte(isInside, result, previous.select(key1, key2))
//                        mkArrayLambda(key1.decl, key2.decl, ite)
//                    }
//                }
//            }
        TODO("Not yet implemented")
    }
}
