package org.usvm

import io.ksmt.expr.transformer.KExprVisitResult
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.expr.transformer.visitExpr
import io.ksmt.sort.KFpSort
import io.ksmt.utils.uncheckedCast
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

abstract class UExprVisitor<T : Any, Type, USizeSort : USort>(
    ctx: UContext<*>
) : KNonRecursiveVisitor<T>(ctx), UTransformer<Type, USizeSort> {
    abstract fun <Sort : USort> visit(expr: URegisterReading<Sort>): KExprVisitResult<T>
    abstract fun <Field, Sort : USort> visit(expr: UInputFieldReading<Field, Sort>): KExprVisitResult<T>
    abstract fun <Sort : USort> visit(expr: UAllocatedArrayReading<Type, Sort, USizeSort>): KExprVisitResult<T>
    abstract fun <Sort : USort> visit(expr: UInputArrayReading<Type, Sort, USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UInputArrayLengthReading<Type, USizeSort>): KExprVisitResult<T>
    abstract fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> visit(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ): KExprVisitResult<T>

    abstract fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> visit(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ): KExprVisitResult<T>

    abstract fun <Sort : USort> visit(expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>): KExprVisitResult<T>
    abstract fun <Sort : USort> visit(expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>): KExprVisitResult<T>
    abstract fun <Sort : USort> visit(expr: UInputRefMapWithInputKeysReading<Type, Sort>): KExprVisitResult<T>
    abstract fun visit(expr: UInputMapLengthReading<Type, USizeSort>): KExprVisitResult<T>
    abstract fun <ElemSort : USort, Reg : Region<Reg>> visit(expr: UAllocatedSetReading<Type, ElemSort, Reg>): KExprVisitResult<T>

    abstract fun <ElemSort : USort, Reg : Region<Reg>> visit(expr: UInputSetReading<Type, ElemSort, Reg>): KExprVisitResult<T>
    abstract fun visit(expr: UAllocatedRefSetWithInputElementsReading<Type>): KExprVisitResult<T>
    abstract fun visit(expr: UInputRefSetWithAllocatedElementsReading<Type>): KExprVisitResult<T>
    abstract fun visit(expr: UInputRefSetWithInputElementsReading<Type>): KExprVisitResult<T>
    abstract fun <Method, Sort : USort> visit(expr: UIndexedMethodReturnValue<Method, Sort>): KExprVisitResult<T>
    abstract fun <Sort : USort> visit(expr: UTrackedSymbol<Sort>): KExprVisitResult<T>
    abstract fun visit(expr: UIsSubtypeExpr<Type>): KExprVisitResult<T>
    abstract fun visit(expr: UIsSupertypeExpr<Type>): KExprVisitResult<T>
    abstract fun visit(expr: UConcreteHeapRef): KExprVisitResult<T>
    abstract fun visit(expr: UNullRef): KExprVisitResult<T>

    abstract fun visit(expr: UStringLengthExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UCharAtExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringHashCodeExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UConcreteStringHashCodeBv32Expr): KExprVisitResult<T>
    abstract fun visit(expr: UConcreteStringHashCodeIntExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringLtExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringLeExpr): KExprVisitResult<T>
    abstract fun visit(expr: UIntFromStringExpr<USizeSort>): KExprVisitResult<T>
    abstract fun <UFloatSort : KFpSort> visit(expr: UFloatFromStringExpr<UFloatSort>): KExprVisitResult<T>
    abstract fun visit(expr: UCharToUpperExpr): KExprVisitResult<T>
    abstract fun visit(expr: UCharToLowerExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringIndexOfExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: URegexMatchesExpr): KExprVisitResult<T>

    abstract fun visit(expr: UStringExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringLiteralExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringFromArrayExpr<Type, USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringFromLanguageExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringConcatExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringSliceExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringFromIntExpr<USizeSort>): KExprVisitResult<T>
    abstract fun <UFloatSort : KFpSort> visit(expr: UStringFromFloatExpr<UFloatSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringRepeatExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringToUpperExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringToLowerExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringReverseExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringReplaceFirstExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringReplaceAllExpr): KExprVisitResult<T>
    abstract fun visit(expr: URegexReplaceFirstExpr): KExprVisitResult<T>
    abstract fun visit(expr: URegexReplaceAllExpr): KExprVisitResult<T>

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>) = visitExpr(expr, ::visit)
    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>) = visitExpr(expr, ::visit)
    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort, USizeSort>) = visitExpr(expr, ::visit)
    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort, USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UInputArrayLengthReading<Type, USizeSort>) = visitExpr(expr, ::visit)
    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ) = visitExpr(expr, ::visit)

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ) = visitExpr(expr, ::visit)

    override fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>) = visitExpr(expr, ::visit)
    override fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>) = visitExpr(expr, ::visit)
    override fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<Type, Sort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UInputMapLengthReading<Type, USizeSort>) = visitExpr(expr, ::visit)
    override fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UAllocatedSetReading<Type, ElemSort, Reg>) =
        visitExpr(expr, ::visit)

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UInputSetReading<Type, ElemSort, Reg>) = visitExpr(expr, ::visit)
    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<Type>) = visitExpr(expr, ::visit)
    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<Type>) = visitExpr(expr, ::visit)
    override fun transform(expr: UInputRefSetWithInputElementsReading<Type>) = visitExpr(expr, ::visit)
    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>) = visitExpr(expr, ::visit)
    override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UIsSubtypeExpr<Type>) = visitExpr(expr, ::visit)
    override fun transform(expr: UIsSupertypeExpr<Type>) = visitExpr(expr, ::visit)
    override fun transform(expr: UConcreteHeapRef) = visitExpr(expr, ::visit)
    override fun transform(expr: UNullRef) = visitExpr(expr, ::visit)

    override fun transform(expr: UStringLengthExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UCharAtExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringHashCodeExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UConcreteStringHashCodeBv32Expr): UExpr<USizeSort> =
        visitExpr(expr, ::visit).uncheckedCast()
    override fun transform(expr: UConcreteStringHashCodeIntExpr): UExpr<USizeSort> =
        visitExpr(expr, ::visit).uncheckedCast()
    override fun transform(expr: UStringLtExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringLeExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UIntFromStringExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun <UFloatSort : KFpSort> transform(expr: UFloatFromStringExpr<UFloatSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UCharToUpperExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UCharToLowerExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringIndexOfExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: URegexMatchesExpr) = visitExpr(expr, ::visit)

    override fun transform(expr: UStringLiteralExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringFromArrayExpr<Type, USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringFromLanguageExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringConcatExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringSliceExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringFromIntExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun <UFloatSort : KFpSort> transform(expr: UStringFromFloatExpr<UFloatSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringRepeatExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringToUpperExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringToLowerExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringReverseExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringReplaceFirstExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringReplaceAllExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: URegexReplaceFirstExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: URegexReplaceAllExpr) = visitExpr(expr, ::visit)
}