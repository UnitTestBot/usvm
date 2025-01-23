package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.typeStreamOf
import org.usvm.machine.FakeType
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSUndefinedSort
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.iteWriteIntoFakeObject
import org.usvm.types.single
import org.usvm.util.boolToFpSort

sealed interface TSBinaryOperator {
    fun TSContext.onBool(
        lhs: UExpr<UBoolSort>,
        rhs: UExpr<UBoolSort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun TSContext.onFp(
        lhs: UExpr<KFp64Sort>,
        rhs: UExpr<KFp64Sort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun TSContext.onRef(
        lhs: UExpr<UAddressSort>,
        rhs: UExpr<UAddressSort>,
        scope: TSStepScope,
    ): UExpr<out USort>

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
        ): UExpr<out USort> = with(lhs.tctx) {
            scope.calcOnState {
                val conjuncts = mutableListOf<Pair<UBoolExpr, UBoolExpr>>()
                val groundFalseBranch = makeSymbolicPrimitive(boolSort)

                if (lhs.isFakeObject() && rhs.isFakeObject()) {
                    lhs as UConcreteHeapRef
                    rhs as UConcreteHeapRef

                    val lhsType = memory.typeStreamOf(lhs).single() as FakeType
                    val rhsType = memory.typeStreamOf(rhs).single() as FakeType

                    scope.assert(
                        mkAnd(
                            lhsType.mkExactlyOneTypeConstraint(ctx),
                            rhsType.mkExactlyOneTypeConstraint(ctx)
                        )
                    )

                    conjuncts += Pair(
                        mkAnd(lhsType.boolTypeExpr, rhsType.boolTypeExpr),
                        mkEq(
                            memory.read(getIntermediateBoolLValue(lhs.address)),
                            memory.read(getIntermediateBoolLValue(rhs.address))
                        )
                    )

                    conjuncts += Pair(
                        mkAnd(lhsType.fpTypeExpr, rhsType.fpTypeExpr),
                        mkFpEqualExpr(
                            memory.read(getIntermediateFpLValue(lhs.address)),
                            memory.read(getIntermediateFpLValue(rhs.address))
                        )
                    )

                    conjuncts += Pair(
                        mkAnd(lhsType.refTypeExpr, rhsType.refTypeExpr),
                        mkHeapRefEq(
                            memory.read(getIntermediateRefLValue(lhs.address)),
                            memory.read(getIntermediateRefLValue(rhs.address))
                        )
                    )

                    conjuncts += Pair(
                        mkAnd(lhsType.boolTypeExpr, rhsType.fpTypeExpr),
                        mkFpEqualExpr(
                            boolToFpSort(memory.read(getIntermediateBoolLValue(lhs.address))),
                            memory.read(getIntermediateFpLValue(rhs.address))
                        )
                    )

                    conjuncts += Pair(
                        mkAnd(lhsType.fpTypeExpr, rhsType.boolTypeExpr),
                        mkFpEqualExpr(
                            memory.read(getIntermediateFpLValue(lhs.address)),
                            boolToFpSort(memory.read(getIntermediateBoolLValue(rhs.address)))
                        )
                    )

                    // TODO: support objects
                }

                if (lhs.isFakeObject()) {
                    lhs as UConcreteHeapRef
                    val lhsType = memory.typeStreamOf(lhs).single() as FakeType

                    scope.assert(lhsType.mkExactlyOneTypeConstraint(ctx))

                    when (rhs.sort) {
                        boolSort -> {
                            conjuncts += Pair(
                                lhsType.boolTypeExpr,
                                mkEq(
                                    memory.read(getIntermediateBoolLValue(lhs.address)),
                                    rhs.asExpr(boolSort)
                                )
                            )

                            conjuncts += Pair(
                                lhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    memory.read(getIntermediateFpLValue(lhs.address)),
                                    boolToFpSort(rhs.asExpr(boolSort))
                                )
                            )

                            // TODO: support objects
                        }

                        fp64Sort -> {
                            conjuncts += Pair(
                                lhsType.boolTypeExpr,
                                mkFpEqualExpr(
                                    boolToFpSort(memory.read(getIntermediateBoolLValue(lhs.address))),
                                    rhs.asExpr(fp64Sort)
                                )
                            )

                            conjuncts += Pair(
                                lhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    memory.read(getIntermediateFpLValue(lhs.address)),
                                    rhs.asExpr(fp64Sort)
                                )
                            )

                            // TODO: support objects
                        }

                        addressSort -> {
                            conjuncts += Pair(
                                lhsType.refTypeExpr,
                                mkHeapRefEq(
                                    memory.read(getIntermediateRefLValue(lhs.address)),
                                    rhs.asExpr(addressSort)
                                )
                            )

                            // TODO: support objects
                        }

                        else -> error("Unsupported sort ${rhs.sort}")
                    }
                }

                if (rhs.isFakeObject()) {
                    rhs as UConcreteHeapRef
                    val rhsType = memory.typeStreamOf(rhs).single() as FakeType

                    scope.assert(rhsType.mkExactlyOneTypeConstraint(ctx))

                    when (lhs.sort) {
                        boolSort -> {
                            conjuncts += Pair(
                                rhsType.boolTypeExpr,
                                mkEq(
                                    lhs.asExpr(boolSort),
                                    memory.read(getIntermediateBoolLValue(rhs.address))
                                )
                            )

                            conjuncts += Pair(
                                rhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    boolToFpSort(lhs.asExpr(boolSort)),
                                    memory.read(getIntermediateFpLValue(rhs.address))
                                )
                            )

                            // TODO: support objects
                        }

                        fp64Sort -> {
                            conjuncts += Pair(
                                rhsType.boolTypeExpr,
                                mkFpEqualExpr(
                                    lhs.asExpr(fp64Sort),
                                    boolToFpSort(memory.read(getIntermediateBoolLValue(rhs.address)))
                                )
                            )

                            conjuncts += Pair(
                                rhsType.fpTypeExpr,
                                mkFpEqualExpr(
                                    lhs.asExpr(fp64Sort),
                                    memory.read(getIntermediateFpLValue(rhs.address))
                                )
                            )

                            // TODO: support objects
                        }

                        addressSort -> {
                            conjuncts += Pair(
                                rhsType.refTypeExpr,
                                mkHeapRefEq(
                                    lhs.asExpr(addressSort),
                                    memory.read(getIntermediateRefLValue(rhs.address))
                                )
                            )

                            // TODO: support objects

                        }

                        else -> error("Unsupported sort ${rhs.sort}")
                    }

                }

