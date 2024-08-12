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
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeSubExpr
import org.usvm.uctx
import org.usvm.regions.Region
import org.usvm.withSizeSort

/**
 * Composable converter of symbolic collection keys. Helps to transparently copy content of various collections
 * each into other without eager address conversion.
 * For instance, when we copy array slice [i : i + len] to destination memory slice [j : j + len],
 * we emulate it by memorizing the source memory updates as-is, but read the destination memory by
 * 'redirecting' the index k to k + j - i of the source memory.
 * This conversion is done by [convertKey].
 * Do not be confused: it converts [DstKey] to [SrcKey] (not vice-versa), as we use it when we
 * read from destination buffer index to source memory.
 */
abstract class USymbolicArrayCopyAdapter<SrcKey, DstKey, USizeSort : USort, SrcSort: USort, DstSort: USort>(
    val srcFrom: SrcKey,
    val dstFrom: DstKey,
    val dstTo: DstKey,
    private val keyInfo: USymbolicCollectionKeyInfo<DstKey, *>,
    protected val valueConverter: ((UExpr<SrcSort>) -> UExpr<DstSort>)?
) : USymbolicCollectionAdapter<SrcKey, DstKey, SrcSort, DstSort> {

    abstract val ctx: UContext<USizeSort>

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        keyInfo.keyRangeRegion(dstFrom, dstTo) as DstReg

    /**
     * Converts source memory key into destination memory key
     */
    abstract override fun convertKey(key: DstKey, composer: UComposer<*, *>?): SrcKey

    override fun convertValue(value: UExpr<SrcSort>): UExpr<DstSort> {
        val converter = valueConverter
        if (converter != null)
            return converter.invoke(value)
        return value.uncheckedCast()
    }

    protected fun convertIndex(
        idx: UExpr<USizeSort>,
        dstFromIdx: UExpr<USizeSort>,
        srcFromIdx: UExpr<USizeSort>
    ): UExpr<USizeSort> = with(ctx) {
        mkSizeAddExpr(mkSizeSubExpr(idx, dstFromIdx), srcFromIdx)
    }

    override fun includesConcretely(key: DstKey): Boolean =
        keyInfo.cmpConcreteLe(dstFrom, key) && keyInfo.cmpConcreteLe(key, dstTo)

    override fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr {
        val leftIsLefter = keyInfo.cmpSymbolicLe(ctx, keyInfo.mapKey(dstFrom, composer), key)
        val rightIsRighter = keyInfo.cmpSymbolicLe(ctx, key, keyInfo.mapKey(dstTo, composer))

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
        srcKey: SrcKey?,
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
        append(convertKey(dstFrom, composer = null))
        append("..")
        append(convertKey(dstTo, composer = null))
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

class USymbolicArrayAllocatedToAllocatedCopyAdapter<USizeSort : USort, SrcSort: USort, DstSort: USort>(
    srcFrom: UExpr<USizeSort>, dstFrom: UExpr<USizeSort>, dstTo: UExpr<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<USizeSort>, *>,
    valueConverter: ((UExpr<SrcSort>) -> UExpr<DstSort>)? = null
) : USymbolicArrayCopyAdapter<UExpr<USizeSort>, UExpr<USizeSort>, USizeSort, SrcSort, DstSort>(
    srcFrom, dstFrom, dstTo, keyInfo, valueConverter
) {
    override val ctx: UContext<USizeSort>
        get() = srcFrom.uctx.withSizeSort()

    override fun convertKey(key: UExpr<USizeSort>, composer: UComposer<*, *>?): UExpr<USizeSort> =
        convertIndex(key, composer.compose(dstFrom), composer.compose(srcFrom))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>?,
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

class USymbolicArrayAllocatedToInputCopyAdapter<USizeSort : USort, SrcSort: USort, DstSort: USort>(
    srcFrom: UExpr<USizeSort>,
    dstFrom: USymbolicArrayIndex<USizeSort>, dstTo: USymbolicArrayIndex<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex<USizeSort>, *>,
    valueConverter: ((UExpr<SrcSort>) -> UExpr<DstSort>)? = null
) : USymbolicArrayCopyAdapter<UExpr<USizeSort>, USymbolicArrayIndex<USizeSort>, USizeSort, SrcSort, DstSort>(
    srcFrom, dstFrom, dstTo, keyInfo, valueConverter
) {
    override val ctx: UContext<USizeSort>
        get() = srcFrom.uctx.withSizeSort()

    override fun convertKey(key: USymbolicArrayIndex<USizeSort>, composer: UComposer<*, *>?): UExpr<USizeSort> =
        convertIndex(key.second, composer.compose(dstFrom.second), composer.compose(srcFrom))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>?,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $srcCollectionId" }
        @Suppress("UNCHECKED_CAST")
        srcCollectionId as USymbolicArrayId<Type, *, SrcSort, *>
        @Suppress("UNCHECKED_CAST")
        dstCollectionId as USymbolicArrayId<Type, *, DstSort, *>

        val converter = valueConverter
        if (converter == null) {
            memory.memcpy(
                srcRef = mkConcreteHeapRef(srcCollectionId.address),
                dstRef = composer.compose(dstFrom.first),
                type = dstCollectionId.arrayType,
                elementSort = dstCollectionId.sort,
                fromSrcIdx = composer.compose(srcFrom),
                fromDstIdx = composer.compose(dstFrom.second),
                toDstIdx = composer.compose(dstTo.second),
                guard = guard // TODO: should we compose it (and other below)?
            )
        } else {
            memory.convert(
                srcType = srcCollectionId.arrayType,
                dstType = dstCollectionId.arrayType,
                srcRef = mkConcreteHeapRef(srcCollectionId.address),
                dstRef = composer.compose(dstFrom.first),
                srcSort = srcCollectionId.sort,
                dstSort = dstCollectionId.sort,
                fromSrcIdx = composer.compose(srcFrom),
                fromDstIdx = composer.compose(dstFrom.second),
                toDstIdx = composer.compose(dstTo.second),
                guard = guard,
                converter = converter
                )
        }
    }
}

class USymbolicArrayInputToAllocatedCopyAdapter<USizeSort : USort, SrcSort: USort, DstSort: USort>(
    srcFrom: USymbolicArrayIndex<USizeSort>, dstFrom: UExpr<USizeSort>, dstTo: UExpr<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<UExpr<USizeSort>, *>,
    valueConverter: ((UExpr<SrcSort>) -> UExpr<DstSort>)? = null
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex<USizeSort>, UExpr<USizeSort>, USizeSort, SrcSort, DstSort>(
    srcFrom, dstFrom, dstTo, keyInfo, valueConverter
) {
    override val ctx: UContext<USizeSort>
        get() = dstFrom.uctx.withSizeSort()

    override fun convertKey(key: UExpr<USizeSort>, composer: UComposer<*, *>?): USymbolicArrayIndex<USizeSort> =
        composer.compose(srcFrom.first) to
                convertIndex(key, composer.compose(dstFrom), composer.compose(srcFrom.second))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicArrayIndex<USizeSort>?,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        @Suppress("UNCHECKED_CAST")
        srcCollectionId as USymbolicArrayId<Type, *, SrcSort, *>
        @Suppress("UNCHECKED_CAST")
        dstCollectionId as USymbolicArrayId<Type, *, DstSort, *>

        val converter = valueConverter
        if (converter == null) {
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
        } else {
            memory.convert(
                srcType = srcCollectionId.arrayType,
                dstType = dstCollectionId.arrayType,
                srcRef = composer.compose(srcFrom.first),
                dstRef = mkConcreteHeapRef(dstCollectionId.address),
                srcSort = srcCollectionId.sort,
                dstSort = dstCollectionId.sort,
                fromSrcIdx = composer.compose(srcFrom.second),
                fromDstIdx = composer.compose(dstFrom),
                toDstIdx = composer.compose(dstTo),
                guard = guard,
                converter = converter
            )
        }
    }
}

class USymbolicArrayInputToInputCopyAdapter<USizeSort : USort, SrcSort: USort, DstSort: USort>(
    srcFrom: USymbolicArrayIndex<USizeSort>,
    dstFrom: USymbolicArrayIndex<USizeSort>, dstTo: USymbolicArrayIndex<USizeSort>,
    keyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex<USizeSort>, *>,
    valueConverter: ((UExpr<SrcSort>) -> UExpr<DstSort>)? = null
) : USymbolicArrayCopyAdapter<USymbolicArrayIndex<USizeSort>, USymbolicArrayIndex<USizeSort>, USizeSort, SrcSort, DstSort>(
    srcFrom, dstFrom, dstTo, keyInfo, valueConverter
) {
    override val ctx: UContext<USizeSort>
        get() = srcFrom.second.uctx.withSizeSort()

    override fun convertKey(key: USymbolicArrayIndex<USizeSort>, composer: UComposer<*, *>?): USymbolicArrayIndex<USizeSort> =
        composer.compose(srcFrom.first) to
                convertIndex(key.second, composer.compose(dstFrom.second), composer.compose(srcFrom.second))

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicArrayIndex<USizeSort>?,
        composer: UComposer<*, *>
    ) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }

        @Suppress("UNCHECKED_CAST")
        srcCollectionId as USymbolicArrayId<Type, *, SrcSort, *>
        @Suppress("UNCHECKED_CAST")
        dstCollectionId as USymbolicArrayId<Type, *, DstSort, *>

        val converter = valueConverter
        if (converter == null) {
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
        } else {
            memory.convert(
                srcType = srcCollectionId.arrayType,
                dstType = dstCollectionId.arrayType,
                srcRef = composer.compose(srcFrom.first),
                dstRef = composer.compose(dstFrom.first),
                srcSort = srcCollectionId.sort,
                dstSort = dstCollectionId.sort,
                fromSrcIdx = composer.compose(srcFrom.second),
                fromDstIdx = composer.compose(dstFrom.second),
                toDstIdx = composer.compose(dstTo.second),
                guard = guard,
                converter = converter
            )
        }
    }
}
