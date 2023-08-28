package org.usvm.collection.map.length

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArraySort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UMemory1DArray
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import java.util.IdentityHashMap

class UMapLengthRegionDecoder<MapType>(
    private val regionId: UMapLengthRegionId<MapType>,
    private val exprTranslator: UExprTranslator<*>
) : URegionDecoder<UMapLengthLValue<MapType>, USizeSort> {

    private var inputTranslator: UInputMapLengthRegionTranslator<MapType>? = null

    fun inputMapLengthRegionTranslator(
        collectionId: UInputMapLengthId<MapType>
    ): URegionTranslator<UInputMapLengthId<MapType>, UHeapRef, USizeSort> {
        if (inputTranslator == null) {
            check(collectionId.mapType == regionId.mapType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }
            inputTranslator = UInputMapLengthRegionTranslator(collectionId, exprTranslator)
        }
        return inputTranslator!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UMemoryRegion<UMapLengthLValue<MapType>, USizeSort> =
        UMapLengthLazyModelRegion(regionId, model, mapping, inputTranslator)
}

private class UInputMapLengthRegionTranslator<MapType>(
    private val collectionId: UInputMapLengthId<MapType>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputMapLengthId<MapType>, UHeapRef, USizeSort>,
    UCollectionDecoder<UHeapRef, USizeSort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, sizeSort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, USizeSort>>>()
    private val updatesTranslator = UInputMapLengthUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputMapLengthId<MapType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): KExpr<USizeSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<UHeapRef, USizeSort> =
        UMemory1DArray(initialValue, model, mapping)
}

private class UInputMapLengthUpdateTranslator(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArraySort<UAddressSort, USizeSort>>
) : U1DUpdatesTranslator<UAddressSort, USizeSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, USizeSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, USizeSort>
    ): KExpr<KArraySort<UAddressSort, USizeSort>> {
        error("Map length has no ranged updates")
    }
}
