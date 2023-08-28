package org.usvm

import io.ksmt.expr.transformer.KNonRecursiveTransformer
import io.ksmt.expr.transformer.KTransformer
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.util.Region

interface UTransformer<Type> : KTransformer {
    fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UCollectionReading<*, *, *>): UExpr<Sort>

    fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort>

    fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr

    fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort>

    fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort>

    fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<Type, Sort>): UExpr<Sort>

    fun transform(expr: UInputMapLengthReading<Type>): USizeExpr

    fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort>

    fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort>

    fun transform(expr: UIsSubtypeExpr<Type>): UBoolExpr

    fun transform(expr: UIsSupertypeExpr<Type>): UBoolExpr

    fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort>

    fun transform(expr: UNullRef): UExpr<UAddressSort>
}

abstract class UExprTransformer<Type>(
    ctx: UContext
) : KNonRecursiveTransformer(ctx), UTransformer<Type>

@Suppress("UNCHECKED_CAST")
fun <Type> UTransformer<*>.asTypedTransformer() = this as UTransformer<Type>
