package org.usvm.solver.translator

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
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.id.UAllocatedSymbolicMapId
import org.usvm.memory.collection.id.UInputSymbolicMapId
import org.usvm.memory.collection.key.USymbolicMapKey
import org.usvm.memory.collection.region.USymbolicMapEntryRef
import org.usvm.memory.collection.region.USymbolicMapRegionId
import org.usvm.model.UMemory1DArray
import org.usvm.model.UMemory2DArray
import org.usvm.model.region.USymbolicMapLazyModelRegion
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.U2DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import org.usvm.util.Region
import java.util.*

class USymbolicMapRegionDecoder<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val regionId: USymbolicMapRegionId<MapType, KeySort, ValueSort, Reg>,
    private val exprTranslator: UExprTranslator<*>
) : URegionDecoder<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> {
    private val allocatedRegions =
        mutableMapOf<UConcreteHeapAddress, UAllocatedSymbolicMapTranslator<MapType, KeySort, ValueSort, Reg>>()

    private var inputRegion: UInputSymbolicMapTranslator<MapType, KeySort, ValueSort, Reg>? = null

    fun allocatedSymbolicMapTranslator(
        collectionId: UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>
    ): URegionTranslator<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort> =
        allocatedRegions.getOrPut(collectionId.address) {
            check(
                collectionId.mapType == regionId.mapType
                        && collectionId.keySort == regionId.keySort
                        && collectionId.sort == regionId.sort
            ) {
                "Unexpected collection: $collectionId"
            }

            UAllocatedSymbolicMapTranslator(collectionId, exprTranslator)
        }

    fun inputSymbolicMapTranslator(
        collectionId: UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>
    ): URegionTranslator<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort> {
        if (inputRegion == null) {
            check(
                collectionId.mapType == regionId.mapType
                        && collectionId.keySort == regionId.keySort
                        && collectionId.sort == regionId.sort
            ) {
                "Unexpected collection: $collectionId"
            }

            inputRegion = UInputSymbolicMapTranslator(collectionId, exprTranslator)
        }
        return inputRegion!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UMemoryRegion<USymbolicMapEntryRef<MapType, KeySort, ValueSort, Reg>, ValueSort> {
        return USymbolicMapLazyModelRegion(regionId, model, mapping, allocatedRegions, inputRegion)
    }
}

private class UAllocatedSymbolicMapTranslator<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val collectionId: UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
    UCollectionDecoder<UExpr<KeySort>, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(collectionId.keySort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<KeySort, ValueSort>>>()
    private val updatesTranslator = UAllocatedSymbolicMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<UExpr<KeySort>, ValueSort> {
        return UMemory1DArray(initialValue, model, mapping)
    }
}

private class UInputSymbolicMapTranslator<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>>(
    private val collectionId: UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
    UCollectionDecoder<USymbolicMapKey<KeySort>, ValueSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, collectionId.keySort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>>()
    private val updatesTranslator = UInputSymbolicMapUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>
    ): KExpr<ValueSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicMapKey<KeySort>, ValueSort> {
        return UMemory2DArray(initialValue, model, mapping)
    }
}

private class UAllocatedSymbolicMapUpdatesTranslator<KeySort : USort, ValueSort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArraySort<KeySort, ValueSort>>
) : U1DUpdatesTranslator<KeySort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, UExpr<KeySort>, ValueSort>
    ): KExpr<KArraySort<KeySort, ValueSort>> {
        TODO("Not yet implemented")
    }
}

private class UInputSymbolicMapUpdatesTranslator<KeySort : USort, ValueSort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>
) : U2DUpdatesTranslator<UAddressSort, KeySort, ValueSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>>,
        update: URangedUpdateNode<*, *, Pair<UExpr<UAddressSort>, UExpr<KeySort>>, ValueSort>
    ): KExpr<KArray2Sort<UAddressSort, KeySort, ValueSort>> {
        TODO("Not yet implemented")
    }
}
