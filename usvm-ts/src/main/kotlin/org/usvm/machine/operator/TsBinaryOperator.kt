package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import mu.KotlinLogging
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.expr.mkNumericExpr
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.util.boolToFp

private val logger = KotlinLogging.logger {}

sealed interface TsBinaryOperator {

    fun TsContext.onBool(
        lhs: UBoolExpr,
        rhs: UBoolExpr,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.onFp(
        lhs: UExpr<KFp64Sort>,
        rhs: UExpr<KFp64Sort>,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.onRef(
        lhs: UHeapRef,
        rhs: UHeapRef,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.resolveFakeObject(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.internalResolve(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.resolve(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
    ): UExpr<*>? {
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
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkEq(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkFpEqualExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkEq(lhs, rhs)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()

            when {
                lhs.isFakeObject() && rhs.isFakeObject() -> {
                    val lhsType = lhs.getFakeType(scope)
                    val rhsType = rhs.getFakeType(scope)

                    // 'fake(bool)' == 'fake(bool)'
                    conjuncts += ExprWithTypeConstraint(
                        constraint = mkAnd(lhsType.boolTypeExpr, rhsType.boolTypeExpr),
                        expr = mkEq(
                            lhs.extractBool(scope),
                            rhs.extractBool(scope),
                        )
                    )

                    // 'fake(fp)' == 'fake(fp)'
                    conjuncts += ExprWithTypeConstraint(
                        constraint = mkAnd(lhsType.fpTypeExpr, rhsType.fpTypeExpr),
                        expr = mkFpEqualExpr(
                            lhs.extractFp(scope),
                            rhs.extractFp(scope),
                        )
                    )

                    // 'fake(ref)' == 'fake(ref)'
                    conjuncts += ExprWithTypeConstraint(
                        constraint = mkAnd(lhsType.refTypeExpr, rhsType.refTypeExpr),
                        expr = mkHeapRefEq(
                            lhs.extractRef(scope),
                            rhs.extractRef(scope),
                        )
                    )

                    // 'fake(bool)' == 'fake(fp)'
                    conjuncts += ExprWithTypeConstraint(
                        constraint = mkAnd(lhsType.boolTypeExpr, rhsType.fpTypeExpr),
                        expr = mkFpEqualExpr(
                            boolToFp(lhs.extractBool(scope)),
                            rhs.extractFp(scope),
                        )
                    )

                    // 'fake(fp)' == 'fake(bool)'
                    conjuncts += ExprWithTypeConstraint(
                        constraint = mkAnd(lhsType.fpTypeExpr, rhsType.boolTypeExpr),
                        expr = mkFpEqualExpr(
                            lhs.extractFp(scope),
                            boolToFp(rhs.extractBool(scope)),
                        )
                    )

                    // TODO: 'fake(ref)' == 'fake(bool)'
                    scope.assert(mkAnd(lhsType.refTypeExpr, rhsType.boolTypeExpr).not())

                    // TODO: 'fake(ref)' == 'fake(fp)'
                    scope.assert(mkAnd(lhsType.refTypeExpr, rhsType.fpTypeExpr).not())

                    // TODO: 'fake(bool)' == 'fake(ref)'
                    scope.assert(mkAnd(lhsType.boolTypeExpr, rhsType.refTypeExpr).not())

                    // TODO: 'fake(fp)' == 'fake(ref)'
                    scope.assert(mkAnd(lhsType.fpTypeExpr, rhsType.refTypeExpr).not())
                }

                lhs.isFakeObject() -> {
                    val lhsType = lhs.getFakeType(scope)

                    when (rhs.sort) {
                        boolSort -> {
                            // 'fake(bool)' == 'fp'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = lhsType.boolTypeExpr,
                                expr = mkEq(
                                    lhs.extractBool(scope),
                                    rhs.asExpr(boolSort),
                                )
                            )

                            // 'fake(fp)' == 'bool'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = lhsType.fpTypeExpr,
                                expr = mkFpEqualExpr(
                                    lhs.extractFp(scope),
                                    boolToFp(rhs.asExpr(boolSort)),
                                )
                            )

                            // TODO: 'fake(ref)' == 'bool'
                            scope.assert(lhsType.refTypeExpr.not())
                        }

                        fp64Sort -> {
                            // 'fake(bool)' == 'fp'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = lhsType.boolTypeExpr,
                                expr = mkFpEqualExpr(
                                    boolToFp(lhs.extractBool(scope)),
                                    rhs.asExpr(fp64Sort),
                                )
                            )

                            // 'fake(fp)' == 'fp'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = lhsType.fpTypeExpr,
                                expr = mkFpEqualExpr(
                                    lhs.extractFp(scope),
                                    rhs.asExpr(fp64Sort),
                                )
                            )

                            // TODO: 'fake(ref)' == 'fp'
                            scope.assert(lhsType.refTypeExpr.not())
                        }

                        addressSort -> {
                            // TODO: 'fake(bool)' == 'ref'
                            scope.assert(lhsType.boolTypeExpr.not())

                            // TODO: 'fake(fp)' == 'ref'
                            scope.assert(lhsType.fpTypeExpr.not())

                            // 'fake(ref)' == 'ref'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = lhsType.refTypeExpr,
                                expr = mkHeapRefEq(
                                    lhs.extractRef(scope),
                                    rhs.asExpr(addressSort),
                                )
                            )
                        }

                        else -> {
                            error("Unsupported sort ${rhs.sort}")
                        }
                    }
                }

                rhs.isFakeObject() -> {
                    val rhsType = rhs.getFakeType(scope)

                    when (lhs.sort) {
                        boolSort -> {
                            // 'bool' == 'fake(bool)'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = rhsType.boolTypeExpr,
                                expr = mkEq(
                                    lhs.asExpr(boolSort),
                                    rhs.extractBool(scope),
                                )
                            )

                            // 'bool' == 'fake(fp)'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = rhsType.fpTypeExpr,
                                expr = mkFpEqualExpr(
                                    boolToFp(lhs.asExpr(boolSort)),
                                    rhs.extractFp(scope),
                                )
                            )

                            // TODO: 'bool' == 'fake(ref)'
                            scope.assert(rhsType.refTypeExpr.not())
                        }

                        fp64Sort -> {
                            // 'fp' == 'fake(bool)'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = rhsType.boolTypeExpr,
                                expr = mkFpEqualExpr(
                                    lhs.asExpr(fp64Sort),
                                    boolToFp(rhs.extractBool(scope)),
                                )
                            )

                            // 'fp' == 'fake(fp)'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = rhsType.fpTypeExpr,
                                expr = mkFpEqualExpr(
                                    lhs.asExpr(fp64Sort),
                                    rhs.extractFp(scope),
                                )
                            )

                            // TODO: 'fp' == 'fake(ref)'
                            scope.assert(rhsType.refTypeExpr.not())
                        }

                        addressSort -> {
                            // TODO: 'ref' == 'fake(bool)'
                            scope.assert(rhsType.boolTypeExpr.not())

                            // TODO: 'ref' == 'fake(fp)'
                            scope.assert(rhsType.fpTypeExpr.not())

                            // 'ref' == 'fake(ref)'
                            conjuncts += ExprWithTypeConstraint(
                                constraint = rhsType.refTypeExpr,
                                expr = mkHeapRefEq(
                                    lhs.asExpr(addressSort),
                                    rhs.extractRef(scope),
                                )
                            )
                        }

                        else -> error("Unsupported sort ${rhs.sort}")
                    }
                }
            }

            // val ground: UBoolExpr = mkFalse()
            // return conjuncts.foldRight(ground) { (condition, value), acc ->
            //     mkIte(condition, value, acc)
            // }
            return mkAnd(conjuncts.map { (condition, value) -> mkImplies(condition, value) })
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
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
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            return with(Eq) {
                onBool(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return with(Eq) {
                onFp(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            return with(Eq) {
                onRef(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            return with(Eq) {
                resolveFakeObject(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            return with(Eq) {
                internalResolve(lhs, rhs, scope).not()
            }
        }
    }

    data object StrictEq : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkEq(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkFpEqualExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkHeapRefEq(lhs, rhs)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            when {
                lhs.isFakeObject() && rhs.isFakeObject() -> {
                    val lhsType = lhs.getFakeType(scope)
                    val rhsType = rhs.getFakeType(scope)
                    val condition = mkAnd(
                        lhsType.boolTypeExpr eq rhsType.boolTypeExpr,
                        lhsType.fpTypeExpr eq rhsType.fpTypeExpr,
                        // TODO support type equality
                        lhsType.refTypeExpr eq rhsType.refTypeExpr
                    )
                    scope.assert(condition)
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

            return with(Eq) {
                resolveFakeObject(lhs, rhs, scope)
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? {
            logger.warn { "Not implemented operator: StrictEq" }
            scope.assert(falseExpr)
            return null
        }
    }

    data object StrictNeq : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UBoolExpr {
            return with(StrictEq) {
                onBool(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return with(StrictEq) {
                onFp(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UBoolExpr {
            return with(StrictEq) {
                onRef(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            return with(StrictEq) {
                resolveFakeObject(lhs, rhs, scope).not()
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? {
            return with(StrictEq) {
                internalResolve(lhs, rhs, scope)?.not()
            }
        }
    }

    data object Add : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
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
        ): UExpr<*> {
            return mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
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
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), boolToFp(lhs), boolToFp(rhs))
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }
    }

    data object And : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkAnd(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return scope.calcOnState {
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, rhs, lhs)
            }
        }
    }

    data object Or : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkOr(lhs, rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            return internalResolve(lhs, rhs, scope)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(lhs.isFakeObject() || rhs.isFakeObject())

            return scope.calcOnState {
                val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
            }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return scope.calcOnState {
                iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
            }
        }
    }

    data object Lt : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkAnd(lhs.not(), rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkFpLessExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }
    }

    data object Gt : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkAnd(lhs, rhs.not())
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkFpGreaterExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            TODO("Not yet implemented")
        }
    }

    data object Mul : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<*> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<*> {
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<*> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(lhs.isFakeObject() || rhs.isFakeObject())
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), left, right)
        }
    }
}
