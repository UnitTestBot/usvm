package org.usvm.model

import io.ksmt.solver.KModel
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapReading
import org.usvm.UHeapRef
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.memory.UInputArrayId
import org.usvm.memory.UInputArrayLengthId
import org.usvm.memory.UInputFieldId
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.URegionId
import org.usvm.memory.URegistersStackEvaluator
import org.usvm.memory.USymbolicArrayIndex
import org.usvm.memory.USymbolicHeap
import org.usvm.solver.UExprTranslator
import org.usvm.uctx


/**
 * A lazy model for registers. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides translated symbolic constants for registers readings.
 */
class ULazyRegistersStackModel(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<*, *>,
) : URegistersStackEvaluator {
    private val uctx = translator.ctx

    override fun <Sort : USort> readRegister(
        registerIndex: Int,
        sort: Sort,
    ): UExpr<Sort> {
        val registerReading = uctx.mkRegisterReading(registerIndex, sort)
        val translated = translator.translate(registerReading)
        return model.eval(translated, isComplete = true).mapAddress(addressesMapping)
    }
}

/**
 * A lazy model for an indexed mocker. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides translated symbolic constants for mock symbols.
 */
class ULazyIndexedMockModel(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<*, *>,
) : UMockEvaluator {
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)
        val translated = translator.translate(symbol)
        return model.eval(translated, isComplete = true).mapAddress(addressesMapping)
    }
}

/**
 *
 * A lazy immutable heap model. Firstly, searches for decoded [UMemoryRegion], decodes it from [model] if not found,
 * secondly, evaluates a value from it.
 *
 * Declared as mutable heap for using in regions composition in [UComposer]. Any call to
 * modifying operation throws an exception.
 *
 * Any [UHeapReading] possibly writing to this heap in its [URegionId.instantiate] call actually has empty updates,
 * because localization happened, so this heap won't be mutated.
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides initial symbolic values by [URegionId]s.
 */
class ULazyHeapModel<Field, ArrayType>(
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<Field, ArrayType>,
) : USymbolicHeap<Field, ArrayType> {
    private val resolvedInputFields = mutableMapOf<Field, UReadOnlyMemoryRegion<UHeapRef, out USort>>()
    private val resolvedInputArrays = mutableMapOf<ArrayType, UReadOnlyMemoryRegion<USymbolicArrayIndex, out USort>>()
    private val resolvedInputLengths = mutableMapOf<ArrayType, UReadOnlyMemoryRegion<UHeapRef, USizeSort>>()

    private val nullRef = model
        .eval(translator.translate(translator.ctx.nullRef))
        .mapAddress(addressesMapping) as UConcreteHeapRef

    override fun <Sort : USort> readField(ref: UHeapRef, field: Field, sort: Sort): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val resolvedRegion = resolvedInputFields[field]
        val regionId = UInputFieldId(field, sort, contextHeap = null)
        val initialValue = translator.translateInputFieldId(regionId)

        return when {
            resolvedRegion != null -> resolvedRegion.read(ref).asExpr(sort)
            else -> {
                val region = UMemory1DArray(initialValue, model, addressesMapping)
                resolvedInputFields[field] = region
                region.read(ref)
            }
        }
    }

    override fun <Sort : USort> readArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        arrayType: ArrayType,
        sort: Sort,
    ): UExpr<Sort> {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val key = ref to index

        val resolvedRegion = resolvedInputArrays[arrayType]
        val regionId = UInputArrayId(arrayType, sort, contextHeap = null)
        val initialValue = translator.translateInputArrayId(regionId)

        return when {
            resolvedRegion != null -> resolvedRegion.read(key).asExpr(sort)
            else -> {
                val region = UMemory2DArray(initialValue, model, addressesMapping)
                resolvedInputArrays[arrayType] = region
                region.read(key)
            }
        }
    }

    override fun readArrayLength(ref: UHeapRef, arrayType: ArrayType): USizeExpr {
        // All the expressions in the model are interpreted, therefore, they must
        // have concrete addresses. Moreover, the model knows only about input values
        // which have addresses less or equal than INITIAL_INPUT_ADDRESS
        require(ref is UConcreteHeapRef && ref.address <= INITIAL_INPUT_ADDRESS)

        val resolvedRegion = resolvedInputLengths[arrayType]
        val regionId = UInputArrayLengthId(arrayType, ref.uctx.sizeSort, contextHeap = null)
        val initialValue = translator.translateInputArrayLengthId(regionId)

        return when {
            resolvedRegion != null -> resolvedRegion.read(ref)
            else -> {
                val region = UMemory1DArray(initialValue.cast(), model, addressesMapping)
                resolvedInputLengths[arrayType] = region
                region.read(ref)
            }
        }
    }

    override fun <Sort : USort> writeField(
        ref: UHeapRef,
        field: Field,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) = error("Illegal operation for a model heap")

    override fun <Sort : USort> writeArrayIndex(
        ref: UHeapRef,
        index: USizeExpr,
        type: ArrayType,
        sort: Sort,
        value: UExpr<out USort>,
        guard: UBoolExpr,
    ) = error("Illegal operation for a model heap")

    override fun writeArrayLength(ref: UHeapRef, size: USizeExpr, arrayType: ArrayType) =
        error("Illegal operation for a model heap")

    override fun <Sort : USort> memcpy(
        srcRef: UHeapRef,
        dstRef: UHeapRef,
        type: ArrayType,
        elementSort: Sort,
        fromSrcIdx: USizeExpr,
        fromDstIdx: USizeExpr,
        toDstIdx: USizeExpr,
        guard: UBoolExpr,
    ) = error("Illegal operation for a model heap")

    override fun <Sort : USort> memset(
        ref: UHeapRef,
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ) = error("Illegal operation for a model")

    override fun allocate() = error("Illegal operation for a model heap")

    override fun allocateArray(count: USizeExpr) = error("Illegal operation for a model heap")

    override fun <Sort : USort> allocateArrayInitialized(
        type: ArrayType,
        sort: Sort,
        contents: Sequence<UExpr<out USort>>,
    ) = error("Illegal operation for a model heap")

    override fun nullRef(): UConcreteHeapRef = nullRef

    override fun toMutableHeap(): ULazyHeapModel<Field, ArrayType> = this
}

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