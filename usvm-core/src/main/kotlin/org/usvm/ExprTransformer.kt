package org.usvm

import io.ksmt.expr.transformer.KNonRecursiveTransformer
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.expr.transformer.KTransformer
import io.ksmt.sort.KFpSort
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
import org.usvm.collection.string.UCharAtExpr
import org.usvm.collection.string.UCharExpr
import org.usvm.collection.string.UCharToLowerExpr
import org.usvm.collection.string.UCharToUpperExpr
import org.usvm.collection.string.UConcreteStringHashCodeBv32Expr
import org.usvm.collection.string.UConcreteStringHashCodeIntExpr
import org.usvm.collection.string.UFloatFromStringExpr
import org.usvm.collection.string.UIntFromStringExpr
import org.usvm.collection.string.URegexMatchesExpr
import org.usvm.collection.string.URegexReplaceAllExpr
import org.usvm.collection.string.URegexReplaceFirstExpr
import org.usvm.collection.string.UStringConcatExpr
//import org.usvm.collection.string.UStringEqExpr
import org.usvm.collection.string.UStringExpr
import org.usvm.collection.string.UStringFromArrayExpr
import org.usvm.collection.string.UStringFromFloatExpr
import org.usvm.collection.string.UStringFromIntExpr
import org.usvm.collection.string.UStringFromLanguageExpr
import org.usvm.collection.string.UStringHashCodeExpr
import org.usvm.collection.string.UStringIndexOfExpr
import org.usvm.collection.string.UStringLeExpr
import org.usvm.collection.string.UStringLengthExpr
import org.usvm.collection.string.UStringLiteralExpr
import org.usvm.collection.string.UStringLtExpr
import org.usvm.collection.string.UStringRepeatExpr
import org.usvm.collection.string.UStringReplaceAllExpr
import org.usvm.collection.string.UStringReplaceFirstExpr
import org.usvm.collection.string.UStringReverseExpr
import org.usvm.collection.string.UStringSliceExpr
import org.usvm.collection.string.UStringToLowerExpr
import org.usvm.collection.string.UStringToUpperExpr
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

    // String expressions

    fun transform(expr: UStringLiteralExpr): UStringExpr

    fun transform(expr: UStringFromArrayExpr<Type, USizeSort>): UStringExpr

    fun transform(expr: UStringFromLanguageExpr): UStringExpr

    fun transform(expr: UStringConcatExpr): UStringExpr

    fun transform(expr: UStringLengthExpr<USizeSort>): UExpr<USizeSort>

    fun transform(expr: UCharAtExpr<USizeSort>): UCharExpr

    fun transform(expr: UStringHashCodeExpr<USizeSort>): UExpr<USizeSort>

    fun transform(expr: UConcreteStringHashCodeBv32Expr): UExpr<USizeSort>

    fun transform(expr: UConcreteStringHashCodeIntExpr): UExpr<USizeSort>

    fun transform(expr: UStringLtExpr): UBoolExpr

    fun transform(expr: UStringLeExpr): UBoolExpr

    fun transform(expr: UStringSliceExpr<USizeSort>): UStringExpr

    fun transform(expr: UStringFromIntExpr<USizeSort>): UStringExpr

    fun transform(expr: UIntFromStringExpr<USizeSort>): UExpr<USizeSort>

    fun <UFloatSort: KFpSort> transform(expr: UStringFromFloatExpr<UFloatSort>): UStringExpr

    fun <UFloatSort: KFpSort> transform(expr: UFloatFromStringExpr<UFloatSort>): UExpr<UFloatSort>

    fun transform(expr: UStringRepeatExpr<USizeSort>): UStringExpr

    fun transform(expr: UStringToUpperExpr): UStringExpr

    fun transform(expr: UStringToLowerExpr): UStringExpr

    fun transform(expr: UCharToUpperExpr): UCharExpr

    fun transform(expr: UCharToLowerExpr): UCharExpr

    fun transform(expr: UStringReverseExpr): UStringExpr

    fun transform(expr: UStringIndexOfExpr<USizeSort>): UExpr<USizeSort>

    fun transform(expr: URegexMatchesExpr): UBoolExpr

    fun transform(expr: UStringReplaceFirstExpr): UStringExpr

    fun transform(expr: UStringReplaceAllExpr): UStringExpr

    fun transform(expr: URegexReplaceFirstExpr): UStringExpr

    fun transform(expr: URegexReplaceAllExpr): UStringExpr
}

abstract class UExprTransformer<Type, USizeSort : USort>(
    ctx: UContext<USizeSort>
) : KNonRecursiveTransformer(ctx), UTransformer<Type, USizeSort>

abstract class UExprVisitor<Type, USizeSort : USort, V: Any>(
    ctx: UContext<USizeSort>
) : KNonRecursiveVisitor<V>(ctx), UTransformer<Type, USizeSort>

@Suppress("UNCHECKED_CAST")
fun <Type, USizeSort : USort> UTransformer<*, *>.asTypedTransformer(): UTransformer<Type, USizeSort> =
    this as UTransformer<Type, USizeSort>

@Suppress("UNCHECKED_CAST")
fun <USizeSort : USort> UTransformer<*, *>.asSizeTypedTransformer(): UTransformer<*, USizeSort> =
    this as UTransformer<*, USizeSort>

@Suppress("NOTHING_TO_INLINE")
inline fun <T : USort> UTransformer<*, *>?.apply(expr: UExpr<T>) = this?.apply(expr) ?: expr
