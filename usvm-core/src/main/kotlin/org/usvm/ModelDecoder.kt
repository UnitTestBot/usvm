package org.usvm

import org.ksmt.expr.KExpr
import org.ksmt.expr.KInterpretedValue
import org.ksmt.solver.KModel
import org.ksmt.sort.KUninterpretedSort
import org.ksmt.utils.asExpr
import org.ksmt.utils.cast
import org.usvm.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS
import org.usvm.UContext.Companion.sampleValue
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf

interface UModelDecoder<Type, Memory, Model> {
    fun decode(memory: Memory, model: KModel): Model
}

fun <Field, Type, Method> buildDefaultTranslatorAndDecoder(
    ctx: UContext,
): Pair<UExprTranslator<Field, Type>, UModelDecoderBase<Field, Type, Method>> {
    val translator = UCachingExprTranslator<Field, Type>(ctx)

    val decoder = UModelDecoderBase<Field, Type, Method>(
        translator.registerIdxToTranslated,
        translator.indexedMethodReturnValueToTranslated,
        translator.translatedNullRef,
        translator.regionIdToTranslator.keys,
        translator.regionIdInitialValueProvider,
    )

    return translator to decoder
}

typealias AddressesMapping = Map<UExpr<UAddressSort>, UConcreteHeapRef>

open class UModelDecoderBase<Field, Type, Method>(
    protected val registerIdxToTranslated: Map<Int, UExpr<out USort>>,
    protected val indexedMethodReturnValueToTranslated: Map<Pair<*, Int>, UExpr<*>>,
    protected val translatedNullRef: UExpr<UAddressSort>,
    protected val translatedRegionIds: Set<URegionId<*, *>>,
    protected val regionIdInitialValueProvider: URegionIdInitialValueFactory,
) : UModelDecoder<Type, UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>> {
    private val ctx: UContext = translatedNullRef.uctx

    /**
     * Build a mapping from instances of an uninterpreted [UAddressSort]
     * to [UConcreteHeapRef] with integer addresses. It allows us to enumerate
     * equivalence classes of addresses and work with their number in the future.
     */
    private fun buildMapping(model: KModel): AddressesMapping {
        // Null is a special value that we want to translate in any case.
        val interpretedNullRef = model.eval(translatedNullRef, isComplete = true)

        val result = mutableMapOf<KExpr<KUninterpretedSort>, UConcreteHeapRef>()
        // Except the null value, it has the NULL_ADDRESS
        result[interpretedNullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)
        result[translatedNullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)

        val universe = model.uninterpretedSortUniverse(ctx.addressSort) ?: return result
        // All the numbers are enumerated from the INITIAL_INPUT_ADDRESS to the Int.MIN_VALUE
        var counter = INITIAL_INPUT_ADDRESS

        for (interpretedAddress in universe) {
            if (interpretedAddress == interpretedNullRef) {
                continue
            }
            result[interpretedAddress] = ctx.mkConcreteHeapRef(counter--)
        }

        return result
    }

    override fun decode(
        memory: UMemoryBase<Field, Type, Method>,
        model: KModel,
    ): UModelBase<Field, Type> {
        val addressesMapping = buildMapping(model)

        val stack = decodeStack(model, addressesMapping)
        val heap = decodeHeap(model, addressesMapping)
        val types = UTypeModel(ctx, memory.typeSystem, typeByAddr = emptyMap())
        val mocks = decodeMocker(model, addressesMapping)

        return UModelBase(ctx, stack, heap, types, mocks)
    }

    private fun decodeStack(model: KModel, addressesMapping: AddressesMapping): URegistersStackModel {
        val registers = registerIdxToTranslated.replaceUninterpretedConsts(model, addressesMapping)

        return URegistersStackModel(registers)
    }

    /**
     * Constructs a [UHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
    private fun decodeHeap(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): UHeapModel<Field, Type> {
        val regionEvaluatorProvider = URegionEvaluatorForHeapModelFactory(
            model,
            addressesMapping,
            translatedRegionIds,
            regionIdInitialValueProvider,
        )

        return UHeapModel(
            addressesMapping.getValue(translatedNullRef),
            regionEvaluatorProvider,
            persistentMapOf(),
            persistentMapOf(),
            persistentMapOf()
        )
    }

    private fun decodeMocker(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): UIndexedMockModel<Method> {
        val values = indexedMethodReturnValueToTranslated.replaceUninterpretedConsts(model, addressesMapping)

        return UIndexedMockModel(values)
    }

    /**
     * Since expressions from [this] might have the [UAddressSort] and therefore
     * they could be uninterpreted constants, we have to replace them with
     * corresponding concrete addresses from the [addressesMapping].
     */
    private fun <K> Map<K, UExpr<out USort>>.replaceUninterpretedConsts(
        model: KModel,
        addressesMapping: AddressesMapping,
    ): Map<K, UExpr<out USort>> {
        val values = mapValues { (_, expr) ->
            val value = model.eval(expr, isComplete = true)
            val transformedValue = value.mapAddress(addressesMapping)

            transformedValue
        }

        return values
    }

}

