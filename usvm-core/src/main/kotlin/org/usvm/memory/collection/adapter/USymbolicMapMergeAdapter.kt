package org.usvm.memory.collection.adapter

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.isTrue
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.id.KeyMapper
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.memory.collection.id.USymbolicMapId
import org.usvm.memory.collection.id.USymbolicSetId
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.util.Region

class USymbolicMapMergeAdapter<SrcKey, DstKey>(
    val dstKey: DstKey,
    override val srcKey: SrcKey,
    val setOfKeys: USymbolicCollection<USymbolicSetId<SrcKey, *>, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    @Suppress("UNCHECKED_CAST")
    override fun convert(key: DstKey): SrcKey =
        when (srcKey) {
            is UExpr<*> ->
                when (dstKey) {
                    is UExpr<*> -> key as SrcKey
                    is Pair<*, *> -> (key as Pair<*, *>).second as SrcKey
                    else -> error("Unexpected symbolic map key $dstKey")
                }

            is Pair<*, *> ->
                when (dstKey) {
                    is UExpr<*> -> (srcKey.first to key) as SrcKey
                    is Pair<*, *> -> (srcKey.first to (key as Pair<*, *>).second) as SrcKey
                    else -> error("Unexpected symbolic map key $dstKey")
                }

            else -> error("Unexpected symbolic map key $srcKey")
        }

    override fun includesConcretely(key: DstKey) =
        includesSymbolically(key).isTrue

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val srcKey = convert(key)
        return setOfKeys.read(srcKey) // ???
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ) =
        false

    @Suppress("UNCHECKED_CAST")
    override fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>? {
        val mappedDstKey = dstKeyMapper(dstKey) ?: return null

        @Suppress("NAME_SHADOWING")
        val srcCollectionId = srcCollectionId as USymbolicMapId<*, MappedSrcKey, *, USymbolicSetId<MappedSrcKey, *>, *>
        val mappedKeys = setOfKeys.mapTo(composer, srcCollectionId.keysSetId)
        if (mappedSrcKey === srcKey && mappedDstKey == dstKey) {
            return this as USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>
        }
        return USymbolicMapMergeAdapter(mappedDstKey, mappedSrcKey, mappedKeys.uncheckedCast())
    }

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String =
        "(merge $collection)"

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    ) {

        TODO("Not yet implemented")
    }

    override fun <Reg : Region<Reg>> region(): Reg =
        convertRegion(setOfKeys.collectionId.region(setOfKeys.updates))

    private fun <Reg : Region<Reg>> convertRegion(srcReg: Reg): Reg =
        srcReg // TODO: implement valid region conversion logic
}


//class UMergeUpdateNode<
//        CollectionId : USymbolicMapId<SrcKey, KeySort, Reg, ValueSort, CollectionId>,
//        SrcKey,
//        DstKey,
//        KeySort : USort,
//        Reg : Region<Reg>,
//        ValueSort : USort>(
//    override val sourceCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>,
//    val keyIncludesCheck: UMergeKeyIncludesCheck<SrcKey, KeySort, *>,
//    override val keyConverter: UMergeKeyConverter<SrcKey, DstKey>,
//    override val guard: UBoolExpr
//) : USymbolicCollectionUpdate<SrcKey, DstKey, ValueSort,
//        USymbolicCollection<CollectionId, SrcKey, ValueSort>,
//        UMergeKeyConverter<SrcKey, DstKey>> {
//
//    override fun includesConcretely(key: DstKey, precondition: UBoolExpr): Boolean {
//        val srcKey = keyConverter.convert(key)
//        val keyIncludes = keyIncludesCheck.check(srcKey)
//        return (keyIncludes === keyIncludes.ctx.trueExpr) && (guard == guard.ctx.trueExpr || precondition == guard)
//    }
//
//    override fun isIncludedByUpdateConcretely(update: UUpdateNode<DstKey, ValueSort>): Boolean = false
//
//    override fun includesSymbolically(key: DstKey): UBoolExpr {
//        val srcKey = keyConverter.convert(key)
//        val keyIncludes = keyIncludesCheck.check(srcKey)
//        return keyIncludes.ctx.mkAnd(keyIncludes, guard)
//    }
//
//    override fun changeCollection(newCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>) =
//        UMergeUpdateNode(newCollection, keyIncludesCheck, keyConverter, guard)
//
//    override fun <Field, Type> map(
//        keyTransformer: KeyTransformer<DstKey>,
//        composer: UComposer<Field, Type>
//    ): UUpdateNode<DstKey, ValueSort> {
//        val mappedCollection = sourceCollection.map(composer)
//        val mappedKeyConverter = keyConverter.map(composer)
//        val mappedIncludesCheck = keyIncludesCheck.map(composer)
//        val mappedGuard = composer.compose(guard)
//
//        if (mappedCollection === sourceCollection
//            && mappedKeyConverter === keyConverter
//            && mappedIncludesCheck === keyIncludesCheck
//            && mappedGuard == guard
//        ) {
//            return this
//        }
//
//        return UMergeUpdateNode(mappedCollection, mappedIncludesCheck, mappedKeyConverter, mappedGuard)
//    }
//
//    override fun toString(): String = "(merge $sourceCollection)"
//}

