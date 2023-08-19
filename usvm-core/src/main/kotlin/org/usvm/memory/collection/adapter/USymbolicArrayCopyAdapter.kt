package org.usvm.memory.collection.adapter

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.id.KeyMapper
import org.usvm.memory.collection.id.UAllocatedArrayId
import org.usvm.memory.collection.id.USymbolicArrayId
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.memory.collection.key.USymbolicArrayIndex
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.memory.collection.region.memcpy
import org.usvm.uctx
import org.usvm.util.Region

/**
 * Composable converter of symbolic collection keys. Helps to transparently copy content of various collections
 * each into other without eager address conversion.
 * For instance, when we copy array slice [i : i + len] to destination memory slice [j : j + len],
 * we emulate it by memorizing the source memory updates as-is, but read the destination memory by
 * 'redirecting' the index k to k + j - i of the source memory.
 * This conversion is done by [convert].
 * Do not be confused: it converts [DstKey] to [SrcKey] (not vice-versa), as we use it when we
 * read from destination buffer index to source memory.
 */
class USymbolicArrayCopyAdapter<SrcKey, DstKey>(
    private val srcFrom: SrcKey,
    val dstFrom: DstKey,
    val dstTo: DstKey,
    private val keyInfo: USymbolicCollectionKeyInfo<DstKey, *>
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    override val srcKey = srcFrom

    override fun <Reg : Region<Reg>> region(): Reg =
        keyInfo.keyRangeRegion(dstFrom, dstTo).uncheckedCast()

    private fun <Key> extractArrayIndex(value: Key): USizeExpr =
        mapKeyType(value, index = { it }, symbolic = { it.second })

    /**
     * Converts source memory key into destination memory key
     */
    override fun convert(key: DstKey): SrcKey =
        mapKeyType(
            key,
            index = { convertIndex(it) },
            symbolic = { it.first to convertIndex(it.second) }
        ).uncheckedCast()

    private fun convertIndex(idx: USizeExpr): USizeExpr = with(idx.ctx) {
        mkBvSubExpr(mkBvAddExpr(idx, extractArrayIndex(dstFrom)), extractArrayIndex(srcFrom))
    }

    override fun includesConcretely(key: DstKey): Boolean =
        keyInfo.cmpConcrete(dstFrom, key) && keyInfo.cmpConcrete(key, dstTo)

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val leftIsLefter = keyInfo.cmpSymbolic(dstFrom, key)
        val rightIsRighter = keyInfo.cmpSymbolic(key, dstTo)
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter)
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ): Boolean =
        update.includesConcretely(dstFrom, guard) && update.includesConcretely(dstTo, guard)

    override fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>? {
        val mappedDstFrom = dstKeyMapper(dstFrom) ?: return null
        val mappedDstTo = dstKeyMapper(dstTo) ?: return null

        if (srcKey === mappedSrcKey && dstFrom === mappedDstFrom && dstTo === mappedDstTo) {
            @Suppress("UNCHECKED_CAST")
            // In this case [MappedSrcKey] == [SrcKey] and [MappedDstKey] == [DstKey],
            // but type system cannot type check that.
            return this as USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>
        }

        return USymbolicArrayCopyAdapter(mappedSrcKey, mappedDstFrom, mappedDstTo, mappedKeyInfo)
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    ) = with(guard.uctx) {
        require(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        val (srcRef: UHeapRef, srcIdx: USizeExpr) = mapKeyType(
            srcFrom,
            index = {
                check(srcCollectionId is UAllocatedArrayId<*, *>) {
                    "Key $srcFrom is concrete by $srcCollectionId is not allocated"
                }
                mkConcreteHeapRef(srcCollectionId.address) to it
            },
            symbolic = { it }
        )

        val (dstRef: UHeapRef, dstFromIdx: USizeExpr, dstToIdx: USizeExpr) = mapKeyType(
            dstFrom,
            index = {
                check(dstCollectionId is UAllocatedArrayId<*, *>) {
                    "Key $dstFrom is concrete by $dstCollectionId is not allocated"
                }
                Triple(mkConcreteHeapRef(dstCollectionId.address), it, ensureIndexKey(dstTo))
            },
            symbolic = {
                Triple(it.first, it.second, ensureSymbolicKey(dstTo).second)
            }
        )

        memory.memcpy(
            srcRef, dstRef, dstCollectionId.arrayType, dstCollectionId.sort, srcIdx, dstFromIdx, dstToIdx, guard
        )
    }

    private fun <Key> keyToString(key: Key) =
        mapKeyType(
            key,
            index = { "$it" },
            symbolic = { "${it.first}.${it.second}" }
        )

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String {
        return "[${keyToString(dstFrom)}..${keyToString(dstTo)}] <- $collection[${convert(dstFrom)}..${convert(dstTo)}]"
    }

    companion object {
        private inline fun <Key, T> mapKeyType(
            key: Key,
            index: (USizeExpr) -> T,
            symbolic: (USymbolicArrayIndex) -> T
        ): T = when (key) {
            is UExpr<*> -> index(key.uncheckedCast())
            is Pair<*, *> -> symbolic(key.uncheckedCast())
            else -> error("Unexpected key: $key")
        }

        private fun <Key> ensureSymbolicKey(key: Key): USymbolicArrayIndex =
            mapKeyType(key, symbolic = { it }, index = { error("Key type mismatch: $key") })

        private fun <Key> ensureIndexKey(key: Key): USizeExpr =
            mapKeyType(key, index = { it }, symbolic = { error("Key type mismatch: $key") })
    }
}
