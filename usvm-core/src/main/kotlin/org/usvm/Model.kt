package org.usvm

import org.ksmt.expr.rewrite.KExprSubstitutor

interface UModel {
    fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

open class UModelBase<Field, Type>(
    private val ctx: UContext,
    val stack: URegistersStackModel,
    val heap: UReadOnlySymbolicHeap<Field, Type>,
    val types: UTypeModel<Type>,
    val mocks: UMockEvaluator
) : UModel {
    private val composer = UComposer(ctx, stack, heap, types, mocks)

    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)
}