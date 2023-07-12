package org.usvm.solver

import io.ksmt.expr.KApp
import io.ksmt.expr.KBvSignedLessOrEqualExpr
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFpRoundingMode
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArray3Sort
import io.ksmt.sort.KArrayNSort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBv1Sort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFp32Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KFpRoundingModeSort
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.utils.asExpr
import org.usvm.UAddressSort
import org.usvm.UAllocatedArrayReading
import org.usvm.UAllocatedSymbolicMapReading
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapReading
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UInputArrayLengthReading
import org.usvm.UInputArrayReading
import org.usvm.UInputFieldReading
import org.usvm.UInputSymbolicMapLengthReading
import org.usvm.UInputSymbolicMapReading
import org.usvm.UIsExpr
import org.usvm.UMockSymbol
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.USymbol
import org.usvm.uctx
import org.usvm.util.Region

class USoftConstraintsProvider<Field, Type>(ctx: UContext) : UExprTransformer<Field, Type>(ctx) {
    // We have a list here since sometimes we want to add several soft constraints
    // to make it possible to drop only a part of them, not the whole soft constraint
    private val caches = hashMapOf<UExpr<*>, Set<UBoolExpr>>().withDefault { emptySet() }
    private val sortPreferredValuesProvider = SortPreferredValuesProvider()

    fun provide(initialExpr: UExpr<*>): Set<UBoolExpr> {
        apply(initialExpr)
        return caches.getValue(initialExpr)
    }

    // region The most common methods

    override fun <T : KSort> transformExpr(expr: KExpr<T>): KExpr<T> = computeSideEffect(expr) {
        caches[expr] = setOf(expr.sort.accept(sortPreferredValuesProvider)(expr))
    }

    override fun <T : KSort, A : KSort> transformApp(expr: KApp<T, A>): KExpr<T> =
        transformExprAfterTransformed(expr, expr.args) { args ->
            computeSideEffect(expr) {
                val nestedConstraints = args.flatMapTo(mutableSetOf()) { caches.getValue(it) }
                val selfConstraint = expr.sort.accept(sortPreferredValuesProvider)(expr)

                caches[expr] = nestedConstraints + selfConstraint
            }
        }

    private fun <Sort : USort> transformAppIfPossible(expr: UExpr<Sort>): UExpr<Sort> =
        if (expr is KApp<Sort, *>) transformApp(expr) else transformExpr(expr)

    // endregion

    // region USymbol specific methods

    override fun <Sort : USort> transform(expr: USymbol<Sort>): UExpr<Sort> =
        error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> = transformExpr(expr)

    override fun <Sort : USort> transform(
        expr: UHeapReading<*, *, *>,
    ): UExpr<Sort> = error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Sort : USort> transform(
        expr: UMockSymbol<Sort>,
    ): UExpr<Sort> = error("You must override `transform` function in UExprTranslator for ${expr::class}")

    override fun <Method, Sort : USort> transform(
        expr: UIndexedMethodReturnValue<Method, Sort>,
    ): UExpr<Sort> = transformAppIfPossible(expr)

    override fun transform(
        expr: UConcreteHeapRef,
    ): UExpr<UAddressSort> = error("Illegal operation since UConcreteHeapRef must not be translated into a solver")

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = expr

    override fun transform(expr: UIsExpr<Type>): UBoolExpr =
        error("Illegal operation since UIsExpr should not be translated into a SMT solver")

    override fun transform(
        expr: UInputArrayLengthReading<Type>,
    ): USizeExpr = transformExprAfterTransformed(expr, expr.address) {
        computeSideEffect(expr) {
            with(expr.ctx) {
                val addressIsNull = caches.getValue(expr.address)
                val arraySize = mkBvSignedLessOrEqualExpr(expr, PREFERRED_MAX_ARRAY_SIZE.toBv())

                caches[expr] = addressIsNull + arraySize
            }
        }
    }

