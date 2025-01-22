package org.usvm.machine.operator

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.machine.FakeType
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSUndefinedSort
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.iteWriteIntoFakeObject
import org.usvm.types.single
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
sealed interface TSBinaryOperator {
    fun TSContext.onBool(lhs: UExpr<UBoolSort>, rhs: UExpr<UBoolSort>, scope: TSStepScope): UExpr<out USort>
    fun TSContext.onFp(lhs: UExpr<KFp64Sort>, rhs: UExpr<KFp64Sort>, scope: TSStepScope): UExpr<out USort>
    fun TSContext.onRef(lhs: UExpr<UAddressSort>, rhs: UExpr<UAddressSort>, scope: TSStepScope): UExpr<out USort>
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

    data object Eq : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkEq(lhs, rhs)
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpEqualExpr(lhs, rhs)
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkEq(lhs, rhs)
        }

        override fun resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            // 1. If the operands have the same type, they are compared using `onFp`, `onBool`, etc.

            // 2. If one of the operands is undefined, the other must also be undefined to return true
            if (lhs.sort is TSUndefinedSort || rhs.sort is TSUndefinedSort) {
                TODO()
            }

            // 3. If one of the operands is an object and the other is a primitive, convert the object to a primitive.
            if (lhs.sort is UAddressSort || rhs.sort is UAddressSort) {
                TODO()
            }

            if (lhs.sort is UBoolSort && rhs.sort is KFp64Sort) {
                return mkFpEqualExpr(boolToFpSort(lhs.cast()), rhs.cast())
            }
            if (lhs.sort is KFp64Sort && rhs.sort is UBoolSort) {
                return mkFpEqualExpr(lhs.cast(), boolToFpSort(rhs.cast()))
            }

            // TODO unsupported string

            // TODO unsupported bigint and fp conversion

            TODO("Unsupported String and bigint comparison")
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
    data object Neq : TSBinaryOperator

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
    {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return lhs.neq(rhs)
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpEqualExpr(lhs, rhs).not()
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkHeapRefEq(lhs, rhs).not()
        }


        override fun resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            scope.calcOnState {
                val conjuncts = mutableListOf<UBoolExpr>()

                if (lhs.isFakeObject() && rhs.isFakeObject()) {
                    lhs as UConcreteHeapRef
                    rhs as UConcreteHeapRef

                    val lhsType = memory.typeStreamOf(lhs).single() as FakeType
                    val rhsType = memory.typeStreamOf(rhs).single() as FakeType

                    conjuncts += lhsType.mkAtLeastOneTypeConstraint(ctx)
                    conjuncts += rhsType.mkAtLeastOneTypeConstraint(ctx)

                    conjuncts += mkImplies(
                        mkAnd(lhsType.boolTypeExpr, rhsType.boolTypeExpr),
                        mkEq(
                            memory.read(getIntermediateBoolLValue(lhs.address)),
                            memory.read(getIntermediateBoolLValue(rhs.address))
                        ).not()
                    )

                    conjuncts += mkImplies(
                        mkAnd(lhsType.fpTypeExpr, rhsType.fpTypeExpr),
                        mkFpEqualExpr(
                            memory.read(getIntermediateFpLValue(lhs.address)),
                            memory.read(getIntermediateFpLValue(rhs.address))
                        ).not()
                    )

                    conjuncts += mkImplies(
                        mkAnd(lhsType.refTypeExpr, rhsType.refTypeExpr),
                        mkHeapRefEq(
                            memory.read(getIntermediateRefLValue(lhs.address)),
                            memory.read(getIntermediateRefLValue(rhs.address))
                        ).not()
                    )

                    conjuncts += mkImplies(
                        mkAnd(lhsType.boolTypeExpr, rhsType.fpTypeExpr),
                        mkFpEqualExpr(
                            boolToFpSort(memory.read(getIntermediateBoolLValue(lhs.address))),
                            memory.read(getIntermediateFpLValue(rhs.address))
                        ).not()
                    )

                    // TODO unsupported objects
                    // conjuncts += mkImplies(
                    //     mkAnd(lhsType.boolTypeExpr, rhsType.refTypeExpr),
                    //     mkFpEqual
                    // )

                    // case 7.2.13 IsLooselyEqual #10
                    conjuncts += mkImplies(
                        mkAnd(lhsType.fpTypeExpr, rhsType.boolTypeExpr),
                        mkFpEqualExpr(
                            memory.read(getIntermediateFpLValue(lhs.address)),
                            boolToFpSort(memory.read(getIntermediateBoolLValue(rhs.address)))
                        ).not()
                    )

                    // TODO unsupported unsupported objects
                }

                if (lhs.isFakeObject()) {
                    lhs as UConcreteHeapRef
                    val lhsType = memory.typeStreamOf(lhs).single() as FakeType


                    conjuncts += lhsType.mkAtLeastOneTypeConstraint(ctx)

                    when (rhs.sort) {
                        boolSort -> {
                            conjuncts += mkImplies(
                                lhsType.boolTypeExpr,
                                mkEq(
                                    memory.read(getIntermediateBoolLValue(lhs.address)),
                                    rhs.asExpr(boolSort)
                                ).not()
                            )

                            conjuncts += mkImplies(
                                lhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    memory.read(getIntermediateFpLValue(lhs.address)),
                                    boolToFpSort(rhs.asExpr(boolSort))
                                ).not()
                            )

                            // TODO unsupported objects
                        }
                        fp64Sort -> {
                            conjuncts += mkImplies(
                                lhsType.boolTypeExpr,
                                mkFpEqualExpr(
                                    boolToFpSort(memory.read(getIntermediateBoolLValue(lhs.address))),
                                    rhs.asExpr(fp64Sort)
                                ).not()
                            )

                            conjuncts += mkImplies(
                                lhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    memory.read(getIntermediateFpLValue(lhs.address)),
                                    rhs.asExpr(fp64Sort)
                                ).not()
                            )

                            // TODO unsupported objects
                        }
                        addressSort -> {
                            conjuncts += mkImplies(
                                lhsType.refTypeExpr,
                                mkHeapRefEq(
                                    memory.read(getIntermediateRefLValue(lhs.address)),
                                    rhs.asExpr(addressSort)
                                ).not()
                            )

                            // TODO unsupported objects

                        }
                        else -> error("Unsupported sort ${rhs.sort}")
                    }
                }

                if (rhs.isFakeObject()) {
                    rhs as UConcreteHeapRef
                    val rhsType = memory.typeStreamOf(rhs).single() as FakeType


                    conjuncts += rhsType.mkAtLeastOneTypeConstraint(ctx)

                    when (lhs.sort) {
                        boolSort -> {
                            conjuncts += mkImplies(
                                rhsType.boolTypeExpr,
                                mkEq(
                                    lhs.asExpr(boolSort),
                                    memory.read(getIntermediateBoolLValue(rhs.address))
                                ).not()
                            )

                            conjuncts += mkImplies(
                                rhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    boolToFpSort(lhs.asExpr(boolSort)),
                                    memory.read(getIntermediateFpLValue(rhs.address))
                                ).not()
                            )

                            // TODO unsupported objects
                        }
                        fp64Sort -> {
                            conjuncts += mkImplies(
                                rhsType.boolTypeExpr,
                                mkFpEqualExpr(
                                    lhs.asExpr(fp64Sort),
                                    boolToFpSort(memory.read(getIntermediateBoolLValue(rhs.address)))
                                ).not()
                            )

                            conjuncts += mkImplies(
                                rhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    lhs.asExpr(fp64Sort),
                                    memory.read(getIntermediateFpLValue(rhs.address))
                                ).not()
                            )

                            // TODO unsupported objects
                        }
                        addressSort -> {
                            conjuncts += mkImplies(
                                rhsType.refTypeExpr,
                                mkHeapRefEq(
                                    lhs.asExpr(addressSort),
                                    memory.read(getIntermediateRefLValue(rhs.address))
                                ).not()
                            )

                            // TODO unsupported objects

                        }
                        else -> error("Unsupported sort ${rhs.sort}")
                    }


                }

                mkAnd(conjuncts)
            }
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            val eqValue = Eq.resolve(lhs, rhs, scope)

            TODO()
        }
    }

    data object Add : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpAddExpr(
                fpRoundingModeSortDefaultValue(),
                boolToFpSort(lhs),
                boolToFpSort(rhs)
            )

        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            // TODO support string concatenation
            // TODO support undefined

            // TODO support bigint

            val fpValue = when {
                lhs.sort is UBoolSort && rhs.sort is KFp64Sort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), boolToFpSort(lhs.cast()), rhs.cast())
                }

                lhs.sort is KFp64Sort && rhs.sort is UBoolSort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.cast(), boolToFpSort(rhs.cast()))
                }

                else -> null
            }
            if (fpValue != null) {
                return fpValue
            }

            // TODO support object to primitive

            TODO()
        }
    }

    data object And : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkAnd(lhs, rhs)
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)

                check(lhs.isFakeObject() || rhs.isFakeObject())

                return@calcOnState iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)

                // // if (lhs is truthy) return rhs else lhs
                // if (lhs.sort == rhs.sort) {
                //     // TODO unwrap ite into guarded writes
                //     // return@calcOnState mkIte(lhsTruthyExpr, rhs.asExpr(lhs.sort), lhs.asExpr(rhs.sort))
                // }
                //
                // // if lhs is fake and rhs is not
                // if (lhs.isFakeObject()) {
                //     return@calcOnState iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
                //     // return@calcOnState mkIte(lhsTruthyExpr, rhs.toFakeObject(scope), lhs.asExpr(addressSort))
                // }
                //
                // // vise-versa
                // if (rhs.isFakeObject()) {
                //     return@calcOnState iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
                //     // return@calcOnState mkIte(lhsTruthyExpr, rhs.asExpr(addressSort), lhs.toFakeObject(scope))
                // }


                // error("Unreachable")
                // just incompatible sorts
                // return@calcOnState mkIte(lhsTruthyExpr, rhs.toFakeObject(scope), lhs.toFakeObject(scope))
            }
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO()
        }
    }

    fun resolveFakeObject(lhs: UExpr<out USort>, rhs: UExpr<out USort>, scope: TSStepScope): UExpr<out USort>

    fun internalResolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun resolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort> {
        with(lhs.sort.tctx) {


            if (lhs.isFakeObject() || rhs.isFakeObject()) {
                return resolveFakeObject(lhs, rhs, scope)
            }

            val lhsSort = lhs.sort
            if (lhsSort == rhs.sort) {
                val result = when (lhsSort) {
                    is KFp64Sort -> onFp(lhs.asExpr(fp64Sort), rhs.asExpr(fp64Sort), scope)
                    is KBoolSort -> onBool(lhs.asExpr(boolSort), rhs.asExpr(boolSort), scope)
                    is UAddressSort -> onRef(lhs.asExpr(addressSort), rhs.asExpr(addressSort), scope)
                    else -> error("Should not be called")
                }

                return result
            }

            return internalResolve(lhs, rhs, scope)
        }
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
    //         is KFp64Sort -> ctx.onFp(lhs.cast(), rhs.cast())
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
