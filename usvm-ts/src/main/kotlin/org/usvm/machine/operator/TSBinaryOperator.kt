package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.typeStreamOf
import org.usvm.machine.TSContext
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.FakeType
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.types.single
import org.usvm.util.boolToFp

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

    fun TSContext.resolveFakeObject(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun TSContext.internalResolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort>

    fun TSContext.resolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TSStepScope,
    ): UExpr<out USort> {
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

        return internalResolve(lhs, rhs, scope)
    }

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

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            return scope.calcOnState {
                val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()
                val groundFalseBranch = makeSymbolicPrimitive(boolSort)

                when {
                    lhs.isFakeObject() && rhs.isFakeObject() -> {
                        val lhsType = memory.typeStreamOf(lhs).single() as FakeType
                        val rhsType = memory.typeStreamOf(rhs).single() as FakeType

                        scope.assert(
                            mkAnd(
                                lhsType.mkExactlyOneTypeConstraint(ctx),
                                rhsType.mkExactlyOneTypeConstraint(ctx)
                            )
                        )

                        conjuncts += ExprWithTypeConstraint(
                            constraint = mkAnd(lhsType.boolTypeExpr, rhsType.boolTypeExpr),
                            expr = mkEq(
                                memory.read(getIntermediateBoolLValue(lhs.address)),
                                memory.read(getIntermediateBoolLValue(rhs.address))
                            )
                        )

                        conjuncts += ExprWithTypeConstraint(
                            constraint = mkAnd(lhsType.fpTypeExpr, rhsType.fpTypeExpr),
                            expr = mkFpEqualExpr(
                                memory.read(getIntermediateFpLValue(lhs.address)),
                                memory.read(getIntermediateFpLValue(rhs.address))
                            )
                        )

                        conjuncts += ExprWithTypeConstraint(
                            constraint = mkAnd(lhsType.refTypeExpr, rhsType.refTypeExpr),
                            expr = mkHeapRefEq(
                                memory.read(getIntermediateRefLValue(lhs.address)),
                                memory.read(getIntermediateRefLValue(rhs.address))
                            )
                        )

                        conjuncts += ExprWithTypeConstraint(
                            constraint = mkAnd(lhsType.boolTypeExpr, rhsType.fpTypeExpr),
                            expr = mkFpEqualExpr(
                                boolToFp(memory.read(getIntermediateBoolLValue(lhs.address))),
                                memory.read(getIntermediateFpLValue(rhs.address))
                            )
                        )

                        conjuncts += ExprWithTypeConstraint(
                            constraint = mkAnd(lhsType.fpTypeExpr, rhsType.boolTypeExpr),
                            expr = mkFpEqualExpr(
                                memory.read(getIntermediateFpLValue(lhs.address)),
                                boolToFp(memory.read(getIntermediateBoolLValue(rhs.address)))
                            )
                        )

                        // TODO: support objects
                    }

                    lhs.isFakeObject() -> {
                        val lhsType = memory.typeStreamOf(lhs).single() as FakeType

                        scope.assert(lhsType.mkExactlyOneTypeConstraint(ctx))

                        when (rhs.sort) {
                            boolSort -> {
                                conjuncts += ExprWithTypeConstraint(
                                    constraint = lhsType.boolTypeExpr,
                                    expr = mkEq(
                                        memory.read(getIntermediateBoolLValue(lhs.address)),
                                        rhs.asExpr(boolSort)
                                    )
                                )

                                conjuncts += ExprWithTypeConstraint(
                                    constraint = lhsType.fpTypeExpr,
                                    expr = mkFpEqualExpr(
                                        memory.read(getIntermediateFpLValue(lhs.address)),
                                        boolToFp(rhs.asExpr(boolSort))
                                    )
                                )

                                // TODO: support objects
                            }

                            fp64Sort -> {
                                conjuncts += ExprWithTypeConstraint(
                                    constraint = lhsType.boolTypeExpr,
                                    expr = mkFpEqualExpr(
                                        boolToFp(memory.read(getIntermediateBoolLValue(lhs.address))),
                                        rhs.asExpr(fp64Sort)
                                    )
                                )

                                conjuncts += ExprWithTypeConstraint(
                                    constraint = lhsType.fpTypeExpr,
                                    expr = mkFpEqualExpr(
                                        memory.read(getIntermediateFpLValue(lhs.address)),
                                        rhs.asExpr(fp64Sort)
                                    )
                                )

                                // TODO: support objects
                            }

                            addressSort -> {
                                conjuncts += ExprWithTypeConstraint(
                                    constraint = lhsType.refTypeExpr,
                                    expr = mkHeapRefEq(
                                        memory.read(getIntermediateRefLValue(lhs.address)),
                                        rhs.asExpr(addressSort)
                                    )
                                )

                                // TODO: support objects
                            }

                            else -> {
                                error("Unsupported sort ${rhs.sort}")
                            }
                        }
                    }

                    rhs.isFakeObject() -> {
                        val rhsType = memory.typeStreamOf(rhs).single() as FakeType

                        scope.assert(rhsType.mkExactlyOneTypeConstraint(ctx))

                        when (lhs.sort) {
                            boolSort -> {
                                conjuncts += ExprWithTypeConstraint(
                                    constraint = rhsType.boolTypeExpr,
                                    expr = mkEq(
                                        lhs.asExpr(boolSort),
                                        memory.read(getIntermediateBoolLValue(rhs.address))
                                    )
                                )

                                conjuncts += ExprWithTypeConstraint(
                                    constraint = rhsType.fpTypeExpr,
                                    expr = mkFpEqualExpr(
                                        boolToFp(lhs.asExpr(boolSort)),
                                        memory.read(getIntermediateFpLValue(rhs.address))
                                    )
                                )

                                // TODO: support objects
                            }

                            fp64Sort -> {
                                conjuncts += ExprWithTypeConstraint(
                                    constraint = rhsType.boolTypeExpr,
                                    expr = mkFpEqualExpr(
                                        lhs.asExpr(fp64Sort),
                                        boolToFp(memory.read(getIntermediateBoolLValue(rhs.address)))
                                    )
                                )

                                conjuncts += ExprWithTypeConstraint(
                                    constraint = rhsType.fpTypeExpr,
                                    expr = mkFpEqualExpr(
                                        lhs.asExpr(fp64Sort),
                                        memory.read(getIntermediateFpLValue(rhs.address))
                                    )
                                )

                                // TODO: support objects
                            }

                            addressSort -> {
                                conjuncts += ExprWithTypeConstraint(
                                    constraint = rhsType.refTypeExpr,
                                    expr = mkHeapRefEq(
                                        lhs.asExpr(addressSort),
                                        memory.read(getIntermediateRefLValue(rhs.address))
                                    )
                                )

                                // TODO: support objects

                            }

                            else -> error("Unsupported sort ${rhs.sort}")
                        }
                    }
                }

                conjuncts.foldRight(groundFalseBranch) { (condition, value), acc ->
                    mkIte(condition, value, acc)
                }
            }
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            // 1. If the operands have the same type, they are compared using `onFp`, `onBool`, etc.

            // 2. If one of the operands is undefined, the other must also be undefined to return true
            // if (lhs.sort is TSUndefinedSort || rhs.sort is TSUndefinedSort) {
            //     TODO()
            // }

            // 3. If one of the operands is an object and the other is a primitive, convert the object to a primitive.
            if (lhs.sort is UAddressSort || rhs.sort is UAddressSort) {
                TODO()
            }

            if (lhs.sort is UBoolSort && rhs.sort is KFp64Sort) {
                return mkFpEqualExpr(boolToFp(lhs.cast()), rhs.cast())
            }

            if (lhs.sort is KFp64Sort && rhs.sort is UBoolSort) {
                return mkFpEqualExpr(lhs.cast(), boolToFp(rhs.cast()))
            }

            // TODO: support string

            // TODO: support bigint and fp conversion

            TODO("Unsupported String and bigint comparison")
        }

    }

    data object Neq : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                onBool(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                onFp(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                onRef(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                resolveFakeObject(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                internalResolve(lhs, rhs, scope).asExpr(boolSort).not()
            }
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
                boolToFp(lhs),
                boolToFp(rhs)
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

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            TODO("Not yet implemented")
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            // TODO support string concatenation
            // TODO support undefined

            // TODO support bigint

            val fpValue = when {
                lhs.sort is UBoolSort && rhs.sort is KFp64Sort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), boolToFp(lhs.cast()), rhs.cast())
                }

                lhs.sort is KFp64Sort && rhs.sort is UBoolSort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.cast(), boolToFp(rhs.cast()))
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

    data object Sub : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), boolToFp(lhs), boolToFp(rhs))
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
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
            return internalResolve(lhs, rhs, scope)
        }

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
            }
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return scope.calcOnState {
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
            }
        }
    }

    data object Or : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkOr(lhs, rhs)
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
            return internalResolve(lhs, rhs, scope)
        }

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
            }
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return scope.calcOnState {
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
            }
        }
    }

    data object Lt : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkAnd(lhs.not(), rhs)
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpLessExpr(lhs.asExpr(fp64Sort), rhs.asExpr(fp64Sort))
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    data object Gt : TSBinaryOperator {
        override fun TSContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkAnd(lhs, rhs.not())
        }

        override fun TSContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            return mkFpGreaterExpr(lhs.asExpr(fp64Sort), rhs.asExpr(fp64Sort))
        }

        override fun TSContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TSContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TSContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TSStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }
}
