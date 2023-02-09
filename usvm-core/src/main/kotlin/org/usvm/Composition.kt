package org.usvm

import org.ksmt.utils.cast

@Suppress("MemberVisibilityCanBePrivate")
open class UComposer<Field, Type>(
    ctx: UContext,
    internal val stackEvaluator: URegistersStackEvaluator,
    internal val heapEvaluator: UReadOnlySymbolicHeap<Field, Type>,
    internal val typeEvaluator: UTypeEvaluator<Type>,
    internal val mockEvaluator: UMockEvaluator
) : UExprTransformer<Field, Type>(ctx) {
    open fun <Sort : USort> compose(expr: UExpr<Sort>): UExpr<Sort> = apply(expr)

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(
        expr: URegisterReading<Sort>
    ): UExpr<Sort> = with(expr) { stackEvaluator.eval(idx, sort) }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in org.usvm.UComposer for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>
    ): UExpr<Sort> = mockEvaluator.eval(expr)

    override fun transform(expr: UIsExpr<Type>): UBoolExpr = with(expr) {
        val composedAddress = compose(ref)
        typeEvaluator.evalIs(composedAddress, type)
    }

    override fun transform(expr: UArrayLength<Type>): USizeExpr = with(expr) {
        val composedAddress = compose(address)
        heapEvaluator.readArrayLength(composedAddress, arrayType)
    }

    override fun <Sort : USort> transform(
        expr: UInputArrayReading<Type, Sort>
    ): UExpr<Sort> = with(expr) {
        val composedAddress = compose(address)
        val composedIndex = compose(index)
        // TODO compose the region

        heapEvaluator.readArrayIndex(
            composedAddress,
            composedIndex,
            arrayType,
            elementSort
        ).cast()
    }

    override fun <Sort : USort> transform(
        expr: UAllocatedArrayReading<Type, Sort>
    ): UExpr<Sort> = with(expr) {
        val composedIndex = compose(index)
        // TODO compose the region
        val heapRef = uctx.mkConcreteHeapRef(address)

        heapEvaluator.readArrayIndex(heapRef, composedIndex, arrayType, elementSort).cast()
    }

    override fun <Sort : USort> transform(expr: UFieldReading<Field, Sort>): UExpr<Sort> = with(expr) {
        val composedAddress = compose(address)
        // TODO compose the region
        heapEvaluator.readField(composedAddress, field, sort).cast()
    }

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> = expr
}
