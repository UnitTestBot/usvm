package org.usvm.model

import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.UTypeModel
import org.usvm.memory.UReadOnlySymbolicHeap

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
) : UModel {
    private val composer = UComposer(ctx, stack, heap, types, mocks)

    /**
     * Note that it is mandatory to pass only UExpr (??? requirements are different in fact)
     */
    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)
}