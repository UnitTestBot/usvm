package org.usvm.collection.map

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.USymbolicCollection
import org.usvm.collection.map.primitive.USymbolicMapId
import org.usvm.collection.set.USymbolicSetId
import org.usvm.isTrue
import org.usvm.memory.KeyMapper
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.util.Region

abstract class USymbolicMapMergeAdapter<SrcKey, DstKey>(
    val dstKey: DstKey,
    override val srcKey: SrcKey,
    val setOfKeys: USymbolicCollection<USymbolicSetId<SrcKey, *, *>, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    abstract override fun convert(key: DstKey): SrcKey

    override fun includesConcretely(key: DstKey) =
        includesSymbolically(key).isTrue

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val srcKey = convert(key)
        return setOfKeys.read(srcKey) // ???
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ) = false

    override fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>? {
        check(srcCollectionId is USymbolicMapId<*, *, *, *, *>) {
            "Unexpected collection: $srcCollectionId"
        }

        val mappedDstKey = dstKeyMapper(dstKey) ?: return null
        val mappedSetOfKeys = setOfKeys.mapTo(composer, srcCollectionId.keysSetId)

        if (mappedSrcKey == srcKey && mappedDstKey == dstKey && mappedSetOfKeys == setOfKeys) {
            @Suppress("UNCHECKED_CAST")
            return this as USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>
        }

        return mapKeyType<USort, _, _>(
            key = mappedSrcKey,
            concrete = { concreteSrc ->
                mapKeyType<USort, _, _>(
                    key = mappedDstKey,
                    concrete = { concreteDst ->
                        USymbolicMapAllocatedToAllocatedMergeAdapter(
                            concreteDst, concreteSrc, mappedSetOfKeys.uncheckedCast()
                        )
                    },
                    symbolic = { symbolicDst ->
                        USymbolicMapAllocatedToInputMergeAdapter(
                            symbolicDst, concreteSrc, mappedSetOfKeys.uncheckedCast()
                        )
                    }
                )
            },
            symbolic = { symbolicSrc ->
                mapKeyType<USort, _, _>(
                    key = mappedDstKey,
                    concrete = { concreteDst ->
                        USymbolicMapInputToAllocatedMergeAdapter(
                            concreteDst, symbolicSrc, mappedSetOfKeys.uncheckedCast()
                        )
                    },
                    symbolic = { symbolicDst ->
                        USymbolicMapInputToInputMergeAdapter(
                            symbolicDst, symbolicSrc, mappedSetOfKeys.uncheckedCast()
                        )
                    }
                )
            }
        ).uncheckedCast()
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

    companion object {
        private inline fun <KeySort : USort, Key, T> mapKeyType(
            key: Key,
            concrete: (UExpr<KeySort>) -> T,
            symbolic: (USymbolicMapKey<KeySort>) -> T
        ): T = when (key) {
            is UExpr<*> -> concrete(key.uncheckedCast())
            is Pair<*, *> -> symbolic(key.uncheckedCast())
            else -> error("Unexpected key: $key")
        }
    }
}

class USymbolicMapAllocatedToAllocatedMergeAdapter<KeySort : USort>(
    dstKey: UExpr<KeySort>, srcKey: UExpr<KeySort>,
    setOfKeys: USymbolicCollection<USymbolicSetId<UExpr<KeySort>, *, *>, UExpr<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<UExpr<KeySort>, UExpr<KeySort>>(
    dstKey, srcKey, setOfKeys
) {
    override fun convert(key: UExpr<KeySort>): UExpr<KeySort> = key
}

class USymbolicMapAllocatedToInputMergeAdapter<KeySort : USort>(
    dstKey: USymbolicMapKey<KeySort>,
    srcKey: UExpr<KeySort>,
    setOfKeys: USymbolicCollection<USymbolicSetId<UExpr<KeySort>, *, *>, UExpr<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<UExpr<KeySort>, USymbolicMapKey<KeySort>>(
    dstKey, srcKey, setOfKeys
) {
    override fun convert(key: USymbolicMapKey<KeySort>): UExpr<KeySort> = key.second
}

class USymbolicMapInputToAllocatedMergeAdapter<KeySort : USort>(
    dstKey: UExpr<KeySort>,
    srcKey: USymbolicMapKey<KeySort>,
    setOfKeys: USymbolicCollection<USymbolicSetId<USymbolicMapKey<KeySort>, *, *>, USymbolicMapKey<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<USymbolicMapKey<KeySort>, UExpr<KeySort>>(
    dstKey, srcKey, setOfKeys
) {
    override fun convert(key: UExpr<KeySort>): USymbolicMapKey<KeySort> = srcKey.first to key
}

class USymbolicMapInputToInputMergeAdapter<KeySort : USort>(
    dstKey: USymbolicMapKey<KeySort>,
    srcKey: USymbolicMapKey<KeySort>,
    setOfKeys: USymbolicCollection<USymbolicSetId<USymbolicMapKey<KeySort>, *, *>, USymbolicMapKey<KeySort>, UBoolSort>
) : USymbolicMapMergeAdapter<USymbolicMapKey<KeySort>, USymbolicMapKey<KeySort>>(
    dstKey, srcKey, setOfKeys
) {
    override fun convert(key: USymbolicMapKey<KeySort>): USymbolicMapKey<KeySort> = srcKey.first to key.second
}
