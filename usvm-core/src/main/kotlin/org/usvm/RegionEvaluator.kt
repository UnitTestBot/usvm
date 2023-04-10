package org.usvm

import org.ksmt.expr.KArray2Store
import org.ksmt.expr.KArrayConst
import org.ksmt.expr.KArrayStore
import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArraySort
import org.ksmt.utils.cast

interface URegionEvaluator<Key, Sort : USort> {
    fun select(key: Key): UExpr<Sort>
    fun write(key: Key, expr: UExpr<Sort>)
    fun clone(): URegionEvaluator<Key, Sort>
}

/**
 * A specific evaluator for one-dimensional regions generalized by a single expression of a [KeySort].
 */
class U1DArrayEvaluator<KeySort : USort, Sort : USort> private constructor(
    private val values: MutableMap<UExpr<KeySort>, UExpr<Sort>>,
    private val constValue: UExpr<Sort>,
) : URegionEvaluator<KExpr<KeySort>, Sort> {

    /**
     * A constructor that is used in cases when we try to evaluate
     * an expression from a region that was never translated.
     */
    constructor(
        mappedConstValue: UExpr<Sort>,
    ) : this(mutableMapOf(), mappedConstValue)

    override fun select(key: KExpr<KeySort>): UExpr<Sort> = values.getOrDefault(key, constValue)

    override fun write(key: KExpr<KeySort>, expr: UExpr<Sort>) {
        values[key] = expr
    }

    override fun clone(): U1DArrayEvaluator<KeySort, Sort> =
        U1DArrayEvaluator(
            values.toMutableMap(),
            constValue
        )

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
        ): U1DArrayEvaluator<KeySort, Sort> {
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
            return U1DArrayEvaluator(values, constValue)
        }
    }
}

/**
 * A specific evaluator for two-dimensional regions generalized be a pair
 * of two expressions with [Key1Sort] and [Key2Sort] sorts.
 */
class U2DArrayEvaluator<Key1Sort : USort, Key2Sort : USort, Sort : USort> private constructor(
    val values: MutableMap<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, UExpr<Sort>>,
    val constValue: UExpr<Sort>,
) : URegionEvaluator<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort> {
    /**
     * A constructor that is used in cases when we try to evaluate
     * an expression from a region that was never translated.
     */
    constructor(
        mappedConstValue: UExpr<Sort>,
    ) : this(mutableMapOf(), mappedConstValue)

    override fun select(key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>): UExpr<Sort> {
        return values.getOrDefault(key, constValue)
    }

    override fun write(key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, expr: UExpr<Sort>) {
        values[key] = expr
    }

    override fun clone(): U2DArrayEvaluator<Key1Sort, Key2Sort, Sort> =
        U2DArrayEvaluator(
            values.toMutableMap(),
            constValue
        )

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
        ): U2DArrayEvaluator<Key1Sort, Key2Sort, Sort> {
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
            return U2DArrayEvaluator(values, constValue)
        }
    }
}

interface URegionEvaluatorProvider {
    fun <Key, Sort : USort> provide(
        regionId: URegionId<Key, Sort>,
    ): URegionEvaluator<Key, Sort>
}

open class URegionEvaluatorFromKModelProvider(
    private val model: KModel,
    private val mapping: Map<KExpr<UAddressSort>, UConcreteHeapRef>,
    private val regionIdInitialValueProvider: URegionIdInitialValueProvider,
) : URegionEvaluatorProvider, URegionIdVisitor<URegionEvaluator<*, *>> {

    /**
     * Returns an evaluator for [regionId].
     * Note that it is a translator-specific evaluator that
     * knows how to decode values from the [model].
     *
     * [mapping] contains information about matching expressions of the
     * address sort and their concrete (evaluated) representations.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <Key, Sort : USort> provide(
        regionId: URegionId<Key, Sort>,
    ): URegionEvaluator<Key, Sort> = apply(regionId) as URegionEvaluator<Key, Sort>

    override fun <Field, Sort : USort> visit(regionId: UInputFieldId<Field, Sort>): URegionEvaluator<UHeapRef, Sort> =
        U1DArrayEvaluator(regionIdInitialValueProvider.apply(regionId).cast(), model, mapping)

    override fun <ArrayType, Sort : USort> visit(regionId: UAllocatedArrayId<ArrayType, Sort>): URegionEvaluator<USizeExpr, Sort> =
        U1DArrayEvaluator(regionIdInitialValueProvider.apply(regionId).cast(), model, mapping)

    override fun <ArrayType, Sort : USort> visit(regionId: UInputArrayId<ArrayType, Sort>): URegionEvaluator<USymbolicArrayIndex, Sort> =
        U2DArrayEvaluator(regionIdInitialValueProvider.apply(regionId).cast(), model, mapping)

    override fun <ArrayType> visit(regionId: UInputArrayLengthId<ArrayType>): URegionEvaluator<UHeapRef, USizeSort> =
        U1DArrayEvaluator(regionIdInitialValueProvider.apply(regionId).cast(), model, mapping)
}