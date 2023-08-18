package org.usvm.model

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.constraints.UTypeModel
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.UWritableMemory

interface UModel {
    fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

/**
 * Consists of decoded components and allows to evaluate any expression. Evaluation is done via generic composition.
 * Evaluated expressions are cached within [UModelBase] instance.
 * If a symbol from an expression not found inside the model, components return the default value
 * of the correct sort.
 */
open class UModelBase<Type>(
    ctx: UContext,
    override val stack: ULazyRegistersStackModel,
    override val types: UTypeModel<Type>,
    override val mocker: UMockEvaluator,
//    val heap: UReadOnlySymbolicHeap<Field, Type>,
    private val nullRef: UConcreteHeapRef,
) : UModel, UWritableMemory<Type> {
    private val composer = UComposer(ctx, this)

    /**
     * The evaluator supports only expressions with symbols inheriting [org.usvm.USymbol].
     *
     * For instance, raw [io.ksmt.expr.KConst] cannot occur in the [expr], only as a result of
     * a reading of some sort, a mock symbol, etc.
     */
    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)

    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        TODO("Not yet implemented")
    }

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        TODO("Not yet implemented")
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) {
        TODO("Not yet implemented")
    }

    override fun nullRef(): UHeapRef =
        nullRef

    override fun toWritableMemory(): UWritableMemory<Type> =
        this
}