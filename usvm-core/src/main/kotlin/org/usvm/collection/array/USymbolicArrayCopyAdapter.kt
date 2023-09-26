package org.usvm.collection.array

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.compose
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import org.usvm.regions.Region
import org.usvm.withSizeSort

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
abstract class USymbolicArrayCopyAdapter<SrcKey, DstKey, USizeSort : USort>(
    val srcFrom: SrcKey,
    val dstFrom: DstKey,
    val dstTo: DstKey,
    private val keyInfo: USymbolicCollectionKeyInfo<DstKey, *>
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    abstract val ctx: UContext<USizeSort>

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        keyInfo.keyRangeRegion(dstFrom, dstTo) as DstReg

    /**
     * Converts source memory key into destination memory key
     */
    abstract override fun convert(key: DstKey, composer: UComposer<*, *>?): SrcKey

    protected fun convertIndex(
        idx: UExpr<USizeSort>,
        dstFromIdx: UExpr<USizeSort>,
        srcFromIdx: UExpr<USizeSort>
    ): UExpr<USizeSort> = with(ctx.withSizeSort<USizeSort>()) {
        // TODO
        mkSizeAddExpr(mkSizeSubExpr(idx, dstFromIdx), srcFromIdx)
    }

    override fun includesConcretely(key: DstKey): Boolean =
        keyInfo.cmpConcreteLe(dstFrom, key) && keyInfo.cmpConcreteLe(key, dstTo)

    override fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr {
        val leftIsLefter = keyInfo.cmpSymbolicLe(ctx, keyInfo.mapKey(dstFrom, composer), key)
        val rightIsRighter = keyInfo.cmpSymbolicLe(ctx, key, keyInfo.mapKey(dstTo, composer))
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter)
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ): Boolean =
        update.includesConcretely(dstFrom, guard) && update.includesConcretely(dstTo, guard)

    abstract override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr,
        srcKey: SrcKey,
        composer: UComposer<*, *>
    )

    private fun <Key> keyToString(key: Key) =
        mapKeyType(
            key,
            concrete = { "$it" },
            symbolic = { "${it.first}.${it.second}" }
        )

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String = buildString {
        append("[")
        append(keyToString(dstFrom))
        append("..")
        append(keyToString(dstTo))
        append("] <- ")
        append(collection)
        append("[")
        append(convert(dstFrom, composer = null))
        append("..")
        append(convert(dstTo, composer = null))
        append("]")
    }

    companion object {
        private inline fun <Key, T> mapKeyType(
            key: Key,
            concrete: (UExpr<*>) -> T,
            symbolic: (USymbolicArrayIndex<*>) -> T
        ): T = when (key) {
            is UExpr<*> -> concrete(key)
            is Pair<*, *> -> symbolic(key.uncheckedCast())
            else -> error("Unexpected key: $key")
        }
    }
}

class USymbolicArrayAllocatedToAllocatedCopyAdapter<USizeSort : USort>(
    srcFrom: UExpr<USizeSort>, dstFrom: UExpr<USizeSort>, dstTo: UExpr<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<USizeSort>, *>
) : USymbolicArrayCopyAdapter<UExpr<USizeSort>, UExpr<USizeSort>, USizeSort>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext<USizeSort>
        get() = srcFrom.uctx.withSizeSort()

    override fun convert(key: UExpr<USizeSort>, composer: UComposer<*, *>?): UExpr<USizeSort> =
        convertIndex(key, composer.compose(dstFrom), composer.compose(srcFrom))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = mkConcreteHeapRef(srcCollectionId.address),
            dstRef = mkConcreteHeapRef(dstCollectionId.address),
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = composer.compose(srcFrom),
            fromDstIdx = composer.compose(dstFrom),
            toDstIdx = composer.compose(dstTo),
            guard = guard
        )
    }
}

class USymbolicArrayAllocatedToInputCopyAdapter<USizeSort : USort>(
    srcFrom: UExpr<USizeSort>,
    dstFrom: USymbolicArrayIndex<USizeSort>, dstTo: USymbolicArrayIndex<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex<USizeSort>, *>
) : USymbolicArrayCopyAdapter<UExpr<USizeSort>, USymbolicArrayIndex<USizeSort>, USizeSort>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext<USizeSort>
        get() = srcFrom.uctx.withSizeSort()

    override fun convert(key: USymbolicArrayIndex<USizeSort>, composer: UComposer<*, *>?): UExpr<USizeSort> =
        convertIndex(key.second, composer.compose(dstFrom.second), composer.compose(srcFrom))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = mkConcreteHeapRef(srcCollectionId.address),
            dstRef = composer.compose(dstFrom.first),
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = composer.compose(srcFrom),
            fromDstIdx = composer.compose(dstFrom.second),
            toDstIdx = composer.compose(dstTo.second),
            guard = guard
        )
    }
}

class USymbolicArrayInputToAllocatedCopyAdapter<USizeSort : USort>(
    srcFrom: USymbolicArrayIndex<USizeSort>, dstFrom: UExpr<USizeSort>, dstTo: UExpr<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<USizeSort>, *>
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex<USizeSort>, UExpr<USizeSort>, USizeSort>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext<USizeSort>
        get() = dstFrom.uctx.withSizeSort()

    override fun convert(key: UExpr<USizeSort>, composer: UComposer<*, *>?): USymbolicArrayIndex<USizeSort> =
        composer.compose(srcFrom.first) to
                convertIndex(key, composer.compose(dstFrom), composer.compose(srcFrom.second))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicArrayIndex<USizeSort>,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = composer.compose(srcFrom.first),
            dstRef = mkConcreteHeapRef(dstCollectionId.address),
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = composer.compose(srcFrom.second),
            fromDstIdx = composer.compose(dstFrom),
            toDstIdx = composer.compose(dstTo),
            guard = guard
        )
    }
}

class USymbolicArrayInputToInputCopyAdapter<USizeSort : USort>(
    srcFrom: USymbolicArrayIndex<USizeSort>,
    dstFrom: USymbolicArrayIndex<USizeSort>, dstTo: USymbolicArrayIndex<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex<USizeSort>, *>
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex<USizeSort>, USymbolicArrayIndex<USizeSort>, USizeSort>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext<USizeSort>
        get() = srcFrom.second.uctx.withSizeSort()

    override fun convert(key: USymbolicArrayIndex<USizeSort>, composer: UComposer<*, *>?): USymbolicArrayIndex<USizeSort> =
        composer.compose(srcFrom.first) to
                convertIndex(key.second, composer.compose(dstFrom.second), composer.compose(srcFrom.second))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicArrayIndex<USizeSort>,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }

        memory.memcpy(
            srcRef = composer.compose(srcFrom.first),
            dstRef = composer.compose(dstFrom.first),
            type = dstCollectionId.arrayType,
            elementSort = dstCollectionId.sort,
            fromSrcIdx = composer.compose(srcFrom.second),
            fromDstIdx = composer.compose(dstFrom.second),
            toDstIdx = composer.compose(dstTo.second),
            guard = guard
        )
    }
}
