package org.usvm.collection.field

import io.ksmt.KContext
import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KArraySort
import io.ksmt.utils.mkConst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.USort
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

class UFieldRegionDecoder<Field, Sort : USort>(
    private val regionId: UFieldsRegionId<Field, Sort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionDecoder<UFieldLValue<Field, Sort>, Sort> {
    private var inputRegionTranslator: UInputFieldRegionTranslator<Field, Sort>? = null

    fun inputFieldRegionTranslator(
        collectionId: UInputFieldId<Field, Sort>
    ): URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort> {
        check(collectionId.field == regionId.field && collectionId.sort == regionId.sort) {
            "Unexpected collection: $collectionId"
        }

        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputFieldRegionTranslator(collectionId, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UMemoryRegion<UFieldLValue<Field, Sort>, Sort> {
        return UFieldsLazyModelRegion(regionId, model, mapping, inputRegionTranslator)
    }
}

private class UInputFieldRegionTranslator<Field, Sort : USort>(
    private val collectionId: UInputFieldId<Field, Sort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputFieldId<Field, Sort>, UHeapRef, Sort>, UCollectionDecoder<UHeapRef, Sort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<UAddressSort, Sort>>>()
    private val updatesTranslator = UInputFieldUpdateTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<UHeapRef, Sort> {
        return UMemory1DArray(initialValue, model, mapping)
    }
}

private class UInputFieldUpdateTranslator<Sort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArraySort<UAddressSort, Sort>>
) : U1DUpdatesTranslator<UAddressSort, Sort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<UAddressSort, Sort>>,
        update: URangedUpdateNode<*, *, UHeapRef, Sort>
    ): KExpr<KArraySort<UAddressSort, Sort>> {
        error("Fields has no ranged updates")
    }
}
