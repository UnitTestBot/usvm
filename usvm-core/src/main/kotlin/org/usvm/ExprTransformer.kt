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
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.regions.Region

interface UTransformer<Type, USizeSort : USort> : KTransformer {
    fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort>

    fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort, USizeSort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort, USizeSort>): UExpr<Sort>

    fun transform(expr: UInputArrayLengthReading<Type, USizeSort>): UExpr<USizeSort>

    fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort>

    fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ): UExpr<Sort>

    fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<Type, Sort>): UExpr<Sort>

    fun transform(expr: UInputMapLengthReading<Type, USizeSort>): UExpr<USizeSort>

    fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UAllocatedSetReading<Type, ElemSort, Reg>): UBoolExpr

    fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UInputSetReading<Type, ElemSort, Reg>): UBoolExpr

    fun transform(expr: UAllocatedRefSetWithInputElementsReading<Type>): UBoolExpr

    fun transform(expr: UInputRefSetWithAllocatedElementsReading<Type>): UBoolExpr

    fun transform(expr: UInputRefSetWithInputElementsReading<Type>): UBoolExpr

    fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort>

    fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>) : UExpr<Sort>

    fun transform(expr: UIsSubtypeExpr<Type>): UBoolExpr

    fun transform(expr: UIsSupertypeExpr<Type>): UBoolExpr

    fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort>

    fun transform(expr: UNullRef): UExpr<UAddressSort>

//    fun <Key, Sort : USort> transform(expr: UPointer<Key, Sort>): UExpr<UPointerSort>
    fun transform(expr: UPointer): UExpr<UPointerSort>
}

abstract class UExprTransformer<Type, USizeSort : USort>(
    ctx: UContext<USizeSort>
) : KNonRecursiveTransformer(ctx), UTransformer<Type, USizeSort>

@Suppress("UNCHECKED_CAST")
fun <Type, USizeSort : USort> UTransformer<*, *>.asTypedTransformer(): UTransformer<Type, USizeSort> =
    this as UTransformer<Type, USizeSort>

@Suppress("NOTHING_TO_INLINE")
inline fun <T : USort> UTransformer<*, *>?.apply(expr: UExpr<T>) = this?.apply(expr) ?: expr