class URegistersStackModel(private val registers: Map<Int, UExpr<out USort>>) : URegistersStackEvaluator {
    override fun <Sort : USort> eval(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> = registers.getOrDefault(registerIndex, sort.sampleValue()).asExpr(sort)
}

class UHeapModel<Field, ArrayType>(
    private val nullRef: UConcreteHeapRef,
    private val regionEvaluatorProvider: URegionEvaluatorFactory,
    private var resolvedInputFields: PersistentMap<Field, URegionEvaluator<UHeapRef, out USort>>,
    private var resolvedInputArrays: PersistentMap<ArrayType, URegionEvaluator<Pair<UHeapRef, USizeExpr>, out USort>>,
    private var resolvedInputLengths: PersistentMap<ArrayType, URegionEvaluator<UHeapRef, USizeSort>>,
) : USymbolicHeap<Field, ArrayType> {
    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        @Suppress("UNCHECKED_CAST")
        val regionEvaluator = resolvedInputFields.getOrElse(field) {
            val regionId = UInputFieldId(field, sort)
            val evaluator = regionEvaluatorProvider.provide(regionId)
            resolvedInputFields = resolvedInputFields.put(field, evaluator)
            evaluator
        } as URegionEvaluator<UHeapRef, Sort>

        return regionEvaluator.select(ref).asExpr(sort)
    }

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        elementSort: Sort,
    ): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val key = ref to index

        @Suppress("UNCHECKED_CAST")
        val regionEvaluator = resolvedInputArrays.getOrElse(arrayType) {
            val regionId = UInputArrayId(arrayType, elementSort)
            val evaluator = regionEvaluatorProvider.provide(regionId)
            resolvedInputArrays = resolvedInputArrays.put(arrayType, evaluator)
            evaluator
        } as URegionEvaluator<Pair<UHeapRef, USizeExpr>, Sort>

        return regionEvaluator.select(key).asExpr(elementSort)
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val regionEvaluator = resolvedInputLengths.getOrElse(arrayType) {
            val regionId = UInputArrayLengthId(arrayType, ref.uctx.sizeSort)
            val evaluator = regionEvaluatorProvider.provide(regionId)
            resolvedInputLengths = resolvedInputLengths.put(arrayType, evaluator)
            evaluator
        }

        return regionEvaluator.select(ref)
    }

    override fun <Sort : USort> writeField(
        ref: UHeapRef,
        field: Field,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        // Since all values in the model are interpreted, we can check the exact guard value.
        when {
            guard.isFalse -> return
            else -> require(guard.isTrue)
        }

        val valueToWrite = value.asExpr(sort)

        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        @Suppress("UNCHECKED_CAST")
        val regionEvaluator = resolvedInputFields.getOrElse(field) {
            val regionId = UInputFieldId(field, sort)
            val evaluator = regionEvaluatorProvider.provide(regionId)
            resolvedInputFields = resolvedInputFields.put(field, evaluator)
            evaluator
        } as URegionEvaluator<UHeapRef, Sort>

        regionEvaluator.write(ref, valueToWrite)
    }

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        elementSort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) {
        // Since all values in the model are interpreted, we can check the exact guard value.
        when {
            guard.isFalse -> return
            else -> require(guard.isTrue)
        }

        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(index is KInterpretedValue<USizeSort>)
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val valueToWrite = value.asExpr(elementSort)

        @Suppress("UNCHECKED_CAST")
        val regionEvaluator = resolvedInputArrays.getOrElse(type) {
            val regionId = UInputArrayId(type, elementSort)
            val evaluator = regionEvaluatorProvider.provide(regionId)
            resolvedInputArrays = resolvedInputArrays.put(type, evaluator)
            evaluator
        } as URegionEvaluator<Pair<UHeapRef, USizeExpr>, Sort>

        regionEvaluator.write(ref to index, valueToWrite)
    }

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(size is KInterpretedValue<USizeSort>)
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val reginEvaluator = resolvedInputLengths.getOrElse(arrayType) {
            val regionId = UInputArrayLengthId(arrayType, ref.uctx.sizeSort)
            val evaluator = regionEvaluatorProvider.provide(regionId)
            resolvedInputLengths = resolvedInputLengths.put(arrayType, evaluator)
            evaluator
        }

        reginEvaluator.write(ref, size)
    }

    override fun <Sort : USort> memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ): Unit = error("Illegal operation for a model")

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ): Unit = error("Illegal operation for a model")

    override fun allocate() = error("Illegal operation for a model")

    override fun allocateArray(count: USizeExpr): UConcreteHeapAddress = error("Illegal operation for a model")

    override fun clone(): UHeapModel<Field, ArrayType> =
        UHeapModel(
            nullRef,
            regionEvaluatorProvider,
            resolvedInputFields.mapValues { evaluator -> evaluator.clone() },
            resolvedInputArrays.mapValues { evaluator -> evaluator.clone() },
            resolvedInputLengths.mapValues { evaluator -> evaluator.clone() },
        )

    override fun nullRef(): UConcreteHeapRef = nullRef

    override fun toMutableHeap(): UHeapModel<Field, ArrayType> = clone()
}

