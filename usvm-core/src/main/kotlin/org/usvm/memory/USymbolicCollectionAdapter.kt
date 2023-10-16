package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.regions.Region

/**
 * Redirects reads from one collection into another. Used in [URangedUpdateNode].
 */
interface USymbolicCollectionAdapter<SrcKey, DstKey> {
    /**
     * Converts destination memory key into source memory key
     */
    fun convert(key: DstKey, composer: UComposer<*, *>?): SrcKey

    /**
     * @return region in the destination collection covered by the adapted collection.
     */
    fun <DstReg : Region<DstReg>> region(): DstReg

    fun includesConcretely(key: DstKey): Boolean

    fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr

    fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ): Boolean

    fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr,
        srcKey: SrcKey,
        composer: UComposer<*, *>
    )

    fun toString(collection: USymbolicCollection<*, SrcKey, *>): String
}
