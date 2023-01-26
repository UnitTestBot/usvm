package org.usvm

interface UModel {
    fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

open class UModelBase<Field, Type>(
    private val ctx: UContext,
    val stack: UStackModel,
    val heap: UReadOnlySymbolicHeap<Field, Type>,
    val types: UTypeModel<Type>,
    val mocks: UMockEvaluator
)
    : UModel
{
    override fun <Sort: USort> eval(expr: UExpr<Sort>): UExpr<Sort> {
        val composer = UComposer(ctx, stack, heap, types, mocks)
        return composer.compose(expr)
    }
}