///**
// * Used when copying data from allocated array to another allocated array.
// */
//class UAllocatedToAllocatedArrayAdapter(
//    private val srcFromIndex: USizeExpr,
//    private val dstFromIndex: USizeExpr,
//    private val dstToIndex: USizeExpr
//) : USymbolicArrayAdapter<USizeExpr, USizeExpr>(/*srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex*/) {
//    override fun convert(key: USizeExpr): USizeExpr = convertIndex(key, srcFromIndex, dstFromIndex)
//    override val fromKey: USizeExpr = dstFromIndex
//    override val toKey: USizeExpr = dstToIndex
//    override val srcKey: USizeExpr = srcFromIndex
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UAllocatedToAllocatedArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort : USort> map(
//        dstKeyMapper: KeyMapper<USizeExpr, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USizeExpr, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromIndex = dstKeyMapper(dstFromIndex) ?: return null
//        val mappedDstToIndex = dstKeyMapper(dstToIndex) ?: return null
//        val mappedSrcFromIndex = composer.compose(srcFromIndex)
//        // collectionId is already an allocated one, so no need to rebind it...
//
//        if (srcFromIndex == mappedSrcFromIndex && dstFromIndex == mappedDstFromIndex && dstToIndex == mappedDstToIndex) {
//            return (this as USymbolicCollectionAdapter<USizeExpr, MappedDstKey>) to collectionId
//        }
//
//        return UAllocatedToAllocatedArrayAdapter(
//            mappedSrcFromIndex,
//            mappedDstFromIndex as USizeExpr,
//            mappedDstToIndex as USizeExpr
//        ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//    }
//}
//
///**
// * Used when copying data from allocated array to input one.
// */
//class UAllocatedToInputArrayAdapter(
//    private val srcFromIndex: USizeExpr,
//    private val dstFromSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstToSymbolicArrayIndex: USymbolicArrayIndex
//) : USymbolicArrayAdapter<USizeExpr, USymbolicArrayIndex>(
////    srcSymbolicArrayIndex,
////    dstFromSymbolicArrayIndex,
////    dstToIndex
//) {
//    init {
//        require(dstFromSymbolicArrayIndex.first == dstToSymbolicArrayIndex.first)
//    }
//
//    override fun convert(key: USymbolicArrayIndex): USizeExpr =
//        convertIndex(key.second, srcFromIndex, dstFromSymbolicArrayIndex.second)
//
//    override val fromKey: USymbolicArrayIndex = dstFromSymbolicArrayIndex
//    override val toKey: USymbolicArrayIndex = dstToSymbolicArrayIndex
//    override val srcKey: USizeExpr = srcFromIndex
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UAllocatedToInputArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort: USort> map(
//        dstKeyMapper: KeyMapper<USymbolicArrayIndex, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USizeExpr, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromSymbolicArrayIndex = dstKeyMapper(dstFromSymbolicArrayIndex) ?: return null
//        val mappedDstToSymbolicArrayIndex = dstKeyMapper(dstToSymbolicArrayIndex) ?: return null
//        val mappedSrcFromIndex = composer.compose(srcFromIndex)
//        // collectionId is already an allocated one, so no need to rebind it...
//
//        if (srcFromIndex == mappedSrcFromIndex &&
//            dstFromSymbolicArrayIndex === mappedDstFromSymbolicArrayIndex &&
//            dstToSymbolicArrayIndex === mappedDstToSymbolicArrayIndex
//        ) {
//            return this as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//        }
//
//        val mappedAdapter = when (mappedDstFromSymbolicArrayIndex) {
//            is Pair<*, *> ->
//                UAllocatedToInputArrayAdapter(
//                    mappedSrcFromIndex,
//                    mappedDstFromSymbolicArrayIndex as USymbolicArrayIndex,
//                    mappedDstToSymbolicArrayIndex as USymbolicArrayIndex
//                ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey>
//
//            else ->
//                UAllocatedToAllocatedArrayAdapter(
//                    mappedSrcFromIndex,
//                    mappedDstFromSymbolicArrayIndex as USizeExpr,
//                    mappedDstToSymbolicArrayIndex as USizeExpr
//                ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey>
//        }
//
//        return mappedAdapter to collectionId
//    }
//}
//
///**
// * Used when copying data from input array to allocated one.
// */
//class UInputToAllocatedArrayAdapter(
//    private val srcSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstFromIndex: USizeExpr,
//    private val dstToIndex: USizeExpr
//) : USymbolicArrayAdapter<USymbolicArrayIndex, USizeExpr>(
////    srcSymbolicArrayIndex,
////    dstFromSymbolicArrayIndex,
////    dstToIndex
//) {
//    override fun convert(key: USizeExpr): USymbolicArrayIndex =
//        srcSymbolicArrayIndex.first to convertIndex(key, srcSymbolicArrayIndex.second, dstFromIndex)
//
//    override val fromKey: USizeExpr = dstFromIndex
//    override val toKey: USizeExpr = dstToIndex
//    override val srcKey: USymbolicArrayIndex = srcSymbolicArrayIndex
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort : USort> map(
//        dstKeyMapper: KeyMapper<USizeExpr, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USymbolicArrayIndex, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromIndex = dstKeyMapper(dstFromIndex) ?: return null
//        val mappedDstToIndex = dstKeyMapper(dstToIndex) ?: return null
//        val mappedSrcSymbolicArrayIndex = collectionId.keyMapper(composer)(srcSymbolicArrayIndex)
//
//        val decomposedSrcKey = collectionId.rebindKey(mappedSrcSymbolicArrayIndex)
//        if (decomposedSrcKey != null) {
//            // In this case, source collection id has been changed. Heuristically, it can change
//            // only to allocated array id, validating it explicitly...
//            require(collectionId is UAllocatedArrayId<*, Sort>)
//            return (UAllocatedToAllocatedArrayAdapter(
//                decomposedSrcKey.key as USizeExpr,
//                mappedDstFromIndex as USizeExpr,
//                mappedDstToIndex as USizeExpr
//            ) as USymbolicCollectionAdapter<*, MappedDstKey>) to decomposedSrcKey.collectionId
//        }
//
//        if (srcSymbolicArrayIndex == mappedSrcSymbolicArrayIndex && dstFromIndex == mappedDstFromIndex && dstToIndex == mappedDstToIndex) {
//            return this as USymbolicCollectionAdapter<USymbolicArrayIndex, MappedDstKey> to collectionId
//        }
//
//        return UInputToAllocatedArrayAdapter(
//            mappedSrcSymbolicArrayIndex,
//            mappedDstFromIndex as USizeExpr,
//            mappedDstToIndex as USizeExpr
//        ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//    }
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UInputToAllocatedArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//}
//
///**
// * Used when copying data from input array to another input array.
// */
//class UInputToInputArrayAdapter(
//    private val srcSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstFromSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstToSymbolicArrayIndex: USymbolicArrayIndex
//) : USymbolicArrayAdapter<USymbolicArrayIndex, USymbolicArrayIndex>(
////    srcFromSymbolicArrayIndex,
////    dstFromSymbolicArrayIndex,
////    dstToIndex
//) {
//    override fun convert(key: USymbolicArrayIndex): USymbolicArrayIndex =
//        srcSymbolicArrayIndex.first to convertIndex(key.second, srcSymbolicArrayIndex.second, dstFromSymbolicArrayIndex.second)
//
//    override val fromKey: USymbolicArrayIndex = dstFromSymbolicArrayIndex
//    override val toKey: USymbolicArrayIndex = dstToSymbolicArrayIndex
//    override val srcKey: USymbolicArrayIndex = srcSymbolicArrayIndex
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UInputToInputArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort : USort> map(
//        dstKeyMapper: KeyMapper<USymbolicArrayIndex, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USymbolicArrayIndex, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromSymbolicArrayIndex = dstKeyMapper(dstFromSymbolicArrayIndex) ?: return null
//        val mappedDstToSymbolicArrayIndex = dstKeyMapper(dstToSymbolicArrayIndex) ?: return null
//        val mappedSrcSymbolicArrayIndex = collectionId.keyMapper(composer)(srcSymbolicArrayIndex)
//
//        val decomposedSrcKey = collectionId.rebindKey(mappedSrcSymbolicArrayIndex)
//        if (decomposedSrcKey != null) {
//            // In this case, source collection id has been changed. Heuristically, it can change
//            // only to allocated array id, validating it explicitly...
//            require(collectionId is UAllocatedArrayId<*, Sort>)
//            return (UAllocatedToAllocatedArrayAdapter(
//                decomposedSrcKey.key as USizeExpr,
//                mappedDstFromSymbolicArrayIndex as USizeExpr,
//                mappedDstToIndex as USizeExpr
//            ) as USymbolicCollectionAdapter<*, MappedDstKey>) to decomposedSrcKey.collectionId
//        }
//
//        if (srcSymbolicArrayIndex == mappedSrcSymbolicArrayIndex && dstFromIndex == mappedDstFromIndex && dstToIndex == mappedDstToIndex) {
//            return this as USymbolicCollectionAdapter<USymbolicArrayIndex, MappedDstKey> to collectionId
//        }
//
//        return UInputToAllocatedArrayAdapter(
//            mappedSrcSymbolicArrayIndex,
//            mappedDstFromIndex as USizeExpr,
//            mappedDstToIndex as USizeExpr
//        ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//    }
//}

