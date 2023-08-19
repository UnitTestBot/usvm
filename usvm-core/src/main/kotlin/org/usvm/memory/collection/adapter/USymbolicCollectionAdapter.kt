package org.usvm.memory.collection.adapter

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.USort
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.id.KeyMapper
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.util.Region

/**
 * Redirects reads from one collection into another. Used in [URangedUpdateNode].
 */
interface USymbolicCollectionAdapter<SrcKey, DstKey> {
    /**
     * Converts destination memory key into source memory key
     */
    fun convert(key: DstKey): SrcKey

    /**
     * Key that defines adapted collection id (used to find  id after mapping).
     */
    val srcKey: SrcKey

    /**
     * Returns region covered by the adapted collection.
     */
    fun <Reg : Region<Reg>> region(): Reg

    fun includesConcretely(key: DstKey): Boolean

    fun includesSymbolically(key: DstKey): UBoolExpr

    fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ): Boolean

    /**
     * Maps this adapter by substituting all symbolic values using composer.
     * The type of adapter might change in both [SrcKey] and [DstKey].
     * Type of [SrcKey] changes if [collectionId] rebinds [srcKey].
     * Type of [DstKey] changes to [MappedDstKey] by [dstKeyMapper].
     * @return
     *  - Null if destination keys are filtered out by [dstKeyMapper]
     *  - Pair(adapter, targetId), where adapter is a mapped version of this one, targetId is a
     *    new collection id for the mapped source collection we adapt.
     */
    fun <Type, MappedDstKey, Sort : USort> map(
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        collectionId: USymbolicCollectionId<SrcKey, Sort, *>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
        val mappedSrcKey = collectionId.keyMapper(composer)(srcKey)
        val decomposedSrcKey = collectionId.rebindKey(mappedSrcKey)
        if (decomposedSrcKey != null) {
            val mappedAdapter =
                mapDstKeys(decomposedSrcKey.key, decomposedSrcKey.collectionId, dstKeyMapper, composer, mappedKeyInfo)
                    ?: return null
            return mappedAdapter to decomposedSrcKey.collectionId
        }

        val mappedAdapter = mapDstKeys(mappedSrcKey, collectionId, dstKeyMapper, composer, mappedKeyInfo) ?: return null
        return mappedAdapter to collectionId
    }

    /**
     * Returns new adapter with destination keys were successfully mapped by [dstKeyMapper].
     * If [dstKeyMapper] returns null for at least one key, returns null.
     */
    fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>?

    fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    )

    fun toString(collection: USymbolicCollection<*, SrcKey, *>): String
}
