package org.usvm.solver

import org.ksmt.expr.KApp
import org.ksmt.expr.KBvSignedLessOrEqualExpr
import org.ksmt.expr.KExpr
import org.ksmt.sort.KArray2Sort
import org.ksmt.sort.KArray3Sort
import org.ksmt.sort.KArrayNSort
import org.ksmt.sort.KArraySort
import org.ksmt.sort.KBoolSort
import org.ksmt.sort.KBvSort
import org.ksmt.sort.KFpRoundingModeSort
import org.ksmt.sort.KFpSort
import org.ksmt.sort.KIntSort
import org.ksmt.sort.KRealSort
import org.ksmt.sort.KSort
import org.ksmt.sort.KSortVisitor
import org.ksmt.sort.KUninterpretedSort
import org.usvm.UAddressSort
import org.usvm.UAllocatedArrayReading
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapReading
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UInputArrayLengthReading
import org.usvm.UInputArrayReading
import org.usvm.UInputFieldReading
import org.usvm.UIsExpr
import org.usvm.UMockSymbol
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.USymbol

class USoftConstraintsProvider<Field, Type>(ctx: UContext) : UExprTransformer<Field, Type>(ctx) {
    private val caches: MutableMap<UExpr<*>, UBoolExpr> = hashMapOf()
    private val sortPreferredValues = SortPreferredValues()

    fun provide(initialExpr: UExpr<*>): UBoolExpr {
        apply(initialExpr)
        return caches.getValue(initialExpr)
    }

    private inline fun <T : USort> computeSideEffect(
        expr: UExpr<T>,
        sideEffectOperation: () -> Unit,
    ): UExpr<T> {
        sideEffectOperation()
        return expr
    }

    // region The most common methods


    override fun <T : KSort> transformExpr(expr: KExpr<T>): KExpr<T> = computeSideEffect(expr) {
        caches[expr] = expr.sort.accept(sortPreferredValues)
    }

    override fun <T : KSort, A : KSort> transformApp(expr: KApp<T, A>): KExpr<T> =
        transformExprAfterTransformed(expr, expr.args) { args ->
            computeSideEffect(expr) {
                val collected = args.fold(expr.ctx.trueExpr as UBoolExpr) { acc, value ->
                    expr.ctx.mkAnd(acc, caches.getValue(value), flat = false)
                }
                val selfConstraint = expr.sort.accept(sortPreferredValues)
                caches[expr] = expr.ctx.mkAnd(selfConstraint, collected, flat = false)
            }
        }

    // endregion

    override fun <T : KBvSort> transform(expr: KBvSignedLessOrEqualExpr<T>): KExpr<KBoolSort> = with(expr.ctx) {
        transformExprAfterTransformed(expr, expr.arg0, expr.arg1) { lhs, rhs ->
            val selfConstraint = mkEq(lhs, rhs)
            val result =
                mkAnd(selfConstraint, mkAnd(caches.getValue(lhs), caches.getValue(rhs), flat = false), flat = false)
            caches[expr] = result
            return result
        }
    }

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> {
        TODO()
    }

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> = with(expr.ctx) {
        caches[expr] = expr.sort.accept(sortPreferredValues)
        expr
    }

    override fun <Sort : USort> transform(expr: UHeapReading<*, *, *>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> transform(expr: UMockSymbol<Sort>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UNullRef): UExpr<UAddressSort> {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UIsExpr<Type>): UBoolExpr {
        TODO("Not yet implemented")
    }

    override fun transform(expr: UInputArrayLengthReading<Type>): USizeExpr {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> {
        TODO("Not yet implemented")
    }


    // region KExpressions


    // endregion
}

private class SortPreferredValues() : KSortVisitor<KExpr<KBoolSort>> {
    override fun <S : KBvSort> visit(sort: S): KExpr<KBoolSort> = with(sort.ctx) {
        TODO()
    }

    override fun <S : KFpSort> visit(sort: S): KExpr<KBoolSort> {
        TODO()
    }

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): KExpr<KBoolSort> {
        return sort.range.accept(this)
    }

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(sort: KArray3Sort<D0, D1, D2, R>): KExpr<KBoolSort> {
        return sort.range.accept(this)
    }

    override fun <R : KSort> visit(sort: KArrayNSort<R>): KExpr<KBoolSort> {
        return sort.range.accept(this)
    }

    override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): KExpr<KBoolSort> {
        TODO("Not yet implemented")
    }

    override fun visit(sort: KBoolSort): KExpr<KBoolSort> {
        TODO("Not yet implemented")
    }

    override fun visit(sort: KFpRoundingModeSort): KExpr<KBoolSort> {
        TODO("Not yet implemented")
    }

    override fun visit(sort: KIntSort): KExpr<KBoolSort> {
        TODO("Not yet implemented")
    }

    override fun visit(sort: KRealSort): KExpr<KBoolSort> {
        TODO("Not yet implemented")
    }

    override fun visit(sort: KUninterpretedSort): KExpr<KBoolSort> {
        TODO("Not yet implemented")
    }
}