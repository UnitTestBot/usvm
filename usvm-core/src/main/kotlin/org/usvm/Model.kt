package org.usvm

interface UModel {
    fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

open class UModelBase<Field, Type>(
    ctx: UContext,
    val stack: URegistersStackModel,
    val heap: UReadOnlySymbolicHeap<Field, Type>,
    val types: UTypeModel<Type>,
    val mocks: UMockEvaluator
) : UModel {
    private val composer = UComposer(ctx, stack, heap, types, mocks)

    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)
}