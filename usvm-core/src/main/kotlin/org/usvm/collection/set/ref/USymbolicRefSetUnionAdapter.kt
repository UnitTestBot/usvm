package org.usvm.collection.set.ref

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementsCollector
import org.usvm.collection.set.USymbolicSetKeyInfo
import org.usvm.collection.set.USymbolicSetUnionElements
import org.usvm.compose
import org.usvm.isTrue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionAdapter
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.regions.Region
import org.usvm.uctx

sealed class USymbolicRefSetUnionAdapter<SetType, SrcKey, DstKey,
        out SetId : USymbolicRefSetId<SetType, SrcKey, *, SetId>, Sort: USort>(
    val setOfKeys: USymbolicCollection<SetId, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey, Sort, Sort>,
    USymbolicSetUnionElements<DstKey> {

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
        "(union $collection)"

    abstract override fun collectSetElements(elements: USymbolicSetElementsCollector.Elements<DstKey>)
}

class UAllocatedToAllocatedSymbolicRefSetUnionAdapter<SetType, Sort: USort>(
    setOfKeys: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>
) : USymbolicRefSetUnionAdapter<SetType, UHeapRef, UHeapRef,
    UAllocatedRefSetWithInputElementsId<SetType>, Sort>(setOfKeys) {

    override fun convertKey(key: UHeapRef, composer: UComposer<*, *>?): UHeapRef = key

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg =
        setOfKeys.collectionId.region(setOfKeys, setOfKeys.collectionId.keyInfo()) as DstReg

    override fun collectSetElements(elements: USymbolicSetElementsCollector.Elements<UHeapRef>) {
        val setElements = USymbolicSetElementsCollector.collect(setOfKeys.updates)
        if (setElements.isInput) {
            elements.isInput = true
        }
        elements.elements += setElements.elements
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        dstCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        guard: UBoolExpr,
        srcKey: UHeapRef?,
        composer: UComposer<*, *>
    ) {
        check(srcCollectionId is UAllocatedRefSetWithInputElementsId<*>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedRefSetWithInputElementsId<*>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            memory.refSetUnion(
                mkConcreteHeapRef(srcCollectionId.setAddress),
                mkConcreteHeapRef(dstCollectionId.setAddress),
                srcCollectionId.setType,
                guard
            )
        }
    }
}

class UAllocatedToInputSymbolicRefSetUnionAdapter<SetType, Sort: USort>(
    val dstSetRef: UHeapRef,
    setOfKeys: USymbolicCollection<UAllocatedRefSetWithInputElementsId<SetType>, UHeapRef, UBoolSort>
) : USymbolicRefSetUnionAdapter<SetType, UHeapRef, USymbolicSetElement<UAddressSort>,
    UAllocatedRefSetWithInputElementsId<SetType>, Sort>(setOfKeys) {

    override fun convertKey(key: USymbolicSetElement<UAddressSort>, composer: UComposer<*, *>?): UHeapRef = key.second

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg {
        val elementRegion = setOfKeys.collectionId.region(
            setOfKeys,
            UHeapRefKeyInfo
        )
        val refRegion = UHeapRefKeyInfo.keyToRegion(dstSetRef)
        return USymbolicSetKeyInfo.addSetRefRegion(elementRegion, refRegion) as DstReg
    }

    override fun collectSetElements(elements: USymbolicSetElementsCollector.Elements<USymbolicSetElement<UAddressSort>>) {
        val setElements = USymbolicSetElementsCollector.collect(setOfKeys.updates)
        if (setElements.isInput) {
            elements.isInput = true
        }
        setElements.elements.mapTo(elements.elements) { dstSetRef to it }
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicSetElement<UAddressSort>, *, *>,
        guard: UBoolExpr,
        srcKey: UHeapRef?,
        composer: UComposer<*, *>
    ) {
        check(srcCollectionId is UAllocatedRefSetWithInputElementsId<*>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicRefSetId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            memory.refSetUnion(
                mkConcreteHeapRef(srcCollectionId.setAddress),
                composer.compose(dstSetRef),
                srcCollectionId.setType,
                guard
            )
        }
    }
}

class UInputToAllocatedSymbolicRefSetUnionAdapter<SetType, Sort: USort>(
    val srcSetRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>
) : USymbolicRefSetUnionAdapter<SetType, USymbolicSetElement<UAddressSort>, UHeapRef,
    UInputRefSetWithInputElementsId<SetType>, Sort>(setOfKeys) {

    override fun convertKey(key: UHeapRef, composer: UComposer<*, *>?): USymbolicSetElement<UAddressSort> =
        composer.compose(srcSetRef) to key

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg {
        val srcKeyInfo = USymbolicSetKeyInfo(UHeapRefKeyInfo)
        val srcKeysRegion = setOfKeys.collectionId.region(
            setOfKeys,
            srcKeyInfo
        )
        return USymbolicSetKeyInfo.removeSetRefRegion(srcKeysRegion, UHeapRefKeyInfo) as DstReg
    }

    override fun collectSetElements(elements: USymbolicSetElementsCollector.Elements<UHeapRef>) {
        val setElements = USymbolicSetElementsCollector.collect(setOfKeys.updates)
        if (setElements.isInput) {
            elements.isInput = true
        }
        setElements.elements.mapTo(elements.elements) { it.second }
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicSetElement<UAddressSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<UHeapRef, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicSetElement<UAddressSort>?,
        composer: UComposer<*, *>
    ) {
        check(srcCollectionId is USymbolicRefSetId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is UAllocatedRefSetWithInputElementsId<*>) { "Unexpected collection: $dstCollectionId" }

        with(guard.uctx) {
            memory.refSetUnion(
                composer.compose(srcSetRef),
                mkConcreteHeapRef(dstCollectionId.setAddress),
                dstCollectionId.setType,
                guard
            )
        }
    }
}

class UInputToInputSymbolicRefSetUnionAdapter<SetType, Sort: USort>(
    val srcSetRef: UHeapRef,
    val dstSetRef: UHeapRef,
    setOfKeys: USymbolicCollection<UInputRefSetWithInputElementsId<SetType>, USymbolicSetElement<UAddressSort>, UBoolSort>
) : USymbolicRefSetUnionAdapter<SetType, USymbolicSetElement<UAddressSort>, USymbolicSetElement<UAddressSort>,
    UInputRefSetWithInputElementsId<SetType>, Sort>(setOfKeys) {

    override fun convertKey(
        key: USymbolicSetElement<UAddressSort>,
        composer: UComposer<*, *>?
    ): USymbolicSetElement<UAddressSort> =
        composer.compose(srcSetRef) to key.second

    @Suppress("UNCHECKED_CAST")
    override fun <DstReg : Region<DstReg>> region(): DstReg {
        val srcKeyInfo = USymbolicSetKeyInfo(UHeapRefKeyInfo)
        val srcKeysReg = setOfKeys.collectionId.region(
            setOfKeys,
            srcKeyInfo
        )
        val dstRefReg = UHeapRefKeyInfo.keyToRegion(dstSetRef)
        return USymbolicSetKeyInfo.changeSetRefRegion(srcKeysReg, dstRefReg, UHeapRefKeyInfo) as DstReg
    }

    override fun collectSetElements(elements: USymbolicSetElementsCollector.Elements<USymbolicSetElement<UAddressSort>>) {
        val setElements = USymbolicSetElementsCollector.collect(setOfKeys.updates)
        if (setElements.isInput) {
            elements.isInput = true
        }
        setElements.elements.mapTo(elements.elements) { dstSetRef to it.second }
    }

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<USymbolicSetElement<UAddressSort>, *, *>,
        dstCollectionId: USymbolicCollectionId<USymbolicSetElement<UAddressSort>, *, *>,
        guard: UBoolExpr,
        srcKey: USymbolicSetElement<UAddressSort>?,
        composer: UComposer<*, *>
    ) {
        check(srcCollectionId is USymbolicRefSetId<*, *, *, *>) { "Unexpected collection: $srcCollectionId" }
        check(dstCollectionId is USymbolicRefSetId<*, *, *, *>) { "Unexpected collection: $dstCollectionId" }

        memory.refSetUnion(
            composer.compose(srcSetRef),
            composer.compose(dstSetRef),
            dstCollectionId.setType,
            guard
        )
    }
}
