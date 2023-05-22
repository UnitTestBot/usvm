package org.usvm.model

import io.ksmt.utils.asExpr
import org.usvm.UArrayIndexRef
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFieldRef
import org.usvm.UHeapRef
import org.usvm.ULValue
import org.usvm.UMockEvaluator
import org.usvm.URegisterRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.constraints.UTypeModel
import org.usvm.memory.UReadOnlySymbolicHeap
import org.usvm.memory.UReadOnlySymbolicMemory

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
open class UModelBase<Field, Type>(
    ctx: UContext,
    val stack: ULazyRegistersStackModel,
    val heap: UReadOnlySymbolicHeap<Field, Type>,
    val types: UTypeModel<Type>,
    val mocks: UMockEvaluator
) : UModel, UReadOnlySymbolicMemory<Type> {
    private val composer = UComposer(ctx, stack, heap, types, mocks)

    /**
     * The evaluator supports only expressions with symbols inheriting [org.usvm.USymbol].
     *
     * For instance, raw [io.ksmt.expr.KConst] cannot occur in the [expr], only as a result of
     * a reading of some sort, a mock symbol, etc.
     */
    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)

    @Suppress("UNCHECKED_CAST")
    override fun read(lvalue: ULValue): UExpr<out USort> = with(lvalue) {
        when (this) {
            is URegisterRef -> stack.readRegister(idx, sort)
            is UFieldRef<*> -> heap.readField(ref, field as Field, sort).asExpr(sort)
            is UArrayIndexRef<*> -> heap.readArrayIndex(ref, index, arrayType as Type, sort).asExpr(sort)

            else -> throw IllegalArgumentException("Unexpected lvalue $this")
        }
    }

    override fun length(ref: UHeapRef, arrayType: Type): USizeExpr =
        heap.readArrayLength(ref, arrayType)

    override fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> =
        eval(expr)
}