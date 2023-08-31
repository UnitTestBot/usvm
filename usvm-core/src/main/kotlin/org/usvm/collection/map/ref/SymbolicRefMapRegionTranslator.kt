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

class URefMapRegionDecoder<MapType, ValueSort : USort>(
    private val regionId: URefMapRegionId<MapType, ValueSort>,
    private val exprTranslator: UExprTranslator<*>
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
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            UAllocatedRefMapWithInputKeysTranslator(collectionId, exprTranslator)
        }

    fun inputRefMapWithAllocatedKeysTranslator(
        collectionId: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>
    ): URegionTranslator<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> =
        inputWithAllocatedKeysRegions.getOrPut(collectionId.keyAddress) {
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            UInputRefMapWithAllocatedKeysTranslator(collectionId, exprTranslator)
        }

    fun inputRefMapTranslator(
        collectionId: UInputRefMapWithInputKeysId<MapType, ValueSort>
    ): URegionTranslator<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort> {
        if (inputRegionTranslator == null) {
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            inputRegionTranslator = UInputRefMapTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ) = inputRegionTranslator?.let { URefMapLazyModelRegion(regionId, model, mapping, it) }
}

private class UAllocatedRefMapWithInputKeysTranslator<MapType, ValueSort : USort>(
    private val collectionId: UAllocatedRefMapWithInputKeysId<MapType, ValueSort>,
    exprTranslator: UExprTranslator<*>
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
    private val collectionId: UInputRefMapWithAllocatedKeysId<MapType, ValueSort>,
    exprTranslator: UExprTranslator<*>
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
    private val collectionId: UInputRefMapWithInputKeysId<MapType, ValueSort>,
    exprTranslator: UExprTranslator<*>
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
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicMapKey<UAddressSort>, ValueSort> =
        UMemory2DArray(initialValue, model, mapping)
}

private class UAllocatedRefMapUpdatesTranslator<ValueSort : USort>(
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

private class UInputRefMapUpdatesTranslator<ValueSort : USort>(
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
