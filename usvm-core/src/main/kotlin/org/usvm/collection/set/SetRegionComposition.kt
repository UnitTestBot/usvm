package org.usvm.collection.set

import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.length.UAllocatedWithAllocatedSymbolicSetIntersectionSize
import org.usvm.collection.set.length.UAllocatedWithInputSymbolicSetIntersectionSize
import org.usvm.collection.set.length.UInputWithInputSymbolicSetIntersectionSize
import org.usvm.collection.set.length.USymbolicSetIntersectionSize
import org.usvm.collection.set.primitive.USetReadOnlyRegion
import org.usvm.collection.set.primitive.USymbolicSetId
import org.usvm.collection.set.ref.URefSetReadOnlyRegion
import org.usvm.collection.set.ref.USymbolicRefSetId
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.uctx
import org.usvm.withSizeSort

fun <USizeSort : USort> composeSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    expr: USymbolicSetIntersectionSize<USizeSort>
): UExpr<USizeSort> = when (expr) {
    is UAllocatedWithAllocatedSymbolicSetIntersectionSize<USizeSort, *, *> -> composeSymbolicSetIntersectionSize(
        composer,
        expr
    )

    is UAllocatedWithInputSymbolicSetIntersectionSize<USizeSort, *, *, *> -> composeSymbolicSetIntersectionSize(
        composer,
        expr
    )

    is UInputWithInputSymbolicSetIntersectionSize<USizeSort, *, *> -> composeSymbolicSetIntersectionSize(composer, expr)
}

private fun <USizeSort : USort, ElementSort : USort, AllocatedCollectionId> composeSymbolicSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    expr: UAllocatedWithAllocatedSymbolicSetIntersectionSize<USizeSort, ElementSort, AllocatedCollectionId>
): UExpr<USizeSort> where AllocatedCollectionId : USymbolicCollectionId<UExpr<ElementSort>, UBoolSort, AllocatedCollectionId> {
    val firstElements = USymbolicSetElementsCollector.collect(expr.firstCollection.updates)
    val secondElements = USymbolicSetElementsCollector.collect(expr.secondCollection.updates)

    val composedFirstRef = composer.compose(expr.uctx.mkConcreteHeapRef(expr.firstAddress))
    val composedSecondRef = composer.compose(expr.uctx.mkConcreteHeapRef(expr.secondAddress))

    return composeSymbolicSetIntersectionSize(
        composer,
        composedFirstRef,
        composedSecondRef,
        expr.firstCollection,
        expr.secondCollection,
        firstElements.elements,
        secondElements.elements,
        firstSetContains = { composedElement ->
            expr.firstCollection.read(composedElement, composer)
        },
        secondSetContains = { composedElement ->
            expr.secondCollection.read(composedElement, composer)
        },
    )
}

private fun <USizeSort : USort, ElementSort : USort, AllocatedCollectionId, InputCollectionId> composeSymbolicSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    expr: UAllocatedWithInputSymbolicSetIntersectionSize<USizeSort, ElementSort, AllocatedCollectionId, InputCollectionId>
): UExpr<USizeSort> where AllocatedCollectionId : USymbolicCollectionId<UExpr<ElementSort>, UBoolSort, AllocatedCollectionId>,
                          InputCollectionId : USymbolicCollectionId<USymbolicSetElement<ElementSort>, UBoolSort, InputCollectionId> {
    val firstElements = USymbolicSetElementsCollector.collect(expr.firstCollection.updates)
    val secondElements = USymbolicSetElementsCollector.collect(expr.secondCollection.updates)

    val composedFirstRef = composer.compose(expr.uctx.mkConcreteHeapRef(expr.firstAddress))
    val composedSecondRef = composer.compose(expr.secondAddress)

    return composeSymbolicSetIntersectionSize(
        composer,
        composedFirstRef,
        composedSecondRef,
        expr.firstCollection,
        expr.secondCollection,
        firstElements.elements,
        secondElements.elements.map { it.second },
        firstSetContains = { composedElement ->
            expr.firstCollection.read(composedElement, composer)
        },
        secondSetContains = { composedElement ->
            expr.secondCollection.read(composedSecondRef to composedElement, composer)
        },
    )
}

