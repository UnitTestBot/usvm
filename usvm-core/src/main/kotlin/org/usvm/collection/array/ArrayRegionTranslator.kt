package org.usvm.collection.array

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
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.model.UMemory2DArray
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.U2DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import org.usvm.uctx
import java.util.IdentityHashMap

class UArrayRegionDecoder<ArrayType, Sort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionDecoder<UArrayIndexLValue<ArrayType, Sort>, Sort> {

    private val allocatedRegions =
        mutableMapOf<UConcreteHeapAddress, UAllocatedArrayRegionTranslator<ArrayType, Sort>>()

    private var inputRegion: UInputArrayRegionTranslator<ArrayType, Sort>? = null

    fun allocatedArrayRegionTranslator(
        collectionId: UAllocatedArrayId<ArrayType, Sort>
    ): URegionTranslator<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> =
        allocatedRegions.getOrPut(collectionId.address) {
            check(collectionId.arrayType == regionId.arrayType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }

            UAllocatedArrayRegionTranslator(collectionId, exprTranslator)
        }

    fun inputArrayRegionTranslator(
        collectionId: UInputArrayId<ArrayType, Sort>
    ): URegionTranslator<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort> {
        if (inputRegion == null) {
            check(collectionId.arrayType == regionId.arrayType && collectionId.sort == regionId.sort) {
                "Unexpected collection: $collectionId"
            }
            inputRegion = UInputArrayRegionTranslator(collectionId, exprTranslator)
        }
        return inputRegion!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UMemoryRegion<UArrayIndexLValue<ArrayType, Sort>, Sort> =
        UArrayLazyModelRegion(regionId, model, mapping, inputRegion)
}

private class UAllocatedArrayRegionTranslator<ArrayType, Sort : USort>(
    private val collectionId: UAllocatedArrayId<ArrayType, Sort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
    private val initialValue = with(collectionId.sort.uctx) {
        val sort = mkArraySort(sizeSort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<USizeSort, Sort>>>()
    private val updatesTranslator = UAllocatedArrayUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputArrayRegionTranslator<ArrayType, Sort : USort>(
    private val collectionId: UInputArrayId<ArrayType, Sort>,
    private val exprTranslator: UExprTranslator<*>
) : URegionTranslator<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
    UCollectionDecoder<USymbolicArrayIndex, Sort> {
    private val initialValue = with(collectionId.sort.uctx) {
        mkArraySort(addressSort, sizeSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>>()
    private val updatesTranslator = UInputArrayUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicArrayIndex, Sort> =
        UMemory2DArray(initialValue, model, mapping)
}

private class UAllocatedArrayUpdatesTranslator<Sort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArraySort<USizeSort, Sort>>
) : U1DUpdatesTranslator<USizeSort, Sort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, USizeExpr, Sort>
    ): KExpr<KArraySort<USizeSort, Sort>> {
        check(update.adapter is USymbolicArrayCopyAdapter<*, *>) {
            "Unexpected array ranged operation: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateArrayCopy(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, Sort, *>, Any, Sort>,
            update.adapter as USymbolicArrayCopyAdapter<Any, USizeExpr>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> KContext.translateArrayCopy(
        previous: KExpr<KArraySort<USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, USizeExpr, Sort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicArrayCopyAdapter<SrcKey, USizeExpr>
    ): KExpr<KArraySort<USizeSort, Sort>> {
        val key = mkFreshConst("k", previous.sort.domain)

        val keyMapper = sourceCollection.collectionId.keyMapper(exprTranslator)
        val convertedKey = keyMapper(adapter.convert(key))

        val isInside = update.includesSymbolically(key).translated // already includes guard

        val result = sourceCollection.collectionId.instantiate(sourceCollection, convertedKey).translated

        val ite = mkIte(isInside, result, previous.select(key))
        return mkArrayLambda(key.decl, ite)
    }
}

private class UInputArrayUpdatesTranslator<Sort : USort>(
    exprTranslator: UExprTranslator<*>,
    initialValue: KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>
) : U2DUpdatesTranslator<UAddressSort, USizeSort, Sort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, USymbolicArrayIndex, Sort>
    ): KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> {
        check(update.adapter is USymbolicArrayCopyAdapter<*, *>) {
            "Unexpected array ranged operation: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateArrayCopy(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, Sort, *>, Any, Sort>,
            update.adapter as USymbolicArrayCopyAdapter<Any, USymbolicArrayIndex>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> KContext.translateArrayCopy(
        previous: KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, USymbolicArrayIndex, Sort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicArrayCopyAdapter<SrcKey, USymbolicArrayIndex>
    ): KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> {
        val key1 = mkFreshConst("k1", previous.sort.domain0)
        val key2 = mkFreshConst("k2", previous.sort.domain1)

        val keyMapper = sourceCollection.collectionId.keyMapper(exprTranslator)
        val convertedKey = keyMapper(adapter.convert(key1 to key2))

        val isInside = update.includesSymbolically(key1 to key2).translated // already includes guard

        val result = sourceCollection.collectionId.instantiate(sourceCollection, convertedKey).translated

        val ite = mkIte(isInside, result, previous.select(key1, key2))
        return mkArrayLambda(key1.decl, key2.decl, ite)
    }
}
