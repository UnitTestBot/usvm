package org.usvm

import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.set.length.UInputSetLengthReading
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.set.USymbolicSetElement
import org.usvm.collection.set.USymbolicSetElementsCollector
import org.usvm.collection.set.USymbolicSetEntries
import org.usvm.collection.set.length.UAllocatedWithAllocatedSymbolicSetIntersectionSize
import org.usvm.collection.set.length.UAllocatedWithInputSymbolicSetIntersectionSize
import org.usvm.collection.set.length.UInputWithInputSymbolicSetIntersectionSize
import org.usvm.collection.set.length.USymbolicSetIntersectionSize
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.primitive.USetReadOnlyRegion
import org.usvm.collection.set.primitive.USymbolicSetId
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.collection.set.ref.URefSetReadOnlyRegion
import org.usvm.collection.set.ref.USymbolicRefSetId
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.regions.Region

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Type, USizeSort : USort>(
    ctx: UContext<USizeSort>,
    val memory: UReadOnlyMemory<Type>
) : UExprTransformer<Type, USizeSort>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <T : USort> transform(expr: UIteExpr<T>): UExpr<T> =
        transformExprAfterTransformed(expr, expr.condition) { condition ->
            when {
                condition.isTrue -> apply(expr.trueBranch)
                condition.isFalse -> apply(expr.falseBranch)
                else -> super.transform(expr)
            }
        }

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>,
    ): UExpr<Sort> = with(expr) { memory.stack.readRegister(idx, sort) }

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>,
    ): UExpr<Sort> = memory.mocker.eval(expr)

    override fun <Sort : USort> transform(
        expr: UTrackedSymbol<Sort>
    ): UExpr<Sort> = memory.mocker.eval(expr)

    override fun transform(expr: UIsSubtypeExpr<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            memory.types.evalIsSubtype(ref, expr.supertype)
        }

    override fun transform(expr: UIsSupertypeExpr<Type>): UBoolExpr =
        transformExprAfterTransformed(expr, expr.ref) { ref ->
            memory.types.evalIsSupertype(ref, expr.subtype)
        }

    fun <CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort> transformCollectionReading(
        expr: UCollectionReading<CollectionId, Key, Sort>,
        key: Key,
    ): UExpr<Sort> = with(expr) {
        val mappedKey = collection.collectionId.keyInfo().mapKey(key, this@UComposer)
        return collection.read(mappedKey, this@UComposer)
    }

    override fun transform(expr: UInputArrayLengthReading<Type, USizeSort>): UExpr<USizeSort> =
        transformCollectionReading(expr, expr.address)

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort, USizeSort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.address to expr.index)

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort, USizeSort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.index)

    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        transformCollectionReading(expr, expr.address)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.key)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.address to expr.key)

    override fun <Sort : USort> transform(
        expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.keyRef)

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.mapRef)

    override fun <Sort : USort> transform(
        expr: UInputRefMapWithInputKeysReading<Type, Sort>
    ): UExpr<Sort> = transformCollectionReading(expr, expr.mapRef to expr.keyRef)

    override fun transform(expr: UInputSetLengthReading<Type, USizeSort>): UExpr<USizeSort> =
        transformCollectionReading(expr, expr.address)

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedSetReading<Type, ElemSort, Reg>
    ): UBoolExpr = transformCollectionReading(expr, expr.element)

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<Type, ElemSort, Reg>
    ): UBoolExpr = transformCollectionReading(expr, expr.address to expr.element)

    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<Type>): UBoolExpr =
        transformCollectionReading(expr, expr.elementRef)

    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<Type>): UBoolExpr =
        transformCollectionReading(expr, expr.setRef)

    override fun transform(expr: UInputRefSetWithInputElementsReading<Type>): UBoolExpr =
        transformCollectionReading(expr, expr.setRef to expr.elementRef)

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = memory.nullRef()

    override fun transform(expr: USymbolicSetIntersectionSize<USizeSort>): UExpr<USizeSort> = when (expr) {
        is UAllocatedWithAllocatedSymbolicSetIntersectionSize<USizeSort, *, *> -> composeSymbolicSetIntersectionSize(expr)
        is UAllocatedWithInputSymbolicSetIntersectionSize<USizeSort, *, *, *> -> composeSymbolicSetIntersectionSize(expr)
        is UInputWithInputSymbolicSetIntersectionSize<USizeSort, *, *> -> composeSymbolicSetIntersectionSize(expr)
    }

    private fun <ElementSort : USort, AllocatedCollectionId> composeSymbolicSetIntersectionSize(
        expr: UAllocatedWithAllocatedSymbolicSetIntersectionSize<USizeSort, ElementSort, AllocatedCollectionId>
    ): UExpr<USizeSort> where AllocatedCollectionId : USymbolicCollectionId<UExpr<ElementSort>, UBoolSort, AllocatedCollectionId> {
        val firstElements = USymbolicSetElementsCollector.collect(expr.firstCollection.updates)
        val secondElements = USymbolicSetElementsCollector.collect(expr.secondCollection.updates)

        val composedFirstRef = compose(expr.uctx.mkConcreteHeapRef(expr.firstAddress))
        val composedSecondRef = compose(expr.uctx.mkConcreteHeapRef(expr.secondAddress))

        return composeSymbolicSetIntersectionSize(
            composedFirstRef,
            composedSecondRef,
            expr.firstCollection,
            expr.secondCollection,
            firstElements.elements,
            secondElements.elements,
            firstSetContains = { composedElement ->
                expr.firstCollection.read(composedElement, this@UComposer)
            },
            secondSetContains = { composedElement ->
                expr.secondCollection.read(composedElement, this@UComposer)
            },
        )
    }

    private fun <ElementSort : USort, AllocatedCollectionId, InputCollectionId> composeSymbolicSetIntersectionSize(
        expr: UAllocatedWithInputSymbolicSetIntersectionSize<USizeSort, ElementSort, AllocatedCollectionId, InputCollectionId>
    ): UExpr<USizeSort> where AllocatedCollectionId : USymbolicCollectionId<UExpr<ElementSort>, UBoolSort, AllocatedCollectionId>,
                              InputCollectionId : USymbolicCollectionId<USymbolicSetElement<ElementSort>, UBoolSort, InputCollectionId> {
        val firstElements = USymbolicSetElementsCollector.collect(expr.firstCollection.updates)
        val secondElements = USymbolicSetElementsCollector.collect(expr.secondCollection.updates)

        val composedFirstRef = compose(expr.uctx.mkConcreteHeapRef(expr.firstAddress))
        val composedSecondRef = compose(expr.secondAddress)

        return composeSymbolicSetIntersectionSize(
            composedFirstRef,
            composedSecondRef,
            expr.firstCollection,
            expr.secondCollection,
            firstElements.elements,
            secondElements.elements.map { it.second },
            firstSetContains = { composedElement ->
                expr.firstCollection.read(composedElement, this@UComposer)
            },
            secondSetContains = { composedElement ->
                expr.secondCollection.read(composedSecondRef to composedElement, this@UComposer)
            },
        )
    }

    private fun <ElementSort : USort, InputCollectionId> composeSymbolicSetIntersectionSize(
        expr: UInputWithInputSymbolicSetIntersectionSize<USizeSort, ElementSort, InputCollectionId>
    ): UExpr<USizeSort> where InputCollectionId : USymbolicCollectionId<USymbolicSetElement<ElementSort>, UBoolSort, InputCollectionId> {
        val firstElements = USymbolicSetElementsCollector.collect(expr.firstCollection.updates)
        val secondElements = USymbolicSetElementsCollector.collect(expr.secondCollection.updates)

        val composedFirstRef = compose(expr.firstAddress)
        val composedSecondRef = compose(expr.secondAddress)

        return composeSymbolicSetIntersectionSize(
            composedFirstRef,
            composedSecondRef,
            expr.firstCollection,
            expr.secondCollection,
            firstElements.elements.map { it.second },
            secondElements.elements.map { it.second },
            firstSetContains = { composedElement ->
                expr.firstCollection.read(composedFirstRef to composedElement, this@UComposer)
            },
            secondSetContains = { composedElement ->
                expr.secondCollection.read(composedSecondRef to composedElement, this@UComposer)
            }
        )
    }

    private inline fun <ElementSort : USort> composeSymbolicSetIntersectionSize(
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

    private inline fun <ElementSort : USort, Entry> composeSymbolicSetIntersectionSize(
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
        regionId, composedFirstSetRef, firstSetElements,
        firstSetContains, secondSetContains, getSetEntries, getEntryElement
    ) ?: tryComposeSymbolicSetIntersectionSize(
        regionId, composedSecondSetRef, secondSetElements,
        firstSetContains, secondSetContains, getSetEntries, getEntryElement
    ) ?: applyCollectionsAndComposeIntersectionSize(
        regionId, composedFirstSetRef, composedSecondSetRef,
        firstCollection, secondCollection, computeIntersection
    )

    private inline fun <ElementSort : USort, Entry> tryComposeSymbolicSetIntersectionSize(
        regionId: UMemoryRegionId<*, UBoolSort>,
        composedSetRef: UHeapRef,
        collectionElements: Iterable<UExpr<ElementSort>>,
        firstSetContains: (UExpr<ElementSort>) -> UBoolExpr,
        secondSetContains: (UExpr<ElementSort>) -> UBoolExpr,
        getSetEntries: (UReadOnlyMemoryRegion<*, UBoolSort>, UHeapRef) -> USymbolicSetEntries<Entry>,
        getEntryElement: (Entry) -> UExpr<ElementSort>,
    ): UExpr<USizeSort>? = with(composedSetRef.uctx.withSizeSort<USizeSort>()) {
        val region = memory.getRegion(regionId)
        val entries = getSetEntries(region, composedSetRef)

        if (entries.isInput) return null

        val composedCollectionElements = mutableListOf<UExpr<ElementSort>>()
        collectionElements.mapTo(composedCollectionElements) { element ->
            compose(element)
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

    private inline fun applyCollectionsAndComposeIntersectionSize(
        regionId: UMemoryRegionId<*, UBoolSort>,
        composedFirstRef: UHeapRef,
        composedSecondRef: UHeapRef,
        firstCollection: USymbolicCollection<*, *, UBoolSort>,
        secondCollection: USymbolicCollection<*, *, UBoolSort>,
        computeIntersection: (UReadOnlyMemoryRegion<*, UBoolSort>, UHeapRef, UHeapRef) -> UExpr<USizeSort>
    ): UExpr<USizeSort> {
        val writableMemory = memory.toWritableMemory()
        firstCollection.applyTo(writableMemory, null, this@UComposer)
        secondCollection.applyTo(writableMemory, null, this@UComposer)

        val setRegion = writableMemory.getRegion(regionId)
        return computeIntersection(setRegion, composedFirstRef, composedSecondRef)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T : USort> UComposer<*, *>?.compose(expr: UExpr<T>) = this?.apply(expr) ?: expr