    override fun <Sort : USort> transform(
        expr: UInputArrayReading<Type, Sort>,
    ): UExpr<Sort> = readingWithTwoArgumentsTransform(expr, expr.index, expr.address)

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> transform(
        expr: UInputSymbolicMapReading<KeySort, Reg, Sort>
    ): UExpr<Sort> = readingWithTwoArgumentsTransform(expr, expr.key, expr.address)

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort> =
        readingWithSingleArgumentTransform(expr, expr.index)

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        readingWithSingleArgumentTransform(expr, expr.address)

    override fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> transform(
        expr: UAllocatedSymbolicMapReading<KeySort, Reg, Sort>
    ): UExpr<Sort> = readingWithSingleArgumentTransform(expr, expr.key)

    override fun transform(
        expr: UInputSymbolicMapLengthReading
    ): USizeExpr = transformExprAfterTransformed(expr, expr.address) {
        computeSideEffect(expr) {
            with(expr.ctx) {
                val addressConstraints = caches.getValue(expr.address)
                val mapLength = mkBvSignedLessOrEqualExpr(expr, PREFERRED_MAX_ARRAY_SIZE.toBv())

                caches[expr] = addressConstraints + mapLength
            }
        }
    }

    private fun <Sort : USort> readingWithSingleArgumentTransform(
        expr: UHeapReading<*, *, Sort>,
        arg: UExpr<*>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, arg) { _ ->
        computeSideEffect(expr) {
            val argConstraint = caches.getValue(arg)
            val selfConstraint = expr.sort.accept(sortPreferredValuesProvider)(expr)

            caches[expr] = argConstraint + selfConstraint
        }
    }

    private fun <Sort : USort> readingWithTwoArgumentsTransform(
        expr: UHeapReading<*, *, Sort>,
        arg0: UExpr<*>,
        arg1: UExpr<*>,
    ): UExpr<Sort> = transformExprAfterTransformed(expr, arg0, arg1) { _, _ ->
        computeSideEffect(expr) {
            val constraints = mutableSetOf<UBoolExpr>()

            constraints += caches.getValue(arg0)
            constraints += caches.getValue(arg1)
            constraints += expr.sort.accept(sortPreferredValuesProvider)(expr)

            caches[expr] = constraints
        }
    }


    // region KExpressions

    override fun <T : KBvSort> transform(expr: KBvSignedLessOrEqualExpr<T>): KExpr<KBoolSort> = with(expr.ctx) {
        transformExprAfterTransformed(expr, expr.arg0, expr.arg1) { lhs, rhs ->
            computeSideEffect(expr) {
                val selfConstraint = mkEq(lhs, rhs)
                caches[expr] = mutableSetOf(selfConstraint) + caches.getValue(lhs) + caches.getValue(rhs)
            }
        }
    }

    // endregion

    private inline fun <T : USort> computeSideEffect(
        expr: UExpr<T>,
        operationWithSideEffect: () -> Unit,
    ): UExpr<T> {
        operationWithSideEffect()
        return expr
    }

    companion object {
        const val PREFERRED_MAX_ARRAY_SIZE = 10
    }
}

private class SortPreferredValuesProvider : KSortVisitor<(KExpr<*>) -> KExpr<KBoolSort>> {
    private val caches: MutableMap<USort, (KExpr<*>) -> KExpr<KBoolSort>> = mutableMapOf()

    override fun <S : KBvSort> visit(sort: S): (KExpr<*>) -> KExpr<KBoolSort> = caches.getOrPut(sort) {
        with(sort.ctx) {
            when (sort) {
                is KBv1Sort -> { expr -> 1.toBv(sort) eq expr.asExpr(sort) }
                else -> {
                    val (minValue, maxValue) = if (sort.sizeBits < 16u) {
                        SMALL_INT_MIN_VALUE to SMALL_INT_MAX_VALUE
                    } else {
                        INT_MIN_VALUE to INT_MAX_VALUE
                    }

                    { expr -> createBvBounds(lowerBound = minValue, upperBound = maxValue, expr) }
                }
            }
        }
    }

