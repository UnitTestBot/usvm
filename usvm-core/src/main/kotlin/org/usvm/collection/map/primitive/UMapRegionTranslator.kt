package org.usvm.collection.map.primitive

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArraySort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UMemory2DArray
import org.usvm.regions.Region
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.U2DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
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
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ) = inputRegionTranslator?.let { UMapLazyModelRegion(regionId, model, mapping, it) }
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
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort> =
        UMemory2DArray(initialValue, model, mapping)
}

private class UAllocatedMapUpdatesTranslator<KeySort : USort, ValueSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArraySort<KeySort, ValueSort>>
) : U1DUpdatesTranslator<KeySort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, UExpr<KeySort>, ValueSort>
    ): KExpr<KArraySort<KeySort, ValueSort>> {

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

private class UInputMapUpdatesTranslator<KeySort : USort, ValueSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>
) : U2DUpdatesTranslator<UAddressSort, KeySort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, Pair<UExpr<UAddressSort>, UExpr<KeySort>>, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>> {
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
