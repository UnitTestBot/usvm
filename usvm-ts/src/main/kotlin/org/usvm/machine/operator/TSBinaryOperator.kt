package org.usvm.machine.operator

import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.machine.TSContext
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.util.boolToFpSort

// import org.usvm.util.unwrapIfRequired

/**
 * @param[desiredSort] accepts two [USort] instances of the expression operands.
 * It defines a desired [USort] for the binary operator to cast both of its operands to.
 *
 * @param[banSorts] accepts two [UExpr] instances of the expression operands.
 * It returns a [Set] of [USort] that are restricted to be coerced to.
 */

// TODO: desiredSort and banSorts achieve the same goal, although have different semantics. Possible to merge them.
sealed class TSBinaryOperator{
    abstract fun TSContext.onBool(lhs: UExpr<UBoolSort>, rhs: UExpr<UBoolSort>, scope: TSStepScope): UExpr<out USort>
    abstract fun TSContext.onFp(lhs: UExpr<UFpSort>, rhs: UExpr<UFpSort>, scope: TSStepScope): UExpr<out USort>
    abstract fun TSContext.onRef(lhs: UExpr<UAddressSort>, rhs: UExpr<UAddressSort>, scope: TSStepScope): UExpr<out USort>
    // Some binary operations like '==' and '!=' can operate on any pair of equal sorts.
    // However, '+' casts both operands to Number in TypeScript (no considering string currently),
    // so fp64sort is required for both sides.
    // This function allows to filter out excess expressions in type coercion.

    // TODO desiredSort is not the only sort
    // val desiredSort: TSContext.(USort, USort) -> USort = { _, _ -> error("Should not be called") },
    // This function specifies a set of banned sorts pre-coercion.
    // Usage of it is limited and was introduced for Neq operation.
    // Generally designed to filter out excess expressions in type coercion.
    // abstract val banSorts: TSContext.(UExpr<out USort>, UExpr<out USort>) -> Set<USort> = { _, _ -> emptySet() },

    object Eq : TSBinaryOperator(
        // onBool = UContext<TSSizeSort>::mkEq,
        // onFp = UContext<TSSizeSort>::mkFpEqualExpr,
        // onRef = UContext<TSSizeSort>::mkHeapRefEq,
        // desiredSort = { lhs, _ -> lhs }
    ) {
        override fun TSContext.onBool(lhs: UExpr<UBoolSort>, rhs: UExpr<UBoolSort>, scope: TSStepScope): UExpr<out USort> {
            return mkEq(lhs, rhs)
        }

        override fun TSContext.onFp(lhs: UExpr<UFpSort>, rhs: UExpr<UFpSort>, scope: TSStepScope): UExpr<out USort> {
            return mkFpEqualExpr(lhs, rhs)
        }

        override fun TSContext.onRef(lhs: UExpr<UAddressSort>, rhs: UExpr<UAddressSort>, scope: TSStepScope): UExpr<out USort> {
            return mkHeapRefEq(lhs, rhs)
        }

        override fun resolveUnresolvedSorts(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
            TODO()
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            // val unwrappedLhs = lhs.unwrapIfRequired()
            // val unwrappedRhs = rhs.unwrapIfRequired()

            // when (lhs.sort) {
            //     is UBoolSort -> when (rhs.sort) {
            //         is UFpSort -> onFp(boolToFpSort(unwrappedLhs.cast()).cast(), unwrappedRhs.cast(), scope)
            //         is UAddressSort -> TODO()
            //         else -> TODO()
            //     }
            //
            //     is UFpSort -> when (rhs.sort) {
            //         is UBoolSort -> onFp(unwrappedLhs.cast(), boolToFpSort(unwrappedRhs.cast()).cast(), scope)
            //         is UAddressSort -> TODO()
            //         else -> TODO()
            //     }
            //
            //     is UAddressSort -> TODO()
            //     else -> TODO()
            // }
            TODO()
        }

    }

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

