package org.usvm

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.ksmt.expr.KArray2Store
import org.ksmt.expr.KArrayConst
import org.ksmt.expr.KArrayStore
import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort


/**
 * A specific evaluator for one-dimensional regions generalized by a single expression of a [KeySort].
 */
class UMemory1DArray<KeySort : USort, Sort : USort>(
    private val values: PersistentMap<UExpr<KeySort>, UExpr<Sort>>,
    private val constValue: UExpr<Sort>,
) : UMemoryRegion<KExpr<KeySort>, Sort> {

    /**
     * A constructor that is used in cases when we try to evaluate
     * an expression from a region that was never translated.
     */
    constructor(
        mappedConstValue: UExpr<Sort>,
    ) : this(persistentMapOf(), mappedConstValue)

    override fun read(key: KExpr<KeySort>): UExpr<Sort> = values.getOrDefault(key, constValue)

    override fun write(
        key: KExpr<KeySort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<KExpr<KeySort>, Sort> {
        when {
            guard.isFalse -> return this
            else -> require(guard.isTrue)
        }
        val newMap = values.put(key, value)
        return UMemory1DArray(newMap, constValue)
    }


    companion object {
        /**
         * A constructor that is used in regular cases for a region
         * that has a corresponding translator. It collects information
         * required for the region decoding using data about translated expressions,
         * resolved values from the [model] and the [mapping] from address expressions
         * to their concrete representation.
         */
        operator fun <KeySort : USort, Sort : USort> invoke(
            initialValue: KExpr<KArraySort<KeySort, Sort>>,
            model: KModel,
            mapping: Map<UHeapRef, UConcreteHeapRef>,
        ): UMemory1DArray<KeySort, Sort> {
            // Since the model contains only information about values we got from the outside,
            // we can translate and ask only about an initial value for the region.
            // All other values should be resolved earlier without asking the model.
            val evaluatedArray = model.eval(initialValue, isComplete = true)

            var valueCopy = evaluatedArray

            val stores = mutableMapOf<UExpr<KeySort>, UExpr<Sort>>()

            // Parse stores into the region, then collect a const value for the evaluated region.
            while (valueCopy !is KArrayConst<*, *>) {
                require(valueCopy is KArrayStore<KeySort, Sort>)

                val value = valueCopy.value.mapAddress(mapping)

                val mapAddress = valueCopy.index.mapAddress(mapping)
                stores[mapAddress] = value
                valueCopy = valueCopy.array
            }
            @Suppress("UNCHECKED_CAST")
            valueCopy as KArrayConst<KArraySort<KeySort, Sort>, Sort>

            val constValue = valueCopy.value.mapAddress(mapping)
            val values = stores
            return UMemory1DArray(values.toPersistentMap(), constValue)
        }
    }
}

/**
 * A specific evaluator for two-dimensional regions generalized be a pair
 * of two expressions with [Key1Sort] and [Key2Sort] sorts.
 */
class UMemory2DArray<Key1Sort : USort, Key2Sort : USort, Sort : USort>(
    val values: PersistentMap<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, UExpr<Sort>>,
    val constValue: UExpr<Sort>,
) : UMemoryRegion<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort> {
    /**
     * A constructor that is used in cases when we try to evaluate
     * an expression from a region that was never translated.
     */
    constructor(
        mappedConstValue: UExpr<Sort>,
    ) : this(persistentMapOf(), mappedConstValue)

    override fun read(key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>): UExpr<Sort> {
        return values.getOrDefault(key, constValue)
    }

    override fun write(
        key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UMemoryRegion<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort> {
        when {
            guard.isFalse -> return this
            else -> require(guard.isTrue)
        }
        val newMap = values.put(key, value)
        return UMemory2DArray(newMap, constValue)
    }

    companion object {
        /**
         * A constructor that is used in regular cases for a region
         * that has a corresponding translator. It collects information
         * required for the region decoding using data about translated expressions,
         * resolved values from the [model] and the [mapping] from address expressions
         * to their concrete representation.
         */
        operator fun <Key1Sort : USort, Key2Sort : USort, Sort : USort> invoke(
            initialValue: KExpr<KArray2Sort<Key1Sort, Key2Sort, Sort>>,
            model: KModel,
            mapping: Map<UHeapRef, UConcreteHeapRef>,
        ): UMemory2DArray<Key1Sort, Key2Sort, Sort> {
            val evaluatedArray = model.eval(initialValue, isComplete = true)

            var valueCopy = evaluatedArray

            val stores = mutableMapOf<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, UExpr<Sort>>()

            while (valueCopy !is KArrayConst<*, *>) {
                require(valueCopy is KArray2Store<Key1Sort, Key2Sort, Sort>)

                val value = valueCopy.value.mapAddress(mapping)

                val index0 = valueCopy.index0.mapAddress(mapping)
                val index1 = valueCopy.index1.mapAddress(mapping)

                stores[index0 to index1] = value
                valueCopy = valueCopy.array
            }

            @Suppress("UNCHECKED_CAST")
            valueCopy as KArrayConst<KArray2Sort<Key1Sort, Key2Sort, Sort>, Sort>

            val constValue = valueCopy.value.mapAddress(mapping)
            val values = stores
            return UMemory2DArray(values.toPersistentMap(), constValue)
        }
    }
}