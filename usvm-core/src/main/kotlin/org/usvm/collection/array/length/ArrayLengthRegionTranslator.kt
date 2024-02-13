package org.usvm.collection.array.length

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

class UArrayLengthRegionDecoder<ArrayType, USizeSort : USort>(
    private val regionId: UArrayLengthsRegionId<ArrayType, USizeSort>,
    private val exprTranslator: UExprTranslator<*, USizeSort>,
) : URegionDecoder<UArrayLengthLValue<ArrayType, USizeSort>, USizeSort> {

    private var inputArrayLengthTranslator: UInputArrayLengthRegionTranslator<ArrayType, USizeSort>? = null

    fun inputArrayLengthRegionTranslator(
        collectionId: UInputArrayLengthId<ArrayType, USizeSort>
    ): URegionTranslator<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort> {
        if (inputArrayLengthTranslator == null) {
            inputArrayLengthTranslator = UInputArrayLengthRegionTranslator(collectionId, exprTranslator)
        }
        return inputArrayLengthTranslator!!
    }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<KExpr<KBoolSort>>
    ) = inputArrayLengthTranslator?.let { UArrayLengthLazyModelRegion(regionId, model, it) }
}

private class UInputArrayLengthRegionTranslator<ArrayType, USizeSort : USort>(
    private val collectionId: UInputArrayLengthId<ArrayType, USizeSort>,
    exprTranslator: UExprTranslator<*, USizeSort>,
) : URegionTranslator<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort>,
    UCollectionDecoder<UHeapRef, USizeSort> {
    private val initialValue = with(exprTranslator.ctx) {
        mkArraySort(addressSort, sizeSort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, USizeSort>>>()
    private val updatesTranslator = UInputArrayLengthUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort>,
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

private class UInputArrayLengthUpdateTranslator<USizeSort : USort>(
    exprTranslator: UExprTranslator<*, USizeSort>,
    initialValue: KExpr<KArraySort<UAddressSort, USizeSort>>
) : U1DUpdatesTranslator<UAddressSort, USizeSort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, USizeSort>>,
        update: URangedUpdateNode<*, *, UHeapRef, USizeSort>
    ): KExpr<KArraySort<UAddressSort, USizeSort>> {
        error("Array length has no ranged updates")
    }
}
