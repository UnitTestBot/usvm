package org.usvm.solver

import io.ksmt.KContext
import io.ksmt.expr.KApp
import io.ksmt.expr.KExpr
import io.ksmt.sort.KSort
import org.usvm.UAddressSort
import org.usvm.UAllocatedArrayReading
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
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
import org.usvm.UTransformer

class UIsExprCollector<Field, Type>(override val ctx: KContext) : UTransformer<Field, Type> {
    private val caches = hashMapOf<UExpr<*>, Set<UIsExpr<Type>>>()

    fun provide(initialExpr: UExpr<*>): Set<UIsExpr<Type>> =
        caches.getOrElse(initialExpr) {
            apply(initialExpr)
            caches.getValue(initialExpr)
        }

    // region The most common methods

    override fun <T : KSort> transformExpr(expr: KExpr<T>): KExpr<T> = computeSideEffect(expr) {
        caches[expr] = emptySet()
    }

    override fun <T : KSort, A : KSort> transformApp(expr: KApp<T, A>): KExpr<T> =
        computeSideEffect(expr) {
            val nestedConstraints = expr.args.flatMapTo(mutableSetOf()) { provide(it) }

            caches[expr] = nestedConstraints
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

    override fun transform(expr: UNullRef): UExpr<UAddressSort> = transformExpr(expr)

    override fun transform(expr: UIsExpr<Type>): UBoolExpr = computeSideEffect(expr) {
        caches[expr] = setOf(expr)
    }
    override fun transform(
        expr: UInputArrayLengthReading<Type>,
    ): USizeExpr = readingWithSingleArgumentTransform(expr, expr.address)

    override fun <Sort : USort> transform(
        expr: UInputArrayReading<Type, Sort>,
    ): UExpr<Sort> = computeSideEffect(expr) {
        val constraints = mutableSetOf<UIsExpr<Type>>()

        constraints += provide(expr.index)
        constraints += provide(expr.address)

        caches[expr] = constraints
    }

    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort>): UExpr<Sort> =
        readingWithSingleArgumentTransform(expr, expr.index)

    override fun <Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> =
        readingWithSingleArgumentTransform(expr, expr.address)

    private fun <Sort : USort> readingWithSingleArgumentTransform(
        expr: UHeapReading<*, *, Sort>,
        arg: UExpr<*>,
    ): UExpr<Sort> = computeSideEffect(expr) {
        caches[expr] = provide(arg)
    }

    // region KExpressions

    // endregion

    private inline fun <T : USort> computeSideEffect(
        expr: UExpr<T>,
        operationWithSideEffect: () -> Unit,
    ): UExpr<T> {
        operationWithSideEffect()
        return expr
    }
}
