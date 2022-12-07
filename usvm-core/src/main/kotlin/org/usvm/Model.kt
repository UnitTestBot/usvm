package org.usvm

interface UModel {
    fun eval(expr: UExpr): UExpr
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
    override fun eval(expr: UExpr): UExpr {
        val composer = UComposer(ctx, stack, heap, types, mocks)
        return composer.compose(expr)
    }
}