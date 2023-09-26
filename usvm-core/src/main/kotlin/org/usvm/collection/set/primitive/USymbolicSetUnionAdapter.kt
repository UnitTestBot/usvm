package org.usvm.collection.set.primitive

import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetKeyInfo
import org.usvm.compose
import org.usvm.isTrue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx
import org.usvm.regions.Region

sealed class USymbolicSetUnionAdapter<
    SetType, SrcKey, DstKey,
    out SetId : USymbolicSetId<SetType, *, SrcKey, *, *, SetId>,
    >(
    val setOfKeys: USymbolicCollection<SetId, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    abstract override fun convert(key: DstKey, composer: UComposer<*, *>?): SrcKey

    override fun includesConcretely(key: DstKey) =
        includesSymbolically(key, composer = null).isTrue

    override fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr {
        val srcKey = convert(key, composer)
        return setOfKeys.read(srcKey, composer)
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ) = false

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String =
        "(union $collection)"
}

class UAllocatedToAllocatedSymbolicSetUnionAdapter<SetType, ElemSort : USort>(
    setOfKeys: USymbolicCollection<UAllocatedSetId<SetType, ElemSort, *>, UExpr<ElemSort>, UBoolSort>,
) : USymbolicSetUnionAdapter<SetType, UExpr<ElemSort>, UExpr<ElemSort>,
    UAllocatedSetId<SetType, ElemSort, *>>(setOfKeys) {
    override fun convert(key: UExpr<ElemSort>, composer: UComposer<*, *>?): UExpr<ElemSort> = key

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        setOfKeys.collectionId.region(setOfKeys, setOfKeys.collectionId.keyInfo()) as DstReg

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<ElemSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<ElemSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<ElemSort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is UAllocatedSetId<*, ElemSort, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedSetId<*, ElemSort, *>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            memory.setUnion(
                mkConcreteHeapRef(srcCollectionId.setAddress),
                mkConcreteHeapRef(dstCollectionId.setAddress),
                srcCollectionId.setType,
                srcCollectionId.elementSort,
                srcCollectionId.keyInfo(),
                guard
            )
        }
    }
}

class UAllocatedToInputSymbolicSetUnionAdapter<SetType, ElemSort : USort>(
    val dstSetRef: UHeapRef,
    setOfKeys: USymbolicCollection<UAllocatedSetId<SetType, ElemSort, *>, UExpr<ElemSort>, UBoolSort>,
) : USymbolicSetUnionAdapter<SetType, UExpr<ElemSort>, USymbolicSetElement<ElemSort>,
    UAllocatedSetId<SetType, ElemSort, *>>(setOfKeys) {

    override fun convert(key: USymbolicSetElement<ElemSort>, composer: UComposer<*, *>?): UExpr<ElemSort> = key.second

    override fun <DstReg : Region<DstReg>> region(): DstReg = convertRegion(setOfKeys.collectionId.keyInfo())

    @Suppress("UNCHECKED_CAST")
    private fun <ElReg : Region<ElReg>, ResReg : Region<ResReg>> convertRegion(
        elementInfo: USymbolicCollectionKeyInfo<UExpr<ElemSort>, ElReg>,
    ): ResReg {
        val elementRegion = setOfKeys.collectionId.region(
            setOfKeys,
            elementInfo
        )
        val refRegion = UHeapRefKeyInfo.keyToRegion(dstSetRef)
        return USymbolicSetKeyInfo.addSetRefRegion(elementRegion, refRegion) as ResReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<ElemSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicSetElement<ElemSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<ElemSort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is UAllocatedSetId<*, ElemSort, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicSetId<*, *, *, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            memory.setUnion(
                mkConcreteHeapRef(srcCollectionId.setAddress),
                composer.compose(dstSetRef),
                srcCollectionId.setType,
                srcCollectionId.elementSort,
                srcCollectionId.keyInfo(),
                guard
            )
        }
    }
}

