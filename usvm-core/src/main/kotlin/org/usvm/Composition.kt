package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.utils.cast

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Field, Type>(
    ctx: UContext,
    internal val stackEvaluator: URegistersStackEvaluator,
    internal val heapEvaluator: UReadOnlySymbolicHeap<Field, Type>,
    internal val typeEvaluator: UTypeEvaluator<Type>,
    internal val mockEvaluator: UMockEvaluator
) : UExprTransformer<Field, Type>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>
    ): UExpr<Sort> = with(expr) { stackEvaluator.eval(idx, sort) }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>
    ): UExpr<Sort> = mockEvaluator.eval(expr)

    override fun transform(expr: UIsExpr<Type>): UBoolExpr = with(expr) {
        val composedAddress = compose(ref)
        typeEvaluator.evalIs(composedAddress, type)
    }

    override fun transform(expr: UArrayLength<Type>): USizeExpr = with(expr) {
        val composedAddress = compose(address)

        val mappedRegion = region.map(
            inputArrayLengthKeyMapper,
            this@UComposer,
            inputArrayLengthInstantiatorConstructor
        )

        mappedRegion.read(composedAddress)
    }

    override fun <Sort : USort> transform(
        expr: UInputArrayReading<Type, Sort>
    ): UExpr<Sort> = with(expr) {
        val composedAddress = compose(address)
        val composedIndex = compose(index)

        val instantiatorConstructor = { mappedUpdates: UMemoryUpdates<USymbolicArrayIndex, Sort> ->
            { key: USymbolicArrayIndex, memoryRegion: UInputArrayMemoryRegion<Type, Sort> ->
                val arrayType = memoryRegion.inputArrayType
                val elementSort = memoryRegion.sort

                heapEvaluator.applyUpdatesAndReadValue(
                    mappedUpdates,
                    { mutableHeap, pinpointUpdate ->
                        mutableHeap.writeArrayIndex(
                            pinpointUpdate.key.first,
                            pinpointUpdate.key.second,
                            arrayType,
                            elementSort,
                            pinpointUpdate.value
                        )
                    },
                    { mutableHeap, rangeUpdate ->
                        mutableHeap.memcpy(rangeUpdate.fromKey, rangeUpdate.toKey, rangeUpdate.region)
                    },
                    readingOperation = { it.readArrayIndex(key.first, key.second, arrayType, elementSort).cast() }
                )
            }
        }

        val mappedRegion = region.map(inputArrayKeyMapper, this@UComposer, instantiatorConstructor)

        mappedRegion.read(composedAddress to composedIndex)
    }

    override fun <Sort : USort> transform(
        expr: UAllocatedArrayReading<Type, Sort>
    ): UExpr<Sort> = with(expr) {
        val composedIndex = compose(index)
        val heapRef = uctx.mkConcreteHeapRef(region.allocatedAddress)

        val allocatedArrayInstantiatorConstructor = { mappedUpdates: UMemoryUpdates<USizeExpr, Sort> ->
            { key: USizeExpr, _: UAllocatedArrayMemoryRegion<Type, Sort> ->
                val arrayType = region.allocatedArrayType
                val elementSort = region.sort

                heapEvaluator.applyUpdatesAndReadValue(
                    mappedUpdates,
                    { mutableHeap, pinpointUpdate ->
                        mutableHeap.writeArrayIndex(
                            heapRef,
                            pinpointUpdate.key,
                            arrayType,
                            elementSort,
                            pinpointUpdate.value
                        )
                    },
                    { mutableHeap, rangedUpdate ->
                        mutableHeap.memcpy(rangedUpdate.fromKey, rangedUpdate.toKey, rangedUpdate.region)
                    },
                    readingOperation = { it.readArrayIndex(heapRef, key, arrayType, elementSort).cast() }
                )
            }
        }

        val mappedRegion = region.map(
            allocatedArrayReadingKeyMapper,
            this@UComposer,
            allocatedArrayInstantiatorConstructor
        )

        mappedRegion.read(composedIndex)
    }

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr

    override fun <Sort : USort> transform(expr: UFieldReading<Field, Sort>): UExpr<Sort> = with(expr) {
        val composedAddress = compose(address)

        val instantiatorConstructor = { mappedUpdates: UMemoryUpdates<UHeapRef, Sort> ->
            { key: UHeapRef, memoryRegion: UInputFieldMemoryRegion<Field, Sort> ->
                val field = memoryRegion.field

                heapEvaluator.applyUpdatesAndReadValue(
                    mappedUpdates,
                    { mutableHeap, pinpointUpdate ->
                        mutableHeap.writeField(pinpointUpdate.key, field, sort, pinpointUpdate.value)
                    },
                    { mutableHeap, rangedUpdate ->
                        mutableHeap.memcpy(rangedUpdate.fromKey, rangedUpdate.toKey, rangedUpdate.region)
                    },
                    readingOperation = { it.readField(key, field, sort).cast() }
                )
            }
        }

        val mappedRegion = region.map(fieldReadingKeyMapper, this@UComposer, instantiatorConstructor)

        mappedRegion.read(composedAddress)
    }

    // Extracted into a field to improve performance since it doesn't depend on input arguments
    private val inputArrayLengthInstantiatorConstructor: (UMemoryUpdates<UHeapRef, USizeSort>) -> UInstantiator<UInputArrayLengthId<Type>, UHeapRef, USizeSort> =
        { mappedUpdates: UMemoryUpdates<UHeapRef, USizeSort> ->
            { key: UHeapRef, memoryRegion: UInputArrayLengthMemoryRegion<Type> ->
                val arrayType = memoryRegion.inputLengthArrayType

                heapEvaluator.applyUpdatesAndReadValue(
                    mappedUpdates,
                    { mutableMap, pinpointUpdate ->
                        mutableMap.writeArrayLength(
                            pinpointUpdate.key,
                            pinpointUpdate.value,
                            arrayType
                        )
                    },
                    { _, _ -> error("URangeUpdateNode is unsupported for UArrayLength regions") },
                    readingOperation = { it.readArrayLength(key, arrayType) }
                )
            }
        }

    private val allocatedArrayReadingKeyMapper: (USizeExpr) -> KExpr<USizeSort> = { key: USizeExpr ->
        val composedKey = compose(key)
        // It is important to return the exact same key if there were no changes during composition
        if (composedKey === key) key else composedKey
    }

    private val inputArrayLengthKeyMapper: (UHeapRef) -> UHeapRef = { key ->
        val composedKey = compose(key)
        // It is important to return the exact same key if there were no changes during composition
        if (composedKey === key) key else composedKey
    }

    private val inputArrayKeyMapper: (USymbolicArrayIndex) -> USymbolicArrayIndex = { key ->
        val (heapRef, sizeExpr) = key
        val composedHeapRef = compose(heapRef)
        val composedSizeExpr = compose(sizeExpr)

        // It is important to return the exact same key if there were no changes during composition
        if (composedHeapRef === heapRef && composedSizeExpr === sizeExpr) {
            key
        } else {
            composedHeapRef to composedSizeExpr
        }
    }

    private val fieldReadingKeyMapper: (UHeapRef) -> KExpr<UAddressSort> = { key: UHeapRef ->
        val composedHeapRef = compose(key)
        // It is important to return the exact same key if there were no changes during composition
        if (composedHeapRef === key) key else composedHeapRef
    }

    /**
     * Applies a sequence of [updates] to [this] and returns a result
     * of the [readingOperation] from the modified heap.
     */
    private inline fun <Ref, Value, SizeT, Key, Sort : USort, R : UExpr<Sort>> UReadOnlyHeap<Ref, Value, SizeT, Field, Type>.applyUpdatesAndReadValue(
        updates: UMemoryUpdates<Key, Sort>,
        pinpointUpdateOperation: (UHeap<Ref, Value, SizeT, Field, Type>, UPinpointUpdateNode<Key, Sort>) -> Unit,
        rangeUpdateOperation: (UHeap<Ref, Value, SizeT, Field, Type>, URangedUpdateNode<Key, Sort>) -> Unit,
        readingOperation: (UHeap<Ref, Value, SizeT, Field, Type>) -> R
    ): R {
        // Create a copy of this heap to avoid its modification
        val heapToApplyUpdates = toMutableHeap()

        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> pinpointUpdateOperation(heapToApplyUpdates, it)
                is URangedUpdateNode<Key, Sort> -> rangeUpdateOperation(heapToApplyUpdates, it)
            }
        }

        // Return a result of a reading from the modified heap
        return readingOperation(heapToApplyUpdates)
    }
}
