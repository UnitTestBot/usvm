package org.usvm

import org.ksmt.expr.transformer.KNonRecursiveTransformer

abstract class UExprTransformer<Field, Type>(ctx: UContext): KNonRecursiveTransformer(ctx) {
    abstract fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort>

    abstract fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort>

    abstract fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort>
    abstract fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort>
    abstract fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort>
    abstract fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort>
    abstract fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr

    abstract fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort>
    abstract fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort>

    abstract fun transform(expr: UIsExpr<Type>): UBoolExpr

    abstract fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort>

    abstract fun transform(expr: UNullRef): UExpr<UAddressSort>
}