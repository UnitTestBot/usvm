package org.usvm

interface UModel {
    fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

open class UModelBase<Field, Type>(
    ctx: UContext,
    stack: URegistersStackModel,
    heap: UReadOnlySymbolicHeap<Field, Type>,
    types: UTypeModel<Type>,
    mocks: UMockEvaluator
) : UModel {
    private val composer = UComposer(ctx, stack, heap, types, mocks)

    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)
}