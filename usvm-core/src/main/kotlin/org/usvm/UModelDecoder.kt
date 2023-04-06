package org.usvm

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.expr.KExpr
import org.ksmt.expr.KInterpretedValue
import org.ksmt.solver.KModel
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.sort.KUninterpretedSort
import org.ksmt.utils.asExpr
import org.usvm.UAddressCounter.Companion.INITIAL_INPUT_ADDRESS
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS

interface UModelDecoder<Type, Memory, Model> {
    fun decode(memory: Memory, model: KModel): Model
}

class UModelDecoderBase<Field, Type, Method>(
    translator: UExprTranslator<Field, Type>,
) : UModelDecoder<Type, UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>, UTranslationObserver {

    private val ctx: UContext = translator.ctx as UContext

    init {
        translator.attachObserver(this)
    }

    private val regionToTranslator = mutableMapOf<URegionId<*, *>, URegionTranslator<*, *, *, *>>()
    private val registerIdxToTranslated = mutableMapOf<Int, UExpr<out USort>>()
    private val indexedMethodReturnValueToTranslated = mutableMapOf<Pair<*, Int>, UExpr<*>>()
    private lateinit var nullRef: UExpr<UAddressSort>

    override fun newRegionTranslator(regionId: URegionId<*, *>, translator: URegionTranslator<*, *, *, *>) {
        regionToTranslator[regionId] = translator
    }

    override fun <Sort : USort> newRegisterReadingTranslated(idx: Int, translated: UExpr<Sort>) {
        registerIdxToTranslated[idx] = translated
    }

    override fun <Method, Sort : USort> newIndexedMethodReturnValueTranslated(
        method: Method,
        callIndex: Int,
        translated: UExpr<Sort>,
    ) {
        indexedMethodReturnValueToTranslated[method to callIndex] = translated
    }

    override fun nullRefTranslated(translated: UExpr<UAddressSort>) {
        nullRef = translated
    }

    /**
     * Build a mapping from instances of an uninterpreted [UAddressSort]
     * to [UConcreteHeapRef] with integer addresses. It allows us to enumerate
     * equivalence classes of addresses and work with their number in the future.
     */
    private fun buildMapping(model: KModel): Map<UExpr<UAddressSort>, UConcreteHeapRef> {
        // Null is a special value that we want to translate in any case.
        val interpretedNullRef = model.eval(nullRef, isComplete = true)

        val universe = model.uninterpretedSortUniverse(ctx.addressSort) ?: return emptyMap()
        // All the numbers are enumerated from the INITIAL_INPUT_ADDRESS to the Int.MIN_VALUE
        var counter = INITIAL_INPUT_ADDRESS

        val result = mutableMapOf<KExpr<KUninterpretedSort>, UConcreteHeapRef>()
        // Except the null value, it has the NULL_ADDRESS
        result[ctx.nullRef] = ctx.mkConcreteHeapRef(NULL_ADDRESS)

        for (interpretedAddress in universe) {
            if (universe == interpretedNullRef) {
                continue
            }
            result[interpretedAddress] = ctx.mkConcreteHeapRef(counter--)
        }

        return result
    }

    /**
     * Constructs a [UHeapModel] for a heap by provided [model] and [addressesMapping].
     */
    @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
    private fun decodeHeap(
        model: KModel,
        addressesMapping: Map<UExpr<UAddressSort>, UConcreteHeapRef>,
    ): UHeapModel<Field, Type> {
        var inputFields = persistentMapOf<Field, URegionEvaluator<UHeapRef, out USort>>()
        var inputArrays = persistentMapOf<Type, URegionEvaluator<Pair<UHeapRef, USizeExpr>, out USort>>()
        var inputLengths = persistentMapOf<Type, URegionEvaluator<UHeapRef, USizeSort>>()

        regionToTranslator.forEach { (regionId, translator) ->
            when (regionId) {
                is UInputFieldRegionId<*, *> -> {
                    regionId as? UInputFieldRegionId<Field, *> ?: return@forEach

                    val evaluator = translator.getEvaluator(model, addressesMapping) as URegionEvaluator<UHeapRef, *>
                    inputFields = inputFields.put(regionId.field, evaluator)
                }

                is UInputArrayId<*, *> -> {
                    regionId as? UInputArrayId<Type, *> ?: return@forEach

                    val evaluator = translator.getEvaluator(
                        model,
                        addressesMapping
                    ) as URegionEvaluator<Pair<UHeapRef, USizeExpr>, out USort>
                    inputArrays = inputArrays.put(regionId.arrayType, evaluator)
                }

                is UInputArrayLengthId<*> -> {
                    regionId as? UInputArrayLengthId<Type> ?: return@forEach

                    val evaluator =
                        translator.getEvaluator(model, addressesMapping) as URegionEvaluator<UHeapRef, USizeSort>
                    inputLengths = inputLengths.put(regionId.arrayType, evaluator)
                }
            }
        }

        return UHeapModel(ctx, inputFields, inputArrays, inputLengths, addressesMapping)
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

    private fun decodeStack(model: KModel, mapping: Map<UExpr<UAddressSort>, UConcreteHeapRef>): URegistersStackModel {
        val registers = registerIdxToTranslated.replaceUninterpretedConsts(model, mapping)

        return URegistersStackModel(registers)
    }

    private fun decodeMocker(
        model: KModel,
        mapping: Map<UExpr<UAddressSort>, UConcreteHeapRef>,
    ): UIndexedMockModel<Method> {
        val values = indexedMethodReturnValueToTranslated.replaceUninterpretedConsts(model, mapping)

        return UIndexedMockModel(values)
    }

    /**
     * Since expressions from [this] might have the [UAddressSort] and therefore
     * they could be uninterpreted constants, we have to replace them with
     * corresponding concrete addresses from the [addressesMapping].
     */
    private fun <K> Map<K, UExpr<out USort>>.replaceUninterpretedConsts(
        model: KModel,
        addressesMapping: Map<UExpr<UAddressSort>, UConcreteHeapRef>,
    ): Map<K, UExpr<out USort>> {
        val values = mapValues { (_, expr) ->
            val value = model.eval(expr, isComplete = true)
            val transformedValue = value.mapAddress(addressesMapping)

            transformedValue
        }

        return values
    }

    companion object {
        /**
         * If [this] value is an instance of address expression, returns
         * an expression with a corresponding concrete address, otherwise
         * returns [this] unchanched.
         */
        fun <S : USort> UExpr<S>.mapAddress(
            mapping: Map<UExpr<UAddressSort>, UConcreteHeapRef>,
        ): UExpr<S> = if (sort == uctx.addressSort) {
            mapping.getValue(asExpr(uctx.addressSort)).asExpr(sort)
        } else {
            this
        }
    }
}

class URegistersStackModel(private val registers: Map<Int, UExpr<out USort>>) : URegistersStackEvaluator {
    override fun <Sort : USort> eval(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> = registers.getOrDefault(registerIndex, sort.sampleValue()).asExpr(sort)
}

class UHeapModel<Field, ArrayType>(
    private val ctx: UContext,
    private var resolvedInputFields: PersistentMap<Field, URegionEvaluator<UHeapRef, out USort>>,
    private var resolvedInputArrays: PersistentMap<ArrayType, URegionEvaluator<Pair<UHeapRef, USizeExpr>, out USort>>,
    private var resolvedInputLengths: PersistentMap<ArrayType, URegionEvaluator<UHeapRef, USizeSort>>,
    private val uninterpretedAddressesMapping: Map<UHeapRef, UConcreteHeapRef>,
) : USymbolicHeap<Field, ArrayType> {
    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        return resolvedInputFields[field]?.select(ref)?.asExpr(sort) ?: sort.sampleValue()
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
        require(ref is UConcreteHeapRef && ref.address < INITIAL_INPUT_ADDRESS)

        val key = ref to index
        return resolvedInputArrays[arrayType]?.select(key)?.asExpr(elementSort) ?: elementSort.sampleValue()
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address < INITIAL_INPUT_ADDRESS)

        return resolvedInputLengths[arrayType]?.select(ref) ?: ctx.sizeSort.sampleValue()
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
        require(ref is UConcreteHeapRef && ref.address < INITIAL_INPUT_ADDRESS)

        @Suppress("UNCHECKED_CAST")
        val regionEvaluator = resolvedInputFields.getOrElse(field) {
            val regionId = UInputFieldRegionId(field, sort)
            val result = U1DArrayUpdateTranslator.U1DArrayEvaluator(regionId, uninterpretedAddressesMapping)

            resolvedInputFields = resolvedInputFields.put(field, result)
            result
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
        require(ref is UConcreteHeapRef && ref.address < INITIAL_INPUT_ADDRESS)

        val valueToWrite = value.asExpr(elementSort)

        @Suppress("UNCHECKED_CAST")
        val regionEvaluator = resolvedInputArrays.getOrElse(type) {
            val regionId = UInputArrayId(type, elementSort)
            val result = U2DArrayUpdateTranslator.U2DArrayEvaluator(regionId, uninterpretedAddressesMapping)

            resolvedInputArrays = resolvedInputArrays.put(type, result)
            result
        } as URegionEvaluator<Pair<UHeapRef, USizeExpr>, Sort>

        regionEvaluator.write(ref to index, valueToWrite)
    }

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model known only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(size is KInterpretedValue<USizeSort>)
        require(ref is UConcreteHeapRef && ref.address < INITIAL_INPUT_ADDRESS)

        resolvedInputLengths.getOrElse(arrayType) {
            val regionId = UInputArrayLengthId(arrayType, ctx.sizeSort)
            val result = U1DArrayUpdateTranslator.U1DArrayEvaluator(regionId, uninterpretedAddressesMapping)

            resolvedInputLengths = resolvedInputLengths.put(arrayType, result)
            result
        }.write(ref, size)
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
    ) {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ) {
        TODO("Not yet implemented")
    }

    override fun allocate() = error("Illegal operation for a model")

    override fun allocateArray(count: USizeExpr): UConcreteHeapAddress = error("Illegal operation for a model")

    override fun clone(): UHeap<UHeapRef, UExpr<out USort>, USizeExpr, Field, ArrayType, UBoolExpr> =
        UHeapModel(
            ctx,
            resolvedInputFields,
            resolvedInputArrays,
            resolvedInputLengths,
            uninterpretedAddressesMapping
        )

    override fun toMutableHeap() = clone()
}