inline private fun <K, V> PersistentMap<K, V>.mapValues(crossinline mapper: (V) -> V): PersistentMap<K, V> =
    mutate { old -> old.replaceAll { _, value -> mapper(value) } }

/**
 * If [this] value is an instance of address expression, returns
 * an expression with a corresponding concrete address, otherwise
 * returns [this] unchanched.
 */
fun <S : USort> UExpr<S>.mapAddress(
    addressesMapping: AddressesMapping,
): UExpr<S> = if (sort == uctx.addressSort) {
    addressesMapping.getValue(asExpr(uctx.addressSort)).asExpr(sort)
} else {
    this
}

private class URegionEvaluatorForHeapModelFactory(
    model: KModel,
    val addressesMapping: AddressesMapping,
    translatedRegionIds: Set<URegionId<*, *>>,
    regionIdInitialValueFactory: URegionIdInitialValueFactory,
) : URegionEvaluatorFactory, URegionIdVisitor<URegionEvaluator<*, *>> {

    private val evaluatorsForTranslatedRegions: MutableMap<URegionId<*, *>, URegionEvaluator<*, *>>

    init {
        val regionEvaluatorProvider =
            URegionEvaluatorFromKModelFactory(model, addressesMapping, regionIdInitialValueFactory)

        evaluatorsForTranslatedRegions = translatedRegionIds.associateWithTo(mutableMapOf()) { regionId ->
            regionEvaluatorProvider.apply(regionId)
        }
    }

    override fun <Key, Sort : USort> provide(regionId: URegionId<Key, Sort>): URegionEvaluator<Key, Sort> =
        evaluatorsForTranslatedRegions.getOrElse(regionId) {
            apply(regionId)
        }.cast()

    override fun <Field, Sort : USort> visit(regionId: UInputFieldId<Field, Sort>): U1DArrayEvaluator<UAddressSort, Sort> {
        // If some region has a default value, it means that the region is an allocated one.
        // All such regions must be processed earlier, and we won't have them here.
        require(regionId.defaultValue == null)
        // So, for these region we should take sample values for theis sorts.
        val mappedConstValue = regionId.sort.sampleValue().mapAddress(addressesMapping)
        return U1DArrayEvaluator(mappedConstValue)
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UAllocatedArrayId<ArrayType, Sort>): URegionEvaluator<*, *> {
        error("Allocated arrays should be evaluated implicitly")
    }

    override fun <ArrayType, Sort : USort> visit(regionId: UInputArrayId<ArrayType, Sort>): U2DArrayEvaluator<UAddressSort, USizeSort, Sort> {
        // If some region has a default value, it means that the region is an allocated one.
        // All such regions must be processed earlier, and we won't have them here.
        require(regionId.defaultValue == null)
        // So, for these region we should take sample values for theis sorts.
        val mappedConstValue = regionId.sort.sampleValue().mapAddress(addressesMapping)
        return U2DArrayEvaluator(mappedConstValue)
    }

    override fun <ArrayType> visit(regionId: UInputArrayLengthId<ArrayType>): U1DArrayEvaluator<UAddressSort, USizeSort> {
        // If some region has a default value, it means that the region is an allocated one.
        // All such regions must be processed earlier, and we won't have them here.
        require(regionId.defaultValue == null)
        // So, for these region we should take sample values for theis sorts.
        val mappedConstValue = regionId.sort.sampleValue().mapAddress(addressesMapping)
        return U1DArrayEvaluator(mappedConstValue)
    }
}
