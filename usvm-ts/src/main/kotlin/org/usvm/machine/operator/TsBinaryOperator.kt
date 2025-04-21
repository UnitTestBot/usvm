package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.machine.TsContext
import org.usvm.machine.expr.mkNumericExpr
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.util.boolToFp

sealed interface TsBinaryOperator {

    fun TsContext.onBool(
        lhs: UExpr<UBoolSort>,
        rhs: UExpr<UBoolSort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.onFp(
        lhs: UExpr<KFp64Sort>,
        rhs: UExpr<KFp64Sort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.onRef(
        lhs: UExpr<UAddressSort>,
        rhs: UExpr<UAddressSort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.resolveFakeObject(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.internalResolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TsStepScope,
    ): UExpr<out USort>

    fun TsContext.resolve(
        lhs: UExpr<out USort>,
        rhs: UExpr<out USort>,
        scope: TsStepScope,
    ): UExpr<out USort> {
        val lhsValue = lhs.extractSingleValueFromFakeObjectOrNull(scope) ?: lhs
        val rhsValue = rhs.extractSingleValueFromFakeObjectOrNull(scope) ?: rhs

        if (lhsValue.isFakeObject() || rhsValue.isFakeObject()) {
            return resolveFakeObject(lhsValue, rhsValue, scope)
        }

        val lhsSort = lhsValue.sort
        if (lhsSort == rhsValue.sort) {
            return when (lhsSort) {
                boolSort -> onBool(lhsValue.asExpr(boolSort), rhsValue.asExpr(boolSort), scope)
                fp64Sort -> onFp(lhsValue.asExpr(fp64Sort), rhsValue.asExpr(fp64Sort), scope)
                addressSort -> onRef(lhsValue.asExpr(addressSort), rhsValue.asExpr(addressSort), scope)
                else -> TODO("Unsupported sort $lhsSort")
            }
        }

        return internalResolve(lhsValue, rhsValue, scope)
    }

    data object Eq : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkEq(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpEqualExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkEq(lhs, rhs)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            return scope.calcOnState {
                val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()
                val groundFalseBranch = makeSymbolicPrimitive(boolSort)

                when {
                    lhs.isFakeObject() && rhs.isFakeObject() -> {
                        val lhsType = lhs.getFakeType(scope)
                        val rhsType = rhs.getFakeType(scope)

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
                        val lhsType = lhs.getFakeType(scope)

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

                                scope.assert(lhsType.fpTypeExpr or lhsType.boolTypeExpr)
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

                                scope.assert(lhsType.fpTypeExpr or lhsType.boolTypeExpr)
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

                                scope.assert(lhsType.refTypeExpr)
                                // TODO: support objects
                            }

                            else -> {
                                error("Unsupported sort ${rhs.sort}")
                            }
                        }
                    }

                    rhs.isFakeObject() -> {
                        val rhsType = rhs.getFakeType(scope)

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

                                scope.assert(rhsType.fpTypeExpr or rhsType.boolTypeExpr)
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

                                scope.assert(rhsType.fpTypeExpr or rhsType.boolTypeExpr)
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

                                scope.assert(rhsType.refTypeExpr)
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

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UBoolExpr {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            // bool == bool
            if (lhs.sort == boolSort && rhs.sort == boolSort) {
                val lhs = lhs.asExpr(boolSort)
                val rhs = rhs.asExpr(boolSort)
                return mkEq(lhs, rhs)
            }

            // fp == fp
            if (lhs.sort == fp64Sort && rhs.sort == fp64Sort) {
                val lhs = lhs.asExpr(fp64Sort)
                val rhs = rhs.asExpr(fp64Sort)
                return mkFpEqualExpr(lhs, rhs)
            }

            // bool == fp
            if (lhs.sort == boolSort && rhs.sort == fp64Sort) {
                val lhs = lhs.asExpr(boolSort)
                val rhs = rhs.asExpr(fp64Sort)
                return mkFpEqualExpr(boolToFp(lhs), rhs)
            }

            // fp == bool
            if (lhs.sort == fp64Sort && rhs.sort == boolSort) {
                val lhs = lhs.asExpr(fp64Sort)
                val rhs = rhs.asExpr(boolSort)
                return mkFpEqualExpr(lhs, boolToFp(rhs))
            }

            // ref == ref
            if (lhs.sort == addressSort && rhs.sort == addressSort) {
                // Note:
                //  undefined == null
                //  null == undefined
                val lhs = lhs.asExpr(addressSort)
                val rhs = rhs.asExpr(addressSort)
                return mkOr(
                    mkEq(lhs, rhs),
                    mkEq(lhs, mkUndefinedValue()) and mkEq(rhs, mkTsNullValue()),
                    mkEq(lhs, mkTsNullValue()) and mkEq(rhs, mkUndefinedValue()),
                )
            }

            // bool == ref
            if (lhs.sort == boolSort && rhs.sort == addressSort) {
                return mkFalse()
            }

            // ref == bool
            if (lhs.sort == addressSort && rhs.sort == boolSort) {
                return mkFalse()
            }

            // fp == ref
            if (lhs.sort == fp64Sort && rhs.sort == addressSort) {
                // TODO: the correct impl is to convert ref to primitive,
                //       and then compare fp and this primitive.
                return mkFalse()
            }

            // ref == fp
            if (lhs.sort == addressSort && rhs.sort == fp64Sort) {
                // TODO: the correct impl is to convert ref to primitive,
                //       and then compare this primitive to fp
                return mkFalse()
            }

            // TODO: support bigint
            // TODO: support string

            TODO("Support equality for sorts: ${lhs.sort} == ${rhs.sort}")
        }
    }

    data object Neq : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                onBool(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                onFp(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                onRef(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                resolveFakeObject(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return with(Eq) {
                internalResolve(lhs, rhs, scope).asExpr(boolSort).not()
            }
        }
    }

    data object StrictEq : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkEq(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpEqualExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkHeapRefEq(lhs, rhs)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                when {
                    lhs.isFakeObject() && rhs.isFakeObject() -> {
                        val lhsType = lhs.getFakeType(scope)
                        val rhsType = rhs.getFakeType(scope)

                        scope.assert(
                            mkAnd(
                                lhsType.boolTypeExpr eq rhsType.boolTypeExpr,
                                lhsType.fpTypeExpr eq rhsType.fpTypeExpr,
                                // TODO support type equality
                                lhsType.refTypeExpr eq rhsType.refTypeExpr
                            )
                        )
                    }

                    lhs.isFakeObject() -> {
                        val lhsType = lhs.getFakeType(scope)

                        val condition = when (rhs.sort) {
                            boolSort -> lhsType.boolTypeExpr
                            fp64Sort -> lhsType.fpTypeExpr
                            // TODO support type equality
                            addressSort -> lhsType.refTypeExpr
                            else -> error("Unsupported sort ${rhs.sort}")
                        }

                        scope.assert(condition)
                    }

                    rhs.isFakeObject() -> {
                        val rhsType = rhs.getFakeType(scope)

                        scope.assert(rhsType.mkExactlyOneTypeConstraint(ctx))

                        val condition = when (lhs.sort) {
                            boolSort -> rhsType.boolTypeExpr
                            fp64Sort -> rhsType.fpTypeExpr
                            // TODO support type equality
                            addressSort -> rhsType.refTypeExpr
                            else -> error("Unsupported sort ${lhs.sort}")
                        }

                        scope.assert(condition)
                    }
                }

                return@calcOnState with(Eq) {
                    resolveFakeObject(lhs, rhs, scope)
                }
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    data object Add : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpAddExpr(
                fpRoundingModeSortDefaultValue(),
                boolToFp(lhs),
                boolToFp(rhs)
            )
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
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

    data object Sub : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), boolToFp(lhs), boolToFp(rhs))
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    data object And : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkAnd(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return scope.calcOnState {
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
            }
        }
    }

    data object Or : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkOr(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return scope.calcOnState {
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
            }
        }
    }

    data object Lt : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkAnd(lhs.not(), rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpLessExpr(lhs.asExpr(fp64Sort), rhs.asExpr(fp64Sort))
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    data object Gt : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkAnd(lhs, rhs.not())
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpGreaterExpr(lhs.asExpr(fp64Sort), rhs.asExpr(fp64Sort))
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            TODO("Not yet implemented")
        }
    }

    data object Mul : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UExpr<UBoolSort>,
            rhs: UExpr<UBoolSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UExpr<UAddressSort>,
            rhs: UExpr<UAddressSort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<out USort>,
            rhs: UExpr<out USort>,
            scope: TsStepScope,
        ): UExpr<out USort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }
    }
}
