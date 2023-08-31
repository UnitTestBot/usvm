package org.usvm.collection.array

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USizeExpr
import org.usvm.compose
import org.usvm.memory.USymbolicCollection
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

    abstract val ctx: UContext

    override fun <Reg : Region<Reg>> region(): Reg =
        keyInfo.keyRangeRegion(dstFrom, dstTo).uncheckedCast()

    /**
     * Converts source memory key into destination memory key
     */
    abstract override fun convert(key: DstKey, composer: UComposer<*>?): SrcKey

    protected fun convertIndex(
        idx: USizeExpr,
        dstFromIdx: USizeExpr,
        srcFromIdx: USizeExpr
    ): USizeExpr = with(ctx) {
        mkBvAddExpr(mkBvSubExpr(idx, dstFromIdx), srcFromIdx)
    }

    override fun includesConcretely(key: DstKey): Boolean =
        keyInfo.cmpConcreteLe(dstFrom, key) && keyInfo.cmpConcreteLe(key, dstTo)

    override fun includesSymbolically(key: DstKey, composer: UComposer<*>?): UBoolExpr {
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
        composer: UComposer<*>
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
            concrete: (USizeExpr) -> T,
            symbolic: (USymbolicArrayIndex) -> T
        ): T = when (key) {
            is UExpr<*> -> concrete(key.uncheckedCast())
            is Pair<*, *> -> symbolic(key.uncheckedCast())
            else -> error("Unexpected key: $key")
        }
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

    override fun convert(key: USizeExpr, composer: UComposer<*>?): USizeExpr =
        convertIndex(key, composer.compose(dstFrom), composer.compose(srcFrom))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        dstCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        guard: UBoolExpr,
        srcKey: USizeExpr,
        composer: UComposer<*>
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $srcCollectionId" }

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

class USymbolicArrayAllocatedToInputCopyAdapter(
    srcFrom: USizeExpr,
    dstFrom: USymbolicArrayIndex, dstTo: USymbolicArrayIndex,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex, *>
) : USymbolicArrayCopyAdapter<USizeExpr, USymbolicArrayIndex>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = srcFrom.uctx

    override fun convert(key: USymbolicArrayIndex, composer: UComposer<*>?): USizeExpr =
        convertIndex(key.second, composer.compose(dstFrom.second), composer.compose(srcFrom))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        guard: UBoolExpr,
        srcKey: USizeExpr,
        composer: UComposer<*>
    ) = with(ctx) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $srcCollectionId" }

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

class USymbolicArrayInputToAllocatedCopyAdapter(
    srcFrom: USymbolicArrayIndex, dstFrom: USizeExpr, dstTo: USizeExpr,
    keyInfo: USymbolicCollectionKeyInfo<USizeExpr, *>
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex, USizeExpr>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = dstFrom.uctx

    override fun convert(key: USizeExpr, composer: UComposer<*>?): USymbolicArrayIndex =
        composer.compose(srcFrom.first) to
                convertIndex(key, composer.compose(dstFrom), composer.compose(srcFrom.second))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        dstCollectionId: USymbolicCollectionId<USizeExpr, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicArrayIndex,
        composer: UComposer<*>
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *>) { "Unexpected collection: $dstCollectionId" }
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

class USymbolicArrayInputToInputCopyAdapter(
    srcFrom: USymbolicArrayIndex,
    dstFrom: USymbolicArrayIndex, dstTo: USymbolicArrayIndex,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex, *>
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex, USymbolicArrayIndex>(
    srcFrom, dstFrom, dstTo, keyInfo
) {
    override val ctx: UContext
        get() = srcFrom.second.uctx

    override fun convert(key: USymbolicArrayIndex, composer: UComposer<*>?): USymbolicArrayIndex =
        composer.compose(srcFrom.first) to
                convertIndex(key.second, composer.compose(dstFrom.second), composer.compose(srcFrom.second))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicArrayIndex,
        composer: UComposer<*>
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
