package org.usvm.collection.set.length

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

class USetLengthRegionDecoder<SetType, USizeSort : USort>(
    private val regionId: USetLengthRegionId<SetType, USizeSort>,
    private val exprTranslator: UExprTranslator<*, USizeSort>,
) : URegionDecoder<USetLengthLValue<SetType, USizeSort>, USizeSort> {

    private var inputRegionTranslator: UInputSetLengthRegionTranslator<SetType, USizeSort>? = null

    fun inputSetLengthRegionTranslator(
        collectionId: UInputSetLengthId<SetType, USizeSort>
    ): URegionTranslator<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputSetLengthRegionTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ) = inputRegionTranslator?.let { USetLengthLazyModelRegion(regionId, model, it) }
}

private class UInputSetLengthRegionTranslator<SetType, USizeSort : USort>(
    private val collectionId: UInputSetLengthId<SetType, USizeSort>,
    exprTranslator: UExprTranslator<*, USizeSort>,
) : URegionTranslator<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort>,
    UCollectionDecoder<UHeapRef, USizeSort> {
    private val initialValue = with(exprTranslator.ctx) {
        mkArraySort(addressSort, sizeSort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, USizeSort>>>()
    private val updatesTranslator = UInputSetLengthUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort>,
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

private class UInputSetLengthUpdateTranslator<USizeSort : USort>(
    exprTranslator: UExprTranslator<*, USizeSort>,
    initialValue: KExpr<KArraySort<UAddressSort, USizeSort>>
) : U1DUpdatesTranslator<UAddressSort, USizeSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, USizeSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, USizeSort>
    ): KExpr<KArraySort<UAddressSort, USizeSort>> {
        error("Map length has no ranged updates")
    }
}