    private fun createBvBounds(
        lowerBound: Int,
        upperBound: Int,
        expr: UExpr<*>,
    ): UBoolExpr = with(expr.ctx) {
        val sort = expr.sort as UBvSort
        mkAnd(
            mkBvSignedLessOrEqualExpr(lowerBound.toBv(sort), expr.asExpr(sort)),
            mkBvSignedGreaterOrEqualExpr(upperBound.toBv(sort), expr.asExpr(sort))
        )
    }

    override fun <S : KFpSort> visit(sort: S): (KExpr<*>) -> KExpr<KBoolSort> = caches.getOrPut(sort) {
        when (sort) {
            is KFp32Sort -> { expr -> createFpBounds(expr) }
            is KFp64Sort -> { expr -> createFpBounds(expr) }
            else -> { expr -> createFpBounds(expr) }
        }
    }

    // TODO find a better way to limit fp values
    private fun createFpBounds(expr: UExpr<*>): UBoolExpr = with(expr.uctx) {
        val sort = expr.sort as KFpSort
        mkAnd(
            mkFpLessOrEqualExpr(FP_MIN_VALUE.toFp(sort), expr.asExpr(sort)),
            mkFpGreaterOrEqualExpr(FP_MAX_VALUE.toFp(sort), expr.asExpr(sort))
        )
    }

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(
        sort: KArray2Sort<D0, D1, R>,
    ): (KExpr<*>) -> KExpr<KBoolSort> = sort.range.accept(this)

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(
        sort: KArray3Sort<D0, D1, D2, R>,
    ): (KExpr<*>) -> KExpr<KBoolSort> = sort.range.accept(this)

    override fun <R : KSort> visit(
        sort: KArrayNSort<R>,
    ): (KExpr<*>) -> KExpr<KBoolSort> = sort.range.accept(this)

    override fun <D : KSort, R : KSort> visit(
        sort: KArraySort<D, R>,
    ): (KExpr<*>) -> KExpr<KBoolSort> = sort.range.accept(this)

    override fun visit(sort: KBoolSort): (KExpr<*>) -> KExpr<KBoolSort> = { it.asExpr(sort) }

    override fun visit(sort: KFpRoundingModeSort): (KExpr<*>) -> KExpr<KBoolSort> =
        caches.getOrPut(sort) {
            with(sort.uctx) {
                // TODO double check it
                { expr -> mkFpRoundingModeExpr(KFpRoundingMode.RoundNearestTiesToEven) eq expr.asExpr(sort) }
            }
        }

    override fun visit(sort: KIntSort): (KExpr<*>) -> KExpr<KBoolSort> =
        caches.getOrPut(sort) {
            with(sort.uctx) {
                { expr ->
                    mkAnd(
                        mkArithLe(INT_MIN_VALUE.expr, expr.asExpr(sort)),
                        mkArithGe(INT_MAX_VALUE.expr, expr.asExpr(sort))
                    )
                }
            }
        }

    // TODO find a better way to limit real values
    override fun visit(sort: KRealSort): (KExpr<*>) -> KExpr<KBoolSort> =
        caches.getOrPut(sort) {
            with(sort.uctx) {
                { expr ->
                    mkAnd(
                        mkAnd(
                            mkArithLe(mkRealNum(INT_MIN_VALUE), expr.asExpr(sort)),
                            mkArithGe(mkRealNum(INT_MAX_VALUE), expr.asExpr(sort))
                        )
                    )
                }
            }
        }

    override fun visit(sort: KUninterpretedSort): (KExpr<*>) -> KExpr<KBoolSort> =
        caches.getOrPut(sort) {
            with(sort.uctx) {
                if (sort === addressSort) {
                    { expr -> mkHeapRefEq(nullRef, expr.asExpr(sort)) }
                } else {
                    { _ -> trueExpr }
                }
            }
        }

    companion object {
        const val SMALL_INT_MIN_VALUE = -8
        const val SMALL_INT_MAX_VALUE = 8

        const val INT_MIN_VALUE = -256
        const val INT_MAX_VALUE = 256

        const val FP_MIN_VALUE = -256.0f
        const val FP_MAX_VALUE = 256.0f
    }
}