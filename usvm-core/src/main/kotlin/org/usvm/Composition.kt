package org.usvm

open class UComposer<Field, Type>(
    private val ctx: UContext,
    val stackEvaluator: UStackEvaluator,
    val heapEvaluator: UReadOnlySymbolicHeap<Field, Type>,
    val typeEvaluator: UTypeEvaluator<Type>,
    val mockEvaluator: UMockEvaluator
    )
// TODO: Inherit UExprVisitor, call evaluator for every USymbol, for all other just use KSMT substitution
{
    open fun <Sort: USort> compose(expr: UExpr<Sort>): UExpr<Sort> =
        TODO()

}