private fun <USizeSort : USort, ElementSort : USort, InputCollectionId> composeSymbolicSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    expr: UInputWithInputSymbolicSetIntersectionSize<USizeSort, ElementSort, InputCollectionId>
): UExpr<USizeSort> where InputCollectionId : USymbolicCollectionId<USymbolicSetElement<ElementSort>, UBoolSort, InputCollectionId> {
    val firstElements = USymbolicSetElementsCollector.collect(expr.firstCollection.updates)
    val secondElements = USymbolicSetElementsCollector.collect(expr.secondCollection.updates)

    val composedFirstRef = composer.compose(expr.firstAddress)
    val composedSecondRef = composer.compose(expr.secondAddress)

    return composeSymbolicSetIntersectionSize(
        composer,
        composedFirstRef,
        composedSecondRef,
        expr.firstCollection,
        expr.secondCollection,
        firstElements.elements.map { it.second },
        secondElements.elements.map { it.second },
        firstSetContains = { composedElement ->
            expr.firstCollection.read(composedFirstRef to composedElement, composer)
        },
        secondSetContains = { composedElement ->
            expr.secondCollection.read(composedSecondRef to composedElement, composer)
        }
    )
}

private inline fun <USizeSort : USort, ElementSort : USort> composeSymbolicSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    composedFirstSetRef: UHeapRef,
    composedSecondSetRef: UHeapRef,
    firstCollection: USymbolicCollection<*, *, UBoolSort>,
    secondCollection: USymbolicCollection<*, *, UBoolSort>,
    firstSetElements: Iterable<UExpr<ElementSort>>,
    secondSetElements: Iterable<UExpr<ElementSort>>,
    firstSetContains: (UExpr<ElementSort>) -> UBoolExpr,
    secondSetContains: (UExpr<ElementSort>) -> UBoolExpr,
): UExpr<USizeSort> = when (val setCollectionId = firstCollection.collectionId) {
    is USymbolicSetId<*, *, *, *, *, *> -> composeSymbolicSetIntersectionSize(
        composer,
        setCollectionId.setRegionId(),
        composedFirstSetRef, composedSecondSetRef,
        firstCollection, secondCollection,
        firstSetElements, secondSetElements,
        firstSetContains, secondSetContains,
        getSetEntries = { region, ref ->
            (region as USetReadOnlyRegion<*, *, *>).setEntries(ref)
        },
        getEntryElement = {
            @Suppress("UNCHECKED_CAST")
            it.setElement as UExpr<ElementSort>
        },
        computeIntersection = { region, firstRef, secondRef ->
            (region as USetReadOnlyRegion<*, *, *>).setIntersectionSize(firstRef, secondRef)
        }
    )

    is USymbolicRefSetId<*, *, *, *> -> composeSymbolicSetIntersectionSize(
        composer,
        setCollectionId.setRegionId(),
        composedFirstSetRef, composedSecondSetRef,
        firstCollection, secondCollection,
        firstSetElements, secondSetElements,
        firstSetContains, secondSetContains,
        getSetEntries = { region, ref ->
            (region as URefSetReadOnlyRegion<*>).setEntries(ref)
        },
        getEntryElement = {
            @Suppress("UNCHECKED_CAST")
            it.setElement as UExpr<ElementSort>
        },
        computeIntersection = { region, firstRef, secondRef ->
            (region as URefSetReadOnlyRegion<*>).setIntersectionSize(firstRef, secondRef)
        }
    )

    else -> error("Unexpected set collection: $setCollectionId")
}

private inline fun <USizeSort : USort, ElementSort : USort, Entry> composeSymbolicSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    regionId: UMemoryRegionId<*, UBoolSort>,
    composedFirstSetRef: UHeapRef,
    composedSecondSetRef: UHeapRef,
    firstCollection: USymbolicCollection<*, *, UBoolSort>,
    secondCollection: USymbolicCollection<*, *, UBoolSort>,
    firstSetElements: Iterable<UExpr<ElementSort>>,
    secondSetElements: Iterable<UExpr<ElementSort>>,
    firstSetContains: (UExpr<ElementSort>) -> UBoolExpr,
    secondSetContains: (UExpr<ElementSort>) -> UBoolExpr,
    getSetEntries: (UReadOnlyMemoryRegion<*, UBoolSort>, UHeapRef) -> USymbolicSetEntries<Entry>,
    getEntryElement: (Entry) -> UExpr<ElementSort>,
    computeIntersection: (UReadOnlyMemoryRegion<*, UBoolSort>, UHeapRef, UHeapRef) -> UExpr<USizeSort>,
): UExpr<USizeSort> = tryComposeSymbolicSetIntersectionSize(
    composer, regionId, composedFirstSetRef, firstSetElements,
    firstSetContains, secondSetContains, getSetEntries, getEntryElement
) ?: tryComposeSymbolicSetIntersectionSize(
    composer, regionId, composedSecondSetRef, secondSetElements,
    firstSetContains, secondSetContains, getSetEntries, getEntryElement
) ?: applyCollectionsAndComposeIntersectionSize(
    composer, regionId, composedFirstSetRef, composedSecondSetRef,
    firstCollection, secondCollection, computeIntersection
)

private inline fun <USizeSort : USort, ElementSort : USort, Entry> tryComposeSymbolicSetIntersectionSize(
    composer: UComposer<*, USizeSort>,
    regionId: UMemoryRegionId<*, UBoolSort>,
    composedSetRef: UHeapRef,
    collectionElements: Iterable<UExpr<ElementSort>>,
    firstSetContains: (UExpr<ElementSort>) -> UBoolExpr,
    secondSetContains: (UExpr<ElementSort>) -> UBoolExpr,
    getSetEntries: (UReadOnlyMemoryRegion<*, UBoolSort>, UHeapRef) -> USymbolicSetEntries<Entry>,
    getEntryElement: (Entry) -> UExpr<ElementSort>,
): UExpr<USizeSort>? = with(composedSetRef.uctx.withSizeSort<USizeSort>()) {
    val region = composer.memory.getRegion(regionId)
    val entries = getSetEntries(region, composedSetRef)

    if (entries.isInput) return null

    val composedCollectionElements = mutableListOf<UExpr<ElementSort>>()
    collectionElements.mapTo(composedCollectionElements) { element ->
        composer.compose(element)
    }
    entries.entries.mapTo(composedCollectionElements) { entry ->
        getEntryElement(entry)
    }

    return composedCollectionElements.fold(mkSizeExpr(0)) { size, composedElement ->
        val firstContains = firstSetContains(composedElement)
        val secondContains = secondSetContains(composedElement)

        mkIte(
            mkAnd(firstContains, secondContains),
            { mkSizeAddExpr(size, mkSizeExpr(1)) },
            { size }
        )
    }
}

private inline fun <USizeSort : USort> applyCollectionsAndComposeIntersectionSize(
    composer: UComposer<*, USizeSort>,
    regionId: UMemoryRegionId<*, UBoolSort>,
    composedFirstRef: UHeapRef,
    composedSecondRef: UHeapRef,
    firstCollection: USymbolicCollection<*, *, UBoolSort>,
    secondCollection: USymbolicCollection<*, *, UBoolSort>,
    computeIntersection: (UReadOnlyMemoryRegion<*, UBoolSort>, UHeapRef, UHeapRef) -> UExpr<USizeSort>
): UExpr<USizeSort> {
    val writableMemory = composer.memory.toWritableMemory()
    firstCollection.applyTo(writableMemory, key = null, composer)
    secondCollection.applyTo(writableMemory, key = null, composer)

    val setRegion = writableMemory.getRegion(regionId)
    return computeIntersection(setRegion, composedFirstRef, composedSecondRef)
}
