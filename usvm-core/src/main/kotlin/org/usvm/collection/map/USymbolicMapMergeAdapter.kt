package org.usvm.collection.map

import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetId
import org.usvm.compose
import org.usvm.isTrue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.regions.Region


abstract class USymbolicMapMergeAdapter<SrcKey, DstKey>(
    val setOfKeys: USymbolicCollection<USymbolicSetId<SrcKey, *, *>, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    abstract override fun convert(key: DstKey, composer: UComposer<*>?): SrcKey

    override fun includesConcretely(key: DstKey) =
        includesSymbolically(key, composer = null).isTrue // todo: composer=null?

    override fun includesSymbolically(key: DstKey, composer: UComposer<*>?): UBoolExpr {
        val srcKey = convert(key, composer)
        return setOfKeys.read(srcKey, composer)
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ) = false

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String =
        "(merge $collection)"


    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr,
        srcKey: SrcKey,
        composer: UComposer<*>
    ) {
        setOfKeys.applyTo(memory, srcKey,  composer)
        TODO()
    }

    override fun <Reg : Region<Reg>> region(): Reg =
        convertRegion(setOfKeys.collectionId.region(setOfKeys.updates))

    private fun <Reg : Region<Reg>> convertRegion(srcReg: Reg): Reg =
        srcReg // TODO: implement valid region conversion logic
}

class USymbolicMapAllocatedToAllocatedMergeAdapter<KeySort : USort>(
    setOfKeys: USymbolicCollection<USymbolicSetId<UExpr<KeySort>, *, *>, UExpr<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<UExpr<KeySort>, UExpr<KeySort>>(
    setOfKeys
) {
    override fun convert(key: UExpr<KeySort>, composer: UComposer<*>?): UExpr<KeySort> = key
}

class USymbolicMapAllocatedToInputMergeAdapter<KeySort : USort>(
    val dstRef: UHeapRef,
    setOfKeys: USymbolicCollection<USymbolicSetId<UExpr<KeySort>, *, *>, UExpr<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<UExpr<KeySort>, USymbolicMapKey<KeySort>>(
    setOfKeys
) {
    override fun convert(key: USymbolicMapKey<KeySort>, composer: UComposer<*>?): UExpr<KeySort> = key.second
}

class USymbolicMapInputToAllocatedMergeAdapter<KeySort : USort>(
    val srcRef: UHeapRef,
    setOfKeys: USymbolicCollection<USymbolicSetId<USymbolicMapKey<KeySort>, *, *>, USymbolicMapKey<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<USymbolicMapKey<KeySort>, UExpr<KeySort>>(
    setOfKeys
) {
    override fun convert(key: UExpr<KeySort>, composer: UComposer<*>?): USymbolicMapKey<KeySort> =
        composer.compose(srcRef) to key
}

class USymbolicMapInputToInputMergeAdapter<KeySort : USort>(
    val srcRef: UHeapRef,
    val dstRef: UHeapRef,
    setOfKeys: USymbolicCollection<USymbolicSetId<USymbolicMapKey<KeySort>, *, *>, USymbolicMapKey<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<USymbolicMapKey<KeySort>, USymbolicMapKey<KeySort>>(
    setOfKeys
) {
    override fun convert(key: USymbolicMapKey<KeySort>, composer: UComposer<*>?): USymbolicMapKey<KeySort> =
        composer.compose(srcRef) to key.second
}
