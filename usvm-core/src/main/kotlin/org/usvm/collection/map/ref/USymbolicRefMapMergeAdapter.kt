package org.usvm.collection.map.ref

import io.ksmt.utils.uncheckedCast
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.set.USymbolicSetKeyInfo
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsId
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsId
import org.usvm.collection.set.ref.USymbolicRefSetId
import org.usvm.compose
import org.usvm.isTrue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx
import org.usvm.regions.Region

sealed class USymbolicRefMapMergeAdapter<
    MapType, SrcKey, DstKey,
    out SetId : USymbolicRefSetId<MapType, SrcKey, *, SetId>,
    Sort: USort>(
    val setOfKeys: USymbolicCollection<SetId, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey, Sort, Sort> {

    abstract override fun convertKey(key: DstKey, composer: UComposer<*, *>?): SrcKey

    override fun convertValue(value: UExpr<Sort>): UExpr<Sort> = value

    override fun includesConcretely(key: DstKey) =
        includesSymbolically(key, composer = null).isTrue

    override fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr {
        val srcKey = convertKey(key, composer)
        return setOfKeys.read(srcKey, composer)
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ) = false

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String =
        "(merge $collection)"
}

class UAllocatedToAllocatedSymbolicRefMapMergeAdapter<MapType, Sort: USort>(
    setOfKeys: USymbolicCollection<UAllocatedRefSetWithInputElementsId<MapType>, UHeapRef, UBoolSort>,
) : USymbolicRefMapMergeAdapter<MapType, UHeapRef, UHeapRef, UAllocatedRefSetWithInputElementsId<MapType>, Sort>(setOfKeys) {
    override fun convertKey(key: UHeapRef, composer: UComposer<*, *>?): UHeapRef = key

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        setOfKeys.collectionId.region(
            setOfKeys,
            setOfKeys.collectionId.keyInfo()
        ) as DstReg

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        dstCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        guard: UBoolExpr,
        srcKey: UHeapRef?,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is UAllocatedRefMapWithInputKeysId<*, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedRefMapWithInputKeysId<*, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            memory.refMapMerge(
                mkConcreteHeapRef(srcCollectionId.mapAddress),
                mkConcreteHeapRef(dstCollectionId.mapAddress),
                srcCollectionId.mapType,
                srcCollectionId.sort,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}

class UAllocatedToInputSymbolicRefMapMergeAdapter<MapType, Sort: USort>(
    val dstMapRef: UHeapRef,
    setOfKeys: USymbolicCollection<UAllocatedRefSetWithInputElementsId<MapType>, UHeapRef, UBoolSort>,
) : USymbolicRefMapMergeAdapter<MapType, UHeapRef, USymbolicMapKey<UAddressSort>,
    UAllocatedRefSetWithInputElementsId<MapType>, Sort>(setOfKeys) {

    override fun convertKey(key: USymbolicMapKey<UAddressSort>, composer: UComposer<*, *>?): UHeapRef = key.second

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg {
        val elementRegion = setOfKeys.collectionId.region(
            setOfKeys,
            UHeapRefKeyInfo
        )
        val dstRefKeyInfo = UHeapRefKeyInfo.keyToRegion(dstMapRef)
        return USymbolicSetKeyInfo.addSetRefRegion(dstRefKeyInfo, elementRegion) as DstReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicMapKey<UAddressSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UHeapRef?,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is UAllocatedRefMapWithInputKeysId<*, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicRefMapId<*, *, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            memory.refMapMerge(
                mkConcreteHeapRef(srcCollectionId.mapAddress),
                composer.compose(dstMapRef),
                srcCollectionId.mapType,
                srcCollectionId.sort,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}

class UInputToAllocatedSymbolicRefMapMergeAdapter<MapType, Sort: USort>(
    val srcMapRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputRefSetWithInputElementsId<MapType>, USymbolicMapKey<UAddressSort>, UBoolSort>,
) : USymbolicRefMapMergeAdapter<MapType, USymbolicMapKey<UAddressSort>, UHeapRef,
    UInputRefSetWithInputElementsId<MapType>, Sort>(setOfKeys) {

    override fun convertKey(key: UHeapRef, composer: UComposer<*, *>?): USymbolicMapKey<UAddressSort> =
        composer.compose(srcMapRef) to key

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg {
        val srcKeySet = setOfKeys.collectionId.region(
            setOfKeys,
            USymbolicSetKeyInfo(UHeapRefKeyInfo)
        )
        return USymbolicSetKeyInfo.removeSetRefRegion(srcKeySet, UHeapRefKeyInfo) as DstReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicMapKey<UAddressSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicMapKey<UAddressSort>?,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is USymbolicRefMapId<*, *, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedRefMapWithInputKeysId<*, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        with(guard.uctx) {
            memory.refMapMerge(
                composer.compose(srcMapRef),
                mkConcreteHeapRef(dstCollectionId.mapAddress),
                dstCollectionId.mapType,
                dstCollectionId.sort,
                setOfKeys.collectionId.setRegionId().uncheckedCast(),
                guard
            )
        }
    }
}

class UInputToInputSymbolicRefMapMergeAdapter<MapType, Sort: USort>(
    val srcMapRef: UHeapRef,
    val dstMapRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputRefSetWithInputElementsId<MapType>, USymbolicMapKey<UAddressSort>, UBoolSort>,
) : USymbolicRefMapMergeAdapter<MapType, USymbolicMapKey<UAddressSort>, USymbolicMapKey<UAddressSort>,
    UInputRefSetWithInputElementsId<MapType>, Sort>(setOfKeys) {

    override fun convertKey(key: USymbolicMapKey<UAddressSort>, composer: UComposer<*, *>?): USymbolicMapKey<UAddressSort> =
        composer.compose(srcMapRef) to key.second

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg {
        val srcKeySet = setOfKeys.collectionId.region(
            setOfKeys,
            USymbolicSetKeyInfo(UHeapRefKeyInfo)
        )
        val dstRefKeyInfo = UHeapRefKeyInfo.keyToRegion(dstMapRef)
        return USymbolicSetKeyInfo.changeSetRefRegion(srcKeySet, dstRefKeyInfo, UHeapRefKeyInfo) as DstReg
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicMapKey<UAddressSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicMapKey<UAddressSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicMapKey<UAddressSort>?,
        composer: UComposer<*, *>,
    ) {
        check(srcCollectionId is USymbolicRefMapId<*, *, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicRefMapId<*, *, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        setOfKeys.applyTo(memory, srcKey, composer)

        memory.refMapMerge(
            composer.compose(srcMapRef),
            composer.compose(dstMapRef),
            dstCollectionId.mapType,
            dstCollectionId.sort,
            setOfKeys.collectionId.setRegionId().uncheckedCast(),
            guard
        )
    }
}
