package org.usvm.collection.map.primitive

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.set.USymbolicSetKeyInfo
import org.usvm.collection.set.primitive.UAllocatedSetId
import org.usvm.collection.set.primitive.UInputSetId
import org.usvm.collection.set.primitive.USymbolicSetId
import org.usvm.compose
import org.usvm.isTrue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.regions.Region
import org.usvm.uctx

sealed class USymbolicMapMergeAdapter<MapType, SrcKey, DstKey, out SetId : USymbolicSetId<MapType, *, SrcKey, *, *, SetId>>(
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
        "(merge $collection)"
}

class UAllocatedToAllocatedSymbolicMapMergeAdapter<MapType, KeySort : USort>(
    setOfKeys: USymbolicCollection<UAllocatedSetId<MapType, KeySort, *>, UExpr<KeySort>, UBoolSort>,
) : USymbolicMapMergeAdapter<MapType, UExpr<KeySort>, UExpr<KeySort>,
    UAllocatedSetId<MapType, KeySort, *>>(setOfKeys) {
    override fun convert(key: UExpr<KeySort>, composer: UComposer<*, *>?): UExpr<KeySort> = key

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        setOfKeys.collectionId.region(setOfKeys, setOfKeys.collectionId.keyInfo()) as DstReg

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<KeySort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<KeySort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<KeySort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is UAllocatedMapId<*, KeySort, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedMapId<*, KeySort, *, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            memory.mapMerge(
                mkConcreteHeapRef(srcCollectionId.address),
                mkConcreteHeapRef(dstCollectionId.address),
                srcCollectionId.mapType,
                srcCollectionId.keySort,
                srcCollectionId.sort,
                srcCollectionId.keyInfo,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}

class UAllocatedToInputSymbolicMapMergeAdapter<MapType, KeySort : USort>(
    val dstMapRef: UHeapRef,
    setOfKeys: USymbolicCollection<UAllocatedSetId<MapType, KeySort, *>, UExpr<KeySort>, UBoolSort>,
) : USymbolicMapMergeAdapter<MapType, UExpr<KeySort>, USymbolicMapKey<KeySort>,
    UAllocatedSetId<MapType, KeySort, *>>(setOfKeys) {

    override fun convert(key: USymbolicMapKey<KeySort>, composer: UComposer<*, *>?): UExpr<KeySort> = key.second

    override fun <DstReg : Region<DstReg>> region(): DstReg =
        convertRegion(setOfKeys.collectionId.keyInfo())

    @Suppress("UNCHECKED_CAST")
    private fun <ElReg : Region<ElReg>, ResReg : Region<ResReg>> convertRegion(
        elementInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, ElReg>,
    ): ResReg {
        val elementRegion = setOfKeys.collectionId.region(
            setOfKeys,
            elementInfo
        )
        val refRegion = UHeapRefKeyInfo.keyToRegion(dstMapRef)
        return USymbolicSetKeyInfo.addSetRefRegion(elementRegion, refRegion) as ResReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UExpr<KeySort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicMapKey<KeySort>, *, *>,
        guard: UBoolExpr,
        srcKey: UExpr<KeySort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is UAllocatedMapId<*, KeySort, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicMapId<*, *, *, *, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            memory.mapMerge(
                mkConcreteHeapRef(srcCollectionId.address),
                composer.compose(dstMapRef),
                srcCollectionId.mapType,
                srcCollectionId.keySort,
                srcCollectionId.sort,
                srcCollectionId.keyInfo,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}

class UInputToAllocatedSymbolicMapMergeAdapter<MapType, KeySort : USort>(
    val srcMapRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputSetId<MapType, KeySort, *>, USymbolicMapKey<KeySort>, UBoolSort>,
) : USymbolicMapMergeAdapter<MapType, USymbolicMapKey<KeySort>, UExpr<KeySort>,
    UInputSetId<MapType, KeySort, *>>(setOfKeys) {

    override fun convert(key: UExpr<KeySort>, composer: UComposer<*, *>?): USymbolicMapKey<KeySort> =
        composer.compose(srcMapRef) to key

    override fun <DstReg : Region<DstReg>> region(): DstReg =
        convertRegion(setOfKeys.collectionId.elementInfo)

    @Suppress("UNCHECKED_CAST")
    private fun <ElReg : Region<ElReg>, ResReg : Region<ResReg>> convertRegion(
        elementInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, ElReg>,
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
        srcCollectionId: USymbolicCollectionId<USymbolicMapKey<KeySort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UExpr<KeySort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicMapKey<KeySort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is USymbolicMapId<*, *, *, *, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedMapId<*, KeySort, *, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            memory.mapMerge(
                composer.compose(srcMapRef),
                mkConcreteHeapRef(dstCollectionId.address),
                dstCollectionId.mapType,
                dstCollectionId.keySort,
                dstCollectionId.sort,
                dstCollectionId.keyInfo,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}

class UInputToInputSymbolicMapMergeAdapter<MapType, KeySort : USort>(
    val srcMapRef: UHeapRef,
    val dstMapRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputSetId<MapType, KeySort, *>, USymbolicMapKey<KeySort>, UBoolSort>,
) : USymbolicMapMergeAdapter<MapType, USymbolicMapKey<KeySort>, USymbolicMapKey<KeySort>,
    UInputSetId<MapType, KeySort, *>>(setOfKeys) {

    override fun convert(key: USymbolicMapKey<KeySort>, composer: UComposer<*, *>?): USymbolicMapKey<KeySort> =
        composer.compose(srcMapRef) to key.second

    override fun <DstReg : Region<DstReg>> region(): DstReg = convertRegion(setOfKeys.collectionId.elementInfo)

    @Suppress("UNCHECKED_CAST")
    private fun <ElReg : Region<ElReg>, ResReg : Region<ResReg>> convertRegion(
        elementInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, ElReg>,
    ): ResReg {
        val srcKeyInfo = USymbolicSetKeyInfo(elementInfo)
        val srcKeysReg = setOfKeys.collectionId.region(
            setOfKeys,
            srcKeyInfo
        )
        val dstRefReg = UHeapRefKeyInfo.keyToRegion(dstMapRef)
        return USymbolicSetKeyInfo.changeSetRefRegion(srcKeysReg, dstRefReg, elementInfo) as ResReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicMapKey<KeySort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicMapKey<KeySort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicMapKey<KeySort>,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is USymbolicMapId<*, *, *, *, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicMapId<*, *, *, *, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            @Suppress("UNCHECKED_CAST")
            memory.mapMerge(
                composer.compose(srcMapRef),
                composer.compose(dstMapRef),
                dstCollectionId.mapType,
                dstCollectionId.keySort,
                dstCollectionId.sort,
                dstCollectionId.keyInfo as USymbolicCollectionKeyInfo<UExpr<USort>, Nothing>,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}