class UInputToAllocatedSymbolicSetUnionAdapter<SetType, ElemSort : USort>(
    val srcSetRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputSetId<SetType, ElemSort, *>, USymbolicSetElement<ElemSort>, UBoolSort>,
) : USymbolicSetUnionAdapter<SetType, USymbolicSetElement<ElemSort>, UExpr<ElemSort>,
    UInputSetId<SetType, ElemSort, *>>(setOfKeys) {

    override fun convert(key: UExpr<ElemSort>, composer: UComposer<*, *>?): USymbolicSetElement<ElemSort> =
        composer.compose(srcSetRef) to key

    override fun <DstReg : Region<DstReg>> region(): DstReg =
        convertRegion(setOfKeys.collectionId.elementInfo)

    @Suppress("UNCHECKED_CAST")
    private fun <ElReg : Region<ElReg>, ResReg : Region<ResReg>> convertRegion(
        elementInfo: USymbolicCollectionKeyInfo<UExpr<ElemSort>, ElReg>,
    ): ResReg {
        val srcKeyInfo = USymbolicSetKeyInfo(elementInfo)
        val srcKeysRegion = setOfKeys.collectionId.region(
            setOfKeys,
            srcKeyInfo
        )
        return USymbolicSetKeyInfo.removeSetRefRegion(srcKeysRegion, elementInfo) as ResReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicSetElement<ElemSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<ElemSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicSetElement<ElemSort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is USymbolicSetId<*, *, *, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedSetId<*, ElemSort, *>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            memory.setUnion(
                composer.compose(srcSetRef),
                mkConcreteHeapRef(dstCollectionId.setAddress),
                dstCollectionId.setType,
                dstCollectionId.elementSort,
                dstCollectionId.keyInfo(),
                guard
            )
        }
    }
}

class UInputToInputSymbolicSetUnionAdapter<SetType, ElemSort : USort>(
    val srcSetRef: UHeapRef,
    val dstSetRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputSetId<SetType, ElemSort, *>, USymbolicSetElement<ElemSort>, UBoolSort>,
) : USymbolicSetUnionAdapter<SetType, USymbolicSetElement<ElemSort>, USymbolicSetElement<ElemSort>,
    UInputSetId<SetType, ElemSort, *>>(setOfKeys) {

    override fun convert(key: USymbolicSetElement<ElemSort>, composer: UComposer<*, *>?): USymbolicSetElement<ElemSort> =
        composer.compose(srcSetRef) to key.second

    override fun <DstReg : Region<DstReg>> region(): DstReg = convertRegion(setOfKeys.collectionId.elementInfo)

    @Suppress("UNCHECKED_CAST")
    private fun <ElReg : Region<ElReg>, ResReg : Region<ResReg>> convertRegion(
        elementInfo: USymbolicCollectionKeyInfo<UExpr<ElemSort>, ElReg>,
    ): ResReg {
        val srcKeyInfo = USymbolicSetKeyInfo(elementInfo)
        val srcKeysReg = setOfKeys.collectionId.region(
            setOfKeys,
            srcKeyInfo
        )
        val dstRefReg = UHeapRefKeyInfo.keyToRegion(dstSetRef)
        return USymbolicSetKeyInfo.changeSetRefRegion(srcKeysReg, dstRefReg, elementInfo) as ResReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicSetElement<ElemSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicSetElement<ElemSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicSetElement<ElemSort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is USymbolicSetId<*, *, *, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicSetId<*, *, *, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            @Suppress("UNCHECKED_CAST")
            memory.setUnion(
                composer.compose(srcSetRef),
                composer.compose(dstSetRef),
                dstCollectionId.setType,
                dstCollectionId.elementSort,
                dstCollectionId.keyInfo() as USymbolicCollectionKeyInfo<UExpr<USort>, Nothing>,
                guard
            )
        }
    }
}