        // desiredSort = { lhs, _ -> lhs },
        // banSorts = { lhs, rhs ->
        //     when {
        //         lhs is TSWrappedValue ->
        //             // rhs.sort == addressSort is a mock not to cause undefined
        //             // behaviour with support of new language features.
        //             // For example, supporting language structures could produce
        //             // incorrect additional sort constraints here if addressSort expressions
        //             // do not return empty set.
        //             if (rhs is TSWrappedValue || rhs.sort == addressSort) {
        //                 emptySet()
        //             } else {
        //                 org.usvm.machine.TSTypeSystem.primitiveTypes
        //                     .map(::typeToSort).toSet()
        //                     .minus(rhs.sort)
        //             }
        //
        //         rhs is TSWrappedValue ->
        //             // lhs.sort == addressSort explained as above.
        //             if (lhs.sort == addressSort) {
        //                 emptySet()
        //             } else {
        //                 org.usvm.machine.TSTypeSystem.primitiveTypes
        //                     .map(::typeToSort).toSet()
        //                     .minus(lhs.sort)
        //             }
        //
        //         else -> emptySet()
        //     }
        // }
    ) {
        override fun TSContext.onBool(lhs: UExpr<UBoolSort>, rhs: UExpr<UBoolSort>, scope: TSStepScope): UExpr<out USort> {
            return lhs.neq(rhs)
        }

        override fun TSContext.onFp(lhs: UExpr<UFpSort>, rhs: UExpr<UFpSort>, scope: TSStepScope): UExpr<out USort> {
            return mkFpEqualExpr(lhs, rhs).not()
        }

        override fun TSContext.onRef(lhs: UExpr<UAddressSort>, rhs: UExpr<UAddressSort>, scope: TSStepScope): UExpr<out USort> {
            return mkHeapRefEq(lhs, rhs).not()
        }

        override fun resolveUnresolvedSorts(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    object Add : TSBinaryOperator(
        // TODO: support string concatenation here by resolving stringSort.
        // desiredSort = { _, _ -> fp64Sort },
    ) {
        override fun TSContext.onBool(lhs: UExpr<UBoolSort>, rhs: UExpr<UBoolSort>, scope: TSStepScope): UExpr<out USort> {
            return mkFpAddExpr(fpRoundingModeSortDefaultValue(), boolToFpSort(lhs), boolToFpSort(rhs))
        }

        override fun TSContext.onFp(lhs: UExpr<UFpSort>, rhs: UExpr<UFpSort>, scope: TSStepScope): UExpr<out USort> {
            return mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TSContext.onRef(lhs: UExpr<UAddressSort>, rhs: UExpr<UAddressSort>, scope: TSStepScope): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun resolveUnresolvedSorts(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            // Resolve for known but different sorts
            val lhsSort = lhs.sort

            TODO()

            // val unwrappedLhs = lhs.unwrapIfRequired()
            // val unwrappedRhs = rhs.unwrapIfRequired()
            //
            // when (lhsSort) {
            //     is KFp64Sort -> when (rhs.sort) {
            //         is KBoolSort -> {
            //             val transformedValue = scope.calcOnState {
            //                 exprTransformer.transform(unwrappedRhs, lhsSort)
            //             }
            //             onFp(unwrappedLhs.cast(), transformedValue.cast(), scope)
            //         }
            //
            //         is UAddressSort -> TODO()
            //         else -> TODO()
            //     }
            //
            //     is KBoolSort -> when (rhs.sort) {
            //         is KFp64Sort -> {
            //             val transformedValue = scope.calcOnState {
            //                 exprTransformer.transform(unwrappedLhs, rhs.sort)
            //             }
            //             onFp(transformedValue.cast(), unwrappedRhs.cast(), scope)
            //         }
            //
            //         is UAddressSort -> TODO()
            //         else -> TODO()
            //     }
            //
            //     is UAddressSort -> TODO()
            //     else -> TODO()
            // }
        }
    }

    object And : TSBinaryOperator(
        // desiredSort = { _, _ -> boolSort },
    ) {
        override fun TSContext.onBool(lhs: UExpr<UBoolSort>, rhs: UExpr<UBoolSort>, scope: TSStepScope): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TSContext.onFp(lhs: UExpr<UFpSort>, rhs: UExpr<UFpSort>, scope: TSStepScope): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TSContext.onRef(lhs: UExpr<UAddressSort>, rhs: UExpr<UAddressSort>, scope: TSStepScope): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun resolveUnresolvedSorts(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO()
        }
    }

    abstract fun resolveUnresolvedSorts(lhs: UExpr<out USort>, rhs: UExpr<out USort>): UExpr<out USort>

    internal abstract fun internalResolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    internal fun resolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort> = with(lhs.tctx) {
        val lhsSort = lhs.sort

        // val unwrappedLhs = if (lhs is TSWrappedValue) lhs.value else lhs
        // val unwrappedRhs = if (rhs is TSWrappedValue) rhs.value else rhs
        //
        // if (lhs.sort is TSUnresolvedSort || rhs.sort is TSUnresolvedSort) {
        //     return resolveUnresolvedSorts(lhs, rhs)
        // }
        //
        // if (lhsSort == rhs.sort) {
        //     val result = when (lhsSort) {
        //         is KFp64Sort -> onFp(unwrappedLhs.cast(), unwrappedRhs.cast(), scope)
        //         is KBoolSort -> onBool(unwrappedLhs.cast(), unwrappedRhs.cast(), scope)
        //         is UAddressSort -> onRef(unwrappedLhs.cast(), unwrappedRhs.cast(), scope)
        //         else -> error("Should not be called")
        //     }
        //
        //     return result
        // }

        return internalResolve(lhs, rhs, scope)
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
}

