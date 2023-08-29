package org.usvm.collection.array

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USizeExpr
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.KeyMapper
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
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
abstract class USymbolicArrayCopyAdapter<SrcKey, DstKey>(
    val srcFrom: SrcKey,
    val dstFrom: DstKey,
    val dstTo: DstKey,
    private val keyInfo: USymbolicCollectionKeyInfo<DstKey, *>
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    override val srcKey = srcFrom

    abstract val ctx: UContext

    override fun <Reg : Region<Reg>> region(): Reg =
        keyInfo.keyRangeRegion(dstFrom, dstTo).uncheckedCast()

    /**
     * Converts source memory key into destination memory key
     */
    abstract override fun convert(key: DstKey): SrcKey

    protected fun convertIndex(
        idx: USizeExpr,
        dstFromIdx: USizeExpr,
        srcFromIdx: USizeExpr
    ): USizeExpr = with(ctx) {
        mkBvAddExpr(mkBvSubExpr(idx, dstFromIdx), srcFromIdx)
    }

    override fun includesConcretely(key: DstKey): Boolean =
        keyInfo.cmpConcreteLe(dstFrom, key) && keyInfo.cmpConcreteLe(key, dstTo)

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val leftIsLefter = keyInfo.cmpSymbolicLe(ctx, dstFrom, key)
        val rightIsRighter = keyInfo.cmpSymbolicLe(ctx, key, dstTo)
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

        return mapKeyType(
            mappedSrcKey,
            concrete = { allocatedSrcKey ->
                mapKeyType(
                    mappedDstFrom,
                    concrete = { allocatedDstFrom ->
                        USymbolicArrayAllocatedToAllocatedCopyAdapter(
                            allocatedSrcKey,
                            allocatedDstFrom,
                            ensureConcreteKey(mappedDstTo),
                            mappedKeyInfo.uncheckedCast()
                        )
                    },
                    symbolic = { symbolicDstFrom ->
                        USymbolicArrayAllocatedToInputCopyAdapter(
                            allocatedSrcKey,
                            symbolicDstFrom,
                            ensureSymbolicKey(mappedDstTo),
                            mappedKeyInfo.uncheckedCast()
                        )
                    }
                )
            },
            symbolic = { symbolicSrcKey ->
                mapKeyType(
                    mappedDstFrom,
                    concrete = { allocatedDstFrom ->
                        USymbolicArrayInputToAllocatedCopyAdapter(
                            symbolicSrcKey,
                            allocatedDstFrom,
                            ensureConcreteKey(mappedDstTo),
                            mappedKeyInfo.uncheckedCast()
                        )
                    },
                    symbolic = { symbolicDstFrom ->
                        USymbolicArrayInputToInputCopyAdapter(
                            symbolicSrcKey,
                            symbolicDstFrom,
                            ensureSymbolicKey(mappedDstTo),
                            mappedKeyInfo.uncheckedCast()
                        )
                    }
                )
            }
        ).uncheckedCast()
    }

    abstract override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    )

    private fun <Key> keyToString(key: Key) =
        mapKeyType(
            key,
            concrete = { "$it" },
            symbolic = { "${it.first}.${it.second}" }
        )

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String {
        return "[${keyToString(dstFrom)}..${keyToString(dstTo)}] <- $collection[${convert(dstFrom)}..${convert(dstTo)}]"
    }

    companion object {
        private inline fun <Key, T> mapKeyType(
            key: Key,
            concrete: (USizeExpr) -> T,
            symbolic: (USymbolicArrayIndex) -> T
        ): T = when (key) {
            is UExpr<*> -> concrete(key.uncheckedCast())
            is Pair<*, *> -> symbolic(key.uncheckedCast())
            else -> error("Unexpected key: $key")
        }

        private fun <Key> ensureSymbolicKey(key: Key): USymbolicArrayIndex =
            mapKeyType(key, symbolic = { it }, concrete = { error("Key type mismatch: $key") })

        private fun <Key> ensureConcreteKey(key: Key): USizeExpr =
            mapKeyType(key, concrete = { it }, symbolic = { error("Key type mismatch: $key") })
    }
}

class USymbolicArrayAllocatedToAllocatedCopyAdapter(
    srcFrom: USizeExpr, dstFrom: USizeExpr, dstTo: USizeExpr,
    keyInfo: USymbolicCollectionKeyInfo<USizeExpr, *>
) : USymbolicArrayCopyAdapter<USizeExpr, USizeExpr>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = srcFrom.uctx

    override fun convert(key: USizeExpr): USizeExpr =
        convertIndex(key, dstFrom, srcFrom)

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        dstCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        guard: UBoolExpr
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = mkConcreteHeapRef(srcCollectionId.address),
            dstRef = mkConcreteHeapRef(dstCollectionId.address),
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = srcFrom,
            fromDstIdx = dstFrom,
            toDstIdx = dstTo,
            guard = guard
        )
    }
}

class USymbolicArrayAllocatedToInputCopyAdapter(
    srcFrom: USizeExpr,
    dstFrom: USymbolicArrayIndex, dstTo: USymbolicArrayIndex,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex, *>
) : USymbolicArrayCopyAdapter<USizeExpr, USymbolicArrayIndex>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = srcFrom.uctx

    override fun convert(key: USymbolicArrayIndex): USizeExpr =
        convertIndex(key.second, dstFrom.second, srcFrom)

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        guard: UBoolExpr
    ) = with(ctx) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = mkConcreteHeapRef(srcCollectionId.address),
            dstRef = dstFrom.first,
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = srcFrom,
            fromDstIdx = dstFrom.second,
            toDstIdx = dstTo.second,
            guard = guard
        )
    }
}

class USymbolicArrayInputToAllocatedCopyAdapter(
    srcFrom: USymbolicArrayIndex, dstFrom: USizeExpr, dstTo: USizeExpr,
    keyInfo: USymbolicCollectionKeyInfo<USizeExpr, *>
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex, USizeExpr>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = dstFrom.uctx

    override fun convert(key: USizeExpr): USymbolicArrayIndex =
        srcFrom.first to convertIndex(key, dstFrom, srcFrom.second)

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        dstCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        guard: UBoolExpr
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = srcFrom.first,
            dstRef = mkConcreteHeapRef(dstCollectionId.address),
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = srcFrom.second,
            fromDstIdx = dstFrom,
            toDstIdx = dstTo,
            guard = guard
        )
    }
}

class USymbolicArrayInputToInputCopyAdapter(
    srcFrom: USymbolicArrayIndex,
    dstFrom: USymbolicArrayIndex, dstTo: USymbolicArrayIndex,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex, *>
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex, USymbolicArrayIndex>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = srcFrom.second.uctx

    override fun convert(key: USymbolicArrayIndex): USymbolicArrayIndex =
        srcFrom.first to convertIndex(key.second, dstFrom.second, srcFrom.second)

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        guard: UBoolExpr
    ) = with(ctx) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = srcFrom.first,
            dstRef = dstFrom.first,
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = srcFrom.second,
            fromDstIdx = dstFrom.second,
            toDstIdx = dstTo.second,
            guard = guard
        )
    }
}
