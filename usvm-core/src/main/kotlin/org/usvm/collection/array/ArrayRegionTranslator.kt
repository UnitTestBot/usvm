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
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.model.UMemory2DArray
import org.usvm.sizeSort
import org.usvm.solver.U1DUpdatesTranslator
import org.usvm.solver.U2DUpdatesTranslator
import org.usvm.solver.UCollectionDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.URegionTranslator
import java.util.IdentityHashMap

class UArrayRegionDecoder<ArrayType, Sort : USort, USizeSort : USort>(
    private val regionId: UArrayRegionId<ArrayType, Sort, USizeSort>,
    private val ctx: UContext<USizeSort>,
    private val exprTranslator: UExprTranslator<*, *>,
) : URegionDecoder<UArrayIndexLValue<ArrayType, Sort, USizeSort>, Sort> {

    private val allocatedRegions =
        mutableMapOf<UConcreteHeapAddress, UAllocatedArrayRegionTranslator<ArrayType, Sort, USizeSort>>()

    private var inputRegionTranslator: UInputArrayRegionTranslator<ArrayType, Sort, USizeSort>? = null

    fun allocatedArrayRegionTranslator(
        collectionId: UAllocatedArrayId<ArrayType, Sort, USizeSort>
    ): URegionTranslator<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort> =
        allocatedRegions.getOrPut(collectionId.address) {
            UAllocatedArrayRegionTranslator(collectionId, ctx, exprTranslator)
        }

    fun inputArrayRegionTranslator(
        collectionId: UInputArrayId<ArrayType, Sort, USizeSort>
    ): URegionTranslator<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort> {
        if (inputRegionTranslator == null) {
            inputRegionTranslator = UInputArrayRegionTranslator(collectionId, ctx, exprTranslator)
        }
        return inputRegionTranslator!!
    }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ) = inputRegionTranslator?.let { UArrayLazyModelRegion(regionId, model, mapping, it) }
}

private class UAllocatedArrayRegionTranslator<ArrayType, Sort : USort, USizeSort : USort>(
    private val collectionId: UAllocatedArrayId<ArrayType, Sort, USizeSort>,
    ctx: UContext<USizeSort>,
    exprTranslator: UExprTranslator<*, *>
) : URegionTranslator<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort> {
    private val initialValue = with(ctx) {
        val sort = mkArraySort(sizeSort, collectionId.sort)
        val translatedDefaultValue = exprTranslator.translate(collectionId.defaultValue)
        mkArrayConst(sort, translatedDefaultValue)
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArraySort<USizeSort, Sort>>>()
    private val updatesTranslator = UAllocatedArrayUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>,
        key: UExpr<USizeSort>
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }
}

private class UInputArrayRegionTranslator<ArrayType, Sort : USort, USizeSort : USort>(
    private val collectionId: UInputArrayId<ArrayType, Sort, USizeSort>,
    ctx: UContext<USizeSort>,
    exprTranslator: UExprTranslator<*, *>,
) : URegionTranslator<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>,
    UCollectionDecoder<USymbolicArrayIndex<USizeSort>, Sort> {
    private val initialValue = with(ctx) {
        mkArraySort(addressSort, sizeSort, collectionId.sort).mkConst(collectionId.toString())
    }

    private val visitorCache = IdentityHashMap<Any?, KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>>()
    private val updatesTranslator = UInputArrayUpdatesTranslator(exprTranslator, initialValue)

    override fun translateReading(
        region: USymbolicCollection<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>,
        key: USymbolicArrayIndex<USizeSort>
    ): KExpr<Sort> {
        val translatedCollection = region.updates.accept(updatesTranslator, visitorCache)
        return updatesTranslator.visitSelect(translatedCollection, key)
    }

    override fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>
    ): UReadOnlyMemoryRegion<USymbolicArrayIndex<USizeSort>, Sort> =
        UMemory2DArray(initialValue, model, mapping)
}

private class UAllocatedArrayUpdatesTranslator<Sort : USort, USizeSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArraySort<USizeSort, Sort>>
) : U1DUpdatesTranslator<USizeSort, Sort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArraySort<USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, UExpr<USizeSort>, Sort>
    ): KExpr<KArraySort<USizeSort, Sort>> {
        check(update.adapter is USymbolicArrayCopyAdapter<*, *, *>) {
            "Unexpected array ranged operation: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateArrayCopy(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, Sort, *>, Any, Sort>,
            update.adapter as USymbolicArrayCopyAdapter<Any, UExpr<USizeSort>, USizeSort>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> KContext.translateArrayCopy(
        previous: KExpr<KArraySort<USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, UExpr<USizeSort>, Sort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicArrayCopyAdapter<SrcKey, UExpr<USizeSort>, USizeSort>
    ): KExpr<KArraySort<USizeSort, Sort>> {
        val key = mkFreshConst("k", previous.sort.domain)

        val keyInfo = sourceCollection.collectionId.keyInfo()
        val convertedKey = keyInfo.mapKey(adapter.convert(key, composer = null), exprTranslator)

        val isInside = update.includesSymbolically(key, composer = null).translated // already includes guard

        val result = sourceCollection.collectionId.instantiate(
            sourceCollection, convertedKey, composer = null
        ).translated

        val ite = mkIte(isInside, result, previous.select(key))
        return mkArrayLambda(key.decl, ite)
    }
}

private class UInputArrayUpdatesTranslator<Sort : USort, USizeSort : USort>(
    exprTranslator: UExprTranslator<*, *>,
    initialValue: KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>
) : U2DUpdatesTranslator<UAddressSort, USizeSort, Sort>(exprTranslator, initialValue) {
    override fun KContext.translateRangedUpdate(
        previous: KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, USymbolicArrayIndex<USizeSort>, Sort>
    ): KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> {
        check(update.adapter is USymbolicArrayCopyAdapter<*, *, *>) {
            "Unexpected array ranged operation: ${update.adapter}"
        }

        @Suppress("UNCHECKED_CAST")
        return translateArrayCopy(
            previous,
            update,
            update.sourceCollection as USymbolicCollection<USymbolicCollectionId<Any, Sort, *>, Any, Sort>,
            update.adapter as USymbolicArrayCopyAdapter<Any, USymbolicArrayIndex<USizeSort>, USizeSort>
        )
    }

    private fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> KContext.translateArrayCopy(
        previous: KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>>,
        update: URangedUpdateNode<*, *, USymbolicArrayIndex<USizeSort>, Sort>,
        sourceCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicArrayCopyAdapter<SrcKey, USymbolicArrayIndex<USizeSort>, USizeSort>
    ): KExpr<KArray2Sort<UAddressSort, USizeSort, Sort>> {
        val key1 = mkFreshConst("k1", previous.sort.domain0)
        val key2 = mkFreshConst("k2", previous.sort.domain1)

        val keyInfo = sourceCollection.collectionId.keyInfo()
        val convertedKey = keyInfo.mapKey(adapter.convert(key1 to key2, composer = null), exprTranslator)

        val isInside = update.includesSymbolically(key1 to key2, composer = null).translated // already includes guard

        val result = sourceCollection.collectionId.instantiate(
            sourceCollection, convertedKey, composer = null
        ).translated

        val ite = mkIte(isInside, result, previous.select(key1, key2))
        return mkArrayLambda(key1.decl, key2.decl, ite)
    }
}
