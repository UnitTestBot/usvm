package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import mu.KotlinLogging
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
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
        if (lhs is UIteExpr<*>) {
            val trueBranch = resolve(lhs.trueBranch, rhs, scope) ?: return null
            val falseBranch = resolve(lhs.falseBranch, rhs, scope) ?: return null
            return lhs.ctx.mkIte(
                lhs.condition,
                trueBranch.asExpr(falseBranch.sort),
                falseBranch.asExpr(trueBranch.sort)
            )
        }

        if (rhs is UIteExpr<*>) {
            val trueBranch = resolve(lhs, rhs.trueBranch, scope) ?: return null
            val falseBranch = resolve(lhs, rhs.falseBranch, scope) ?: return null
            return lhs.ctx.mkIte(
                rhs.condition,
                trueBranch.asExpr(falseBranch.sort),
                falseBranch.asExpr(trueBranch.sort)
            )
        }

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

    fun <R : USort> TsContext.commonResolveFakeObject(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
        resultSort: R,
        reduce: (List<ExprWithTypeConstraint<R>>) -> UExpr<R>,
    ): UExpr<R>? {
        check(lhs.isFakeObject() || rhs.isFakeObject())

        val conjuncts = mutableListOf<ExprWithTypeConstraint<R>>()

        when {
            lhs.isFakeObject() && rhs.isFakeObject() -> {
                val lhsType = lhs.getFakeType(scope)
                val rhsType = rhs.getFakeType(scope)

                val lhsBool = lhs.extractBool(scope)
                val rhsBool = rhs.extractBool(scope)

                val lhsFp = lhs.extractFp(scope)
                val rhsFp = rhs.extractFp(scope)

                val lhsRef = lhs.extractRef(scope)
                val rhsRef = rhs.extractRef(scope)

                // fake(bool) + fake(bool)
                val boolBoolExpr = onBool(lhsBool, rhsBool, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.boolTypeExpr, rhsType.boolTypeExpr),
                    expr = boolBoolExpr
                )

                // fake(bool) + fake(fp)
                val boolFpExpr = internalResolve(lhsBool, lhsFp, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.boolTypeExpr, rhsType.fpTypeExpr),
                    expr = boolFpExpr
                )

                // fake(bool) + fake(ref)
                val boolRefExpr = internalResolve(lhsBool, lhsRef, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.boolTypeExpr, rhsType.refTypeExpr),
                    expr = boolRefExpr
                )

                // fake(fp) + fake(bool)
                val fpBoolExpr = internalResolve(lhsFp, rhsBool, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.fpTypeExpr, rhsType.boolTypeExpr),
                    expr = fpBoolExpr
                )

                // fake(fp) + fake(fp)
                val fpFpExpr = onFp(lhsFp, rhsFp, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.fpTypeExpr, rhsType.fpTypeExpr),
                    expr = fpFpExpr
                )

                // fake(fp) + fake(ref)
                val fpRefExpr = internalResolve(lhsFp, rhsRef, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.fpTypeExpr, rhsType.refTypeExpr),
                    expr = fpRefExpr
                )

                // fake(ref) + fake(bool)
                val refBoolExpr = internalResolve(lhsRef, rhsBool, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.refTypeExpr, rhsType.boolTypeExpr),
                    expr = refBoolExpr
                )

                // fake(ref) + fake(fp)
                val refFpExpr = internalResolve(lhsRef, rhsFp, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.refTypeExpr, rhsType.fpTypeExpr),
                    expr = refFpExpr
                )

                // fake(ref) + fake(ref)
                val refRefExpr = onRef(lhsRef, rhsRef, scope)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = mkAnd(lhsType.refTypeExpr, rhsType.refTypeExpr),
                    expr = refRefExpr
                )
            }

            lhs.isFakeObject() -> {
                val lhsType = lhs.getFakeType(scope)
                val lhsBool = lhs.extractBool(scope)
                val lhsFp = lhs.extractFp(scope)
                val lhsRef = lhs.extractRef(scope)

                when (rhs.sort) {
                    is UBoolSort -> {
                        // fake(bool) + bool
                        val rhsBool = rhs.asExpr<UBoolSort>(boolSort)
                        val boolBoolExpr = onBool(lhsBool, rhsBool, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.boolTypeExpr,
                            expr = boolBoolExpr
                        )

                        // fake(fp) + bool
                        val fpBoolExpr = internalResolve(lhsFp, rhsBool, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.fpTypeExpr,
                            expr = fpBoolExpr
                        )

                        // fake(ref) + bool
                        val refBoolExpr = internalResolve(lhsRef, rhsBool, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.refTypeExpr,
                            expr = refBoolExpr
                        )
                    }

                    is KFp64Sort -> {
                        // fake(bool) + fp
                        val rhsFpExpr = rhs.asExpr(fp64Sort)
                        val boolFpExpr = internalResolve(lhsBool, rhsFpExpr, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.boolTypeExpr,
                            expr = boolFpExpr
                        )

                        // fake(fp) + fp
                        val fpFpExpr = onFp(lhsFp, rhsFpExpr, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.fpTypeExpr,
                            expr = fpFpExpr
                        )

                        // fake(ref) + fp
                        val refFpExpr = internalResolve(lhsRef, rhsFpExpr, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.refTypeExpr,
                            expr = refFpExpr
                        )
                    }

                    is UAddressSort -> {
                        // fake(bool) + ref
                        val rhsRef = rhs.asExpr(addressSort)
                        val boolRefExpr = internalResolve(lhsBool, rhsRef, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.boolTypeExpr,
                            expr = boolRefExpr
                        )

                        // fake(fp) + ref
                        val fpRefExpr = internalResolve(lhsFp, rhsRef, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.fpTypeExpr,
                            expr = fpRefExpr
                        )

                        // fake(ref) + ref
                        val refRefExpr = onRef(lhsRef, rhsRef, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = lhsType.refTypeExpr,
                            expr = refRefExpr
                        )
                    }

                    else -> {
                        error("Unsupported sort ${rhs.sort}")
                    }
                }
            }

            rhs.isFakeObject() -> {
                val rhsType = rhs.getFakeType(scope)
                val rhsBool = rhs.extractBool(scope)
                val rhsFp = rhs.extractFp(scope)
                val rhsRef = rhs.extractRef(scope)

                when (lhs.sort) {
                    is UBoolSort -> {
                        // bool + fake(bool)
                        val lhsBool = lhs.asExpr<UBoolSort>(boolSort)
                        val boolBoolExpr = onBool(lhsBool, rhsBool, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.boolTypeExpr,
                            expr = boolBoolExpr
                        )

                        // bool + fake(fp)
                        val boolFpExpr = internalResolve(lhsBool, rhsFp, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.fpTypeExpr,
                            expr = boolFpExpr
                        )

                        // bool + fake(ref)
                        val boolRefExpr = internalResolve(lhsBool, rhsRef, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.refTypeExpr,
                            expr = boolRefExpr
                        )
                    }

                    is KFp64Sort -> {
                        // fp + fake(bool)
                        val lhsFp = lhs.asExpr(fp64Sort)
                        val fpBoolExpr = internalResolve(lhsFp, rhsBool, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.boolTypeExpr,
                            expr = fpBoolExpr
                        )

                        // fp + fake(fp)
                        val fpFpExpr = onFp(lhsFp, rhsFp, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.fpTypeExpr,
                            expr = fpFpExpr
                        )

                        // fp + fake(ref)
                        val fpRefExpr = internalResolve(lhsFp, rhsRef, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.refTypeExpr,
                            expr = fpRefExpr
                        )
                    }

                    is UAddressSort -> {
                        // ref + fake(bool)
                        val lhsRef = lhs.asExpr(addressSort)
                        val refBoolExpr = internalResolve(lhsRef, rhsBool, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.boolTypeExpr,
                            expr = refBoolExpr
                        )

                        // ref + fake(fp)
                        val refFpExpr = internalResolve(lhsRef, rhsFp, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.fpTypeExpr,
                            expr = refFpExpr
                        )

                        // ref + fake(ref)
                        val refRefExpr = onRef(lhsRef, rhsRef, scope)?.asExpr(resultSort) ?: return null
                        conjuncts += ExprWithTypeConstraint(
                            constraint = rhsType.refTypeExpr,
                            expr = refRefExpr
                        )
                    }

                    else -> {
                        error("Unsupported sort ${lhs.sort}")
                    }
                }
            }
        }

        return reduce(conjuncts)
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
            return commonResolveFakeObject(
                lhs,
                rhs,
                scope,
                boolSort
            ) { conjuncts -> mkAnd(conjuncts.map { (condition, value) -> mkImplies(condition, value) }) }
                ?: error("Should not be null")
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

            var lhsValue: UExpr<*> = lhs
            var rhsValue: UExpr<*> = rhs

            val typeConstraint = when {
                lhs.isFakeObject() && rhs.isFakeObject() -> {
                    val lhsType = lhs.getFakeType(scope)
                    val rhsType = rhs.getFakeType(scope)
                    mkAnd(
                        lhsType.boolTypeExpr eq rhsType.boolTypeExpr,
                        lhsType.fpTypeExpr eq rhsType.fpTypeExpr,
                        // TODO support type equality
                        lhsType.refTypeExpr eq rhsType.refTypeExpr
                    )
                }

                lhs.isFakeObject() -> {
                    val lhsType = lhs.getFakeType(scope)
                    when (rhs.sort) {
                        boolSort -> {
                            lhsValue = lhs.extractBool(scope)
                            lhsType.boolTypeExpr
                        }

                        fp64Sort -> {
                            lhsValue = lhs.extractFp(scope)
                            lhsType.fpTypeExpr
                        }

                        // TODO support type equality
                        addressSort -> {
                            lhsValue = lhs.extractRef(scope)
                            lhsType.refTypeExpr
                        }

                        else -> error("Unsupported sort ${rhs.sort}")
                    }
                }

                rhs.isFakeObject() -> {
                    val rhsType = rhs.getFakeType(scope)
                    when (lhs.sort) {
                        boolSort -> {
                            rhsValue = rhs.extractBool(scope)
                            rhsType.boolTypeExpr
                        }

                        fp64Sort -> {
                            rhsValue = rhs.extractFp(scope)
                            rhsType.fpTypeExpr
                        }

                        // TODO support type equality
                        addressSort -> {
                            rhsValue = rhs.extractRef(scope)
                            rhsType.refTypeExpr
                        }

                        else -> error("Unsupported sort ${lhs.sort}")
                    }
                }

                else -> {
                    error("Should not be called")
                }
            }

            val loosyEqualityConstraint = with(Eq) {
                resolve(lhsValue, rhsValue, scope)?.asExpr(boolSort) ?: error("Should not be encountered")
            }

            return mkAnd(typeConstraint, loosyEqualityConstraint)
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? {
            // Strict equality checks that both sides are of the same type,
            // therefore they have to be processed in the other methods.
            // Otherwise, they would have the same sorts.
            return mkFalse()
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
        ): UExpr<KFp64Sort> {
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
        ): UExpr<KFp64Sort> {
            return mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return mkFpAddExpr(
                fpRoundingModeSortDefaultValue(),
                mkNumericExpr(lhs, scope),
                mkNumericExpr(rhs, scope)
            )
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return commonResolveFakeObject(
                lhs,
                rhs,
                scope,
                fp64Sort
            ) { conjuncts ->
                conjuncts.foldRight(mkFp(0.0, fp64Sort).asExpr(fp64Sort)) { value, acc ->
                    mkIte(value.constraint, value.expr, acc)
                }
            } ?: error("Should not be null")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            // TODO support string concatenation
            // TODO support bigint

            return when {
                lhs.sort is UBoolSort && rhs.sort is KFp64Sort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), boolToFp(lhs.cast()), rhs.cast())
                }

                lhs.sort is UBoolSort && rhs.sort is UAddressSort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), boolToFp(lhs.cast()), mkNumericExpr(rhs, scope))
                }

                lhs.sort is KFp64Sort && rhs.sort is UBoolSort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.cast(), boolToFp(rhs.cast()))
                }

                lhs.sort is KFp64Sort && rhs.sort is UAddressSort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), lhs.cast(), mkNumericExpr(rhs, scope))
                }

                lhs.sort is UAddressSort && rhs.sort is KFp64Sort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), mkNumericExpr(lhs, scope), rhs.cast())
                }

                lhs.sort is UAddressSort && rhs.sort is UBoolSort -> {
                    mkFpAddExpr(fpRoundingModeSortDefaultValue(), mkNumericExpr(lhs, scope), boolToFp(rhs.cast()))
                }

                else -> TODO("Unsupported combination ${lhs.sort} and ${rhs.sort}")
            }
        }
    }

    data object Sub : TsArithmeticOperator {
        override fun TsContext.apply(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
        ): UExpr<KFp64Sort> {
            return mkFpSubExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }
    }

    data object Mul : TsArithmeticOperator {
        override fun TsContext.apply(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
        ): UExpr<KFp64Sort> {
            return mkFpMulExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }
    }

    data object Div : TsArithmeticOperator {
        override fun TsContext.apply(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
        ): UExpr<KFp64Sort> {
            return mkFpDivExpr(fpRoundingModeSortDefaultValue(), lhs, rhs)
        }
    }

    data object Rem : TsArithmeticOperator {
        override fun TsContext.apply(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
        ): UExpr<KFp64Sort> {
            return mkFpRemExpr(lhs, rhs)
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

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*> {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            val lhsTruthyExpr = mkTruthyExpr(lhs, scope)
            return iteWriteIntoFakeObject(scope, lhsTruthyExpr, lhs, rhs)
        }
    }

    data object Lt : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkAnd(lhs.not(), rhs)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkFpLessExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UBoolExpr {
            val lhsNumeric = mkNumericExpr(lhs, scope)
            val rhsNumeric = mkNumericExpr(rhs, scope)
            return mkFpLessExpr(lhsNumeric, rhsNumeric)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            return commonResolveFakeObject(
                lhs,
                rhs,
                scope,
                boolSort
            ) { conjuncts ->
                conjuncts.foldRight(mkFalse().asExpr(boolSort)) { value, acc ->
                    mkIte(value.constraint, value.expr, acc)
                }
            } ?: error("Should not be null")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            // TODO: the immediate conversion to numbers is not correct,
            //       we first need to try to convert arguments to primitive values,
            //       which might become strings, for which LT has different semantics.
            val lhsNumeric = mkNumericExpr(lhs, scope)
            val rhsNumeric = mkNumericExpr(rhs, scope)
            return mkFpLessExpr(lhsNumeric, rhsNumeric)
        }
    }

    data object Gt : TsBinaryOperator {
        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkAnd(lhs, rhs.not())
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UBoolExpr {
            return mkFpGreaterExpr(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UBoolExpr {
            val lhsNumeric = mkNumericExpr(lhs, scope)
            val rhsNumeric = mkNumericExpr(rhs, scope)
            return mkFpGreaterExpr(lhsNumeric, rhsNumeric)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            return commonResolveFakeObject(
                lhs,
                rhs,
                scope,
                boolSort
            ) { conjuncts ->
                conjuncts.foldRight(falseExpr.asExpr(boolSort)) { value, acc ->
                    mkIte(value.constraint, value.expr, acc)
                }
            } ?: error("Should not be null")
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr {
            val lhsNumeric = mkNumericExpr(lhs, scope)
            val rhsNumeric = mkNumericExpr(rhs, scope)
            return mkFpGreaterExpr(lhsNumeric, rhsNumeric)
        }
    }

    sealed interface TsArithmeticOperator : TsBinaryOperator {
        fun TsContext.apply(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
        ): UExpr<KFp64Sort>

        override fun TsContext.onBool(
            lhs: UBoolExpr,
            rhs: UBoolExpr,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return apply(left, right)
        }

        override fun TsContext.onFp(
            lhs: UExpr<KFp64Sort>,
            rhs: UExpr<KFp64Sort>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            return apply(lhs, rhs)
        }

        override fun TsContext.onRef(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return apply(left, right)
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return apply(left, right)
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<KFp64Sort> {
            val left = mkNumericExpr(lhs, scope)
            val right = mkNumericExpr(rhs, scope)
            return apply(left, right)
        }
    }
}
