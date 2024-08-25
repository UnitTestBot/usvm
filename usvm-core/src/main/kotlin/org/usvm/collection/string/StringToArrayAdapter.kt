package org.usvm.collection.string

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UCharSort
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.collection.array.USymbolicArrayId
import org.usvm.collection.array.USymbolicArrayIndex
import org.usvm.collection.array.USymbolicArrayIndexKeyInfo
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.regions.Region
import org.usvm.uctx
import org.usvm.withSizeSort


/**
 * Helps to copy the content of UStringExpr into char array.
 */
abstract class UStringToArrayAdapter<DstKey, USizeSort : USort, DstSort : USort>(
    protected val ctx: UContext<USizeSort>,
    private val keyInfo: USymbolicCollectionKeyInfo<DstKey, *>,
    protected val valueConverter: ((UExpr<UCharSort>) -> UExpr<DstSort>)?
) : USymbolicCollectionAdapter<UExpr<USizeSort>, DstKey, UCharSort, DstSort> {

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        // We copy whole contents of a string
        keyInfo.topRegion() as DstReg

    /**
     * Converts source memory key into destination memory key
     */
    abstract override fun convertKey(key: DstKey, composer: UComposer<*, *>?): UExpr<USizeSort>

    override fun convertValue(value: UExpr<UCharSort>): UExpr<DstSort> {
        val converter = valueConverter
        if (converter != null)
            return converter.invoke(value)
        return value.uncheckedCast()
    }

    override fun includesConcretely(key: DstKey): Boolean = true
    override fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr = ctx.trueExpr
    override fun isIncludedByUpdateConcretely(update: UUpdateNode<DstKey, *>, guard: UBoolExpr): Boolean = true

    abstract override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>?,
        composer: UComposer<*, *>
    )

    override fun toString(collection: USymbolicCollection<*, UExpr<USizeSort>, *>): String = buildString {
        check(collection.collectionId is UStringCollectionId<*>) { "Unexpected collection: ${collection.collectionId}" }
        val string = collection.collectionId.string
        append("[content of ")
        append(string)
        append("]")
    }
}

class UStringToAllocatedArrayAdapter<USizeSort : USort, DstSort : USort>(
    dstRef: UConcreteHeapRef,
    valueConverter: ((UExpr<UCharSort>) -> UExpr<DstSort>)? = null
) : UStringToArrayAdapter<UExpr<USizeSort>, USizeSort, DstSort>(
    dstRef.uctx.withSizeSort(),
    USizeExprKeyInfo(),
    valueConverter
) {

    override fun convertKey(key: UExpr<USizeSort>, composer: UComposer<*, *>?): UExpr<USizeSort> = key

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>?,
        composer: UComposer<*, *>
    ) = with(ctx) {
        check(dstCollectionId is UAllocatedArrayId<*, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UStringCollectionId<*>) { "Unexpected collection: $srcCollectionId" }
        @Suppress("UNCHECKED_CAST")
        dstCollectionId as USymbolicArrayId<Type, *, DstSort, *>

        memory.copyStringContentToArray<_, _, USizeSort>(
            composer.compose(srcCollectionId.string),
            mkConcreteHeapRef(dstCollectionId.address),
            dstCollectionId.arrayType,
            dstCollectionId.sort,
            guard,
            valueConverter
        )
    }
}

class UStringToInputArrayAdapter<USizeSort : USort, DstSort : USort>(
    val dstRef: UHeapRef,
    valueConverter: ((UExpr<UCharSort>) -> UExpr<DstSort>)? = null
) : UStringToArrayAdapter<USymbolicArrayIndex<USizeSort>, USizeSort, DstSort>(
    dstRef.uctx.withSizeSort(), USymbolicArrayIndexKeyInfo(), valueConverter
) {
    override fun convertKey(key: USymbolicArrayIndex<USizeSort>, composer: UComposer<*, *>?): UExpr<USizeSort> =
        key.second

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<USizeSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicArrayIndex<USizeSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<USizeSort>?,
        composer: UComposer<*, *>
    ) {
        check(dstCollectionId is USymbolicArrayId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }
        check(srcCollectionId is UStringCollectionId<*>) { "Unexpected collection: $srcCollectionId" }
        @Suppress("UNCHECKED_CAST")
        dstCollectionId as USymbolicArrayId<Type, *, DstSort, *>

        memory.copyStringContentToArray<_, _, USizeSort>(
            composer.compose(srcCollectionId.string),
            composer.compose(dstRef),
            dstCollectionId.arrayType,
            dstCollectionId.sort,
            guard,
            valueConverter
        )
    }
}
