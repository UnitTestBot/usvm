package org.usvm.model

import io.ksmt.utils.uncheckedCast
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.memory.URegisterStackId
import org.usvm.memory.UWritableMemory

interface UModel {
    fun <Sort : USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

/**
 * Consists of decoded components and allows to evaluate any expression. Evaluation is done via generic composition.
 * Evaluated expressions are cached within [UModelBase] instance.
 * If a symbol from an expression not found inside the model, components return the default value
 * of the correct sort.
 */
open class UModelBase<Type>(
    ctx: UContext<*>,
    override val stack: UReadOnlyRegistersStack,
    override val types: UTypeModel<Type>,
    override val mocker: UMockEvaluator,
    val regions: Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>,
    val nullRef: UConcreteHeapRef,
    override val ownership: MutabilityOwnership = MutabilityOwnership(),
) : UModel, UWritableMemory<Type> {
    @Suppress("LeakingThis")
    protected open val composer = ctx.composer(this, ownership)

    /**
     * The evaluator supports only expressions with symbols inheriting [org.usvm.USymbol].
     *
     * For instance, raw [io.ksmt.expr.KConst] cannot occur in the [expr], only as a result of
     * a reading of some sort, a mock symbol, etc.
     */
    override fun <Sort : USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)

    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        if (regionId is URegisterStackId) {
            return stack.uncheckedCast()
        }

        return regions[regionId]?.uncheckedCast()
            ?: error("Model has no region: $regionId")
    }

    override fun nullRef(): UHeapRef = nullRef

    override fun toWritableMemory(ownership: MutabilityOwnership): UWritableMemory<Type> = this

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        error("Illegal operation for a model")
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) {
        error("Illegal operation for a model")
    }

    override fun allocConcrete(type: Type): UConcreteHeapRef {
        error("Illegal operation for a model")
    }

    override fun allocStatic(type: Type): UConcreteHeapRef {
        error("Illegal operation for a model")
    }
}

fun modelEnsureConcreteInputRef(ref: UHeapRef): UConcreteHeapRef {
    // All the expressions in the model are interpreted, therefore, they must
    // have concrete addresses. Moreover, the model knows only about input values
    // which have addresses less or equal than INITIAL_INPUT_ADDRESS (or NULL_ADDRESS for null values)
    require(ref is UConcreteHeapRef && (ref.address <= INITIAL_INPUT_ADDRESS || ref.address == NULL_ADDRESS)) {
        "Unexpected ref: $ref"
    }
    return ref
}
