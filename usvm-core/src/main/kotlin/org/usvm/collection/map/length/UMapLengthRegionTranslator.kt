package org.usvm.collection.map.length

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.model.UModelEvaluator
import org.usvm.sizeSort
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import java.util.IdentityHashMap

class UMapLengthRegionDecoder<MapType, USizeSort : USort>(
    private val regionId: UMapLengthRegionId<MapType, USizeSort>,
    private val exprTranslator: UExprTranslator<*, USizeSort>,
) : URegionDecoder<UMapLengthLValue<MapType, USizeSort>, USizeSort> {

    private var inputRegionTranslator: UInputMapLengthRegionTranslator<MapType, USizeSort>? = null

    fun inputMapLengthRegionTranslator(
        collectionId: UInputMapLengthId<MapType, USizeSort>
    ): URegionTranslator<UInputMapLengthId<MapType, USizeSort>, UHeapRef, USizeSort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputMapLengthRegionTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ) = inputRegionTranslator?.let { UMapLengthLazyModelRegion(regionId, model, it) }
}

private class UInputMapLengthRegionTranslator<MapType, USizeSort : USort>(
    private val collectionId: UInputMapLengthId<MapType, USizeSort>,
    exprTranslator: UExprTranslator<*, USizeSort>,
) : URegionTranslator<UInputMapLengthId<MapType, USizeSort>, UHeapRef, USizeSort>,
    UCollectionDecoder<UHeapRef, USizeSort> {
    private val initialValue = with(exprTranslator.ctx) {
        mkArraySort(addressSort, sizeSort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, USizeSort>>>()
    private val updatesTranslator = UInputMapLengthUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputMapLengthId<MapType, USizeSort>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): KExpr<USizeSort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: UModelEvaluator<*>
    ): UReadOnlyMemoryRegion<UHeapRef, USizeSort> =
        model.evalAndCompleteArray1DMemoryRegion(initialValue.decl)
}

private class UInputMapLengthUpdateTranslator<USizeSort : USort>(
    exprTranslator: UExprTranslator<*, USizeSort>,
    initialValue: KExpr<KArraySort<UAddressSort, USizeSort>>
) : U1DUpdatesTranslator<UAddressSort, USizeSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, USizeSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, *, USizeSort>
    ): KExpr<KArraySort<UAddressSort, USizeSort>> {
        error("Map length has no ranged updates")
    }
}
