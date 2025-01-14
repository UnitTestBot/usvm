package org.usvm.machine.operator

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.TSContext
import org.usvm.machine.TSSizeSort
import org.usvm.machine.expr.TSUnresolvedSort
import org.usvm.machine.expr.TSWrappedValue
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.util.boolToFpSort
import org.usvm.util.unwrapIfRequired

/**
 * @param[desiredSort] accepts two [USort] instances of the expression operands.
 * It defines a desired [USort] for the binary operator to cast both of its operands to.
 *
 * @param[banSorts] accepts two [UExpr] instances of the expression operands.
 * It returns a [Set] of [USort] that are restricted to be coerced to.
 */

// TODO: desiredSort and banSorts achieve the same goal, although have different semantics. Possible to merge them.
sealed class TSBinaryOperator(
    val onBool: TSContext.(UExpr<UBoolSort>, UExpr<UBoolSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onBv: TSContext.(UExpr<UBvSort>, UExpr<UBvSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onFp: TSContext.(UExpr<UFpSort>, UExpr<UFpSort>) -> UExpr<out USort> = shouldNotBeCalled,
    val onRef: TSContext.(UExpr<UAddressSort>, UExpr<UAddressSort>) -> UExpr<out USort> = shouldNotBeCalled,
    // Some binary operations like '==' and '!=' can operate on any pair of equal sorts.
    // However, '+' casts both operands to Number in TypeScript (no considering string currently),
    // so fp64sort is required for both sides.
    // This function allows to filter out excess expressions in type coercion.

    // TODO desiredSort is not the only sort
    // val desiredSort: TSContext.(USort, USort) -> USort = { _, _ -> error("Should not be called") },
    // This function specifies a set of banned sorts pre-coercion.
    // Usage of it is limited and was introduced for Neq operation.
    // Generally designed to filter out excess expressions in type coercion.
    val banSorts: TSContext.(UExpr<out USort>, UExpr<out USort>) -> Set<USort> = { _, _ -> emptySet() },
) {

    object Eq : TSBinaryOperator(
        onBool = UContext<TSSizeSort>::mkEq,
        onBv = UContext<TSSizeSort>::mkEq,
        onFp = UContext<TSSizeSort>::mkFpEqualExpr,
        onRef = UContext<TSSizeSort>::mkHeapRefEq,
        // desiredSort = { lhs, _ -> lhs }
    )

    // Neq must not be applied to a pair of expressions
    // containing generated ones during coercion initialization (exprCache intersection).
    // For example,
    // "a (ref reg reading) != 1.0 (fp64 number)"
    // can't yield a list of type coercion bool expressions containing:
    // "a (bool reg reading) != true (bool)",
    // since "1.0.toBool() = true" is a new value for TSExprTransformer(1.0) exprCache.
    //
    // So, that's the reason why banSorts in Neq throws out all primitive types except one of the expressions' one.
    // (because obviously we must be able to coerce to expression's base sort)

    // TODO: banSorts is still draft here, it only handles specific operands' configurations. General solution required.
    object Neq : TSBinaryOperator(
        onBool = { lhs, rhs -> lhs.neq(rhs) },
        onBv = { lhs, rhs -> lhs.neq(rhs) },
        onFp = { lhs, rhs -> mkFpEqualExpr(lhs, rhs).not() },
        onRef = { lhs, rhs -> mkHeapRefEq(lhs, rhs).not() },
        // desiredSort = { lhs, _ -> lhs },
        banSorts = { lhs, rhs ->
            when {
                lhs is TSWrappedValue<*> ->
                    // rhs.sort == addressSort is a mock not to cause undefined
                    // behaviour with support of new language features.
                    // For example, supporting language structures could produce
                    // incorrect additional sort constraints here if addressSort expressions
                    // do not return empty set.
                    if (rhs is TSWrappedValue<*> || rhs.sort == addressSort) {
                        emptySet()
                    } else {
                        org.usvm.machine.TSTypeSystem.primitiveTypes
                            .map(::typeToSort).toSet()
                            .minus(rhs.sort)
                    }

                rhs is TSWrappedValue<*> ->
                    // lhs.sort == addressSort explained as above.
                    if (lhs.sort == addressSort) {
                        emptySet()
                    } else {
                        org.usvm.machine.TSTypeSystem.primitiveTypes
                            .map(::typeToSort).toSet()
                            .minus(lhs.sort)
                    }

                else -> emptySet()
            }
        }
    )

    object Add : TSBinaryOperator(
        onFp = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs) },
        onBv = UContext<TSSizeSort>::mkBvAddExpr,
        onBool = { lhs, rhs -> mkFpAddExpr(fpRoundingModeSortDefaultValue(), boolToFpSort(lhs), boolToFpSort(rhs)) }
        // TODO: support string concatenation here by resolving stringSort.
        // desiredSort = { _, _ -> fp64Sort },
    ) {
        override fun resolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            val superResult = super.resolve(lhs, rhs, scope)
            if (superResult != null) {
                return superResult
            }

            if (lhs.sort is TSUnresolvedSort || rhs.sort is TSUnresolvedSort) {
                TODO()
            }

            val lhsSort = lhs.sort

            val unwrappedLhs =  lhs.unwrapIfRequired()
            val unwrappedRhs = rhs.unwrapIfRequired()

            when (lhsSort) {
                is KFp64Sort -> when (rhs.sort) {
                    is KBoolSort -> {
                        val transformedValue = scope.calcOnState { exprTransformer.transform(unwrappedRhs, lhsSort) }
                        val result = onFp(unwrappedLhs.cast(), transformedValue.cast())
                        return result
                    }
                    is UAddressSort -> TODO()
                    else -> TODO()
                }
                is KBoolSort -> when (rhs.sort) {
                    is KFp64Sort -> {
                        val transformedValue = scope.calcOnState { exprTransformer.transform(unwrappedLhs, rhs.sort) }
                        val result = onFp(transformedValue.cast(), unwrappedRhs.cast())
                        return result
                    }

                    is UAddressSort -> TODO()
                    else -> TODO()
                }
                is UAddressSort -> TODO()
                is UBvSort -> TODO()
                else -> TODO()
            }
        }
    }

    object And : TSBinaryOperator(
        onBool = UContext<TSSizeSort>::mkAnd,
        // desiredSort = { _, _ -> boolSort },
    ) {
    }

    internal open fun resolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>? = with(lhs.tctx) {
        val lhsSort = lhs.sort

        val unwrappedLhs = if (lhs is TSWrappedValue<*>) lhs.value else lhs
        val unwrappedRhs = if (rhs is TSWrappedValue<*>) rhs.value else rhs

        if (lhsSort == rhs.sort) {
            val result = when (lhsSort) {
                is KFp64Sort -> onFp(unwrappedLhs.cast(), unwrappedRhs.cast())
                is KBoolSort -> onBool(unwrappedLhs.cast(), unwrappedRhs.cast())
                is UAddressSort -> onRef(unwrappedLhs.cast(), unwrappedRhs.cast())
                is UBvSort -> onBv(unwrappedLhs.cast(), unwrappedRhs.cast())
                else -> error("Should not be called")
            }

            return result
        }

        return null
    }

    // val bannedSorts = lhs.tctx.banSorts(lhs, rhs)
    //
    // fun apply(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort>? {
    //     val ctx = lhs.tctx
    //     val lhsSort = lhs.sort
    //     val rhsSort = rhs.sort
    //     if (lhsSort != rhsSort) error("Sorts must be equal: $lhsSort != $rhsSort")
    //
    //     // banSorts filtering.
    //     if (lhsSort in bannedSorts) return null
    //     // desiredSort filtering.
    //     // if (ctx.desiredSort(lhsSort, rhsSort) != lhsSort) return null
    //
    //     return when (lhs.sort) {
    //         is UBoolSort -> ctx.onBool(lhs.cast(), rhs.cast())
    //         is UBvSort -> ctx.onBv(lhs.cast(), rhs.cast())
    //         is UFpSort -> ctx.onFp(lhs.cast(), rhs.cast())
    //         is UAddressSort -> ctx.onRef(lhs.cast(), rhs.cast())
    //         else -> error("Unexpected sorts: $lhsSort, $rhsSort")
    //     }
    // }
    //
    // val lhsSort = lhs.sort
    // val rhsSort = rhs.sort
    //
    // val ctx = lhs.tctx
    // // Chosen sort is only used to intersect both exprCaches and
    // // have at least one sort to apply binary operation to.
    // // All sorts are examined in TSExprTransformer class and not limited by this "chosen one".
    // // val sort = ctx.desiredSort(lhsSort, rhsSort)
    //
    // return when {
    //     lhs is TSWrappedValue -> lhs.coerceWithSort(rhs, ::apply, desiredSort = null, scope)
    //     else -> TSWrappedValue(ctx, lhs).coerceWithSort(rhs, ::apply, desiredSort = null, scope)
    // }

    companion object {
        private val shouldNotBeCalled: TSContext.(UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> =
            { _, _ -> error("Should not be called") }
    }
}