                conjuncts.foldRight(groundFalseBranch) { (condition, value), acc ->
                    mkIte(condition, value, acc)
                }
            }
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

            // TODO: support string

            // TODO: support bigint and fp conversion

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
    data object Neq : TSBinaryOperator {
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

        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            with(Eq) {
                onBool(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            with(Eq) {
                onFp(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            with(Eq) {
                onRef(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            Eq.resolveFakeObject(lhs, rhs, scope).asExpr(boolSort).not()
        }

        override fun internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> = with(lhs.tctx) {
            Eq.internalResolve(lhs, rhs, scope).asExpr(boolSort).not()
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

            // TODO: support object to primitive

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

    fun resolveFakeObject(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun internalResolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun resolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort> = with(lhs.sort.tctx) {
        if (lhs.isFakeObject() || rhs.isFakeObject()) {
            return resolveFakeObject(lhs, rhs, scope)
        }

        val lhsSort = lhs.sort
        if (lhsSort == rhs.sort) {
            return when (lhsSort) {
                boolSort -> onBool(lhs.asExpr(boolSort), rhs.asExpr(boolSort), scope)
                fp64Sort -> onFp(lhs.asExpr(fp64Sort), rhs.asExpr(fp64Sort), scope)
                addressSort -> onRef(lhs.asExpr(addressSort), rhs.asExpr(addressSort), scope)
                else -> TODO("Unsupported sort $lhsSort")
            }
        }

        internalResolve(lhs, rhs, scope)
    }
}
