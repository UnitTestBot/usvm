package org.usvm

import io.ksmt.expr.transformer.KNonRecursiveTransformer
import io.ksmt.expr.transformer.KTransformer

interface UTransformer<Field, Type> : KTransformer {
    fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort>
    fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort>
    fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort>
    fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort>
    fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr

    fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort>
    fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort>

    fun transform(expr: UIsExpr<Type>): UBoolExpr

    fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort>

    fun transform(expr: UNullRef): UExpr<UAddressSort>
}

abstract class UExprTransformer<Field, Type>(
    ctx: UContext
) : KNonRecursiveTransformer(ctx), UTransformer<Field, Type>
