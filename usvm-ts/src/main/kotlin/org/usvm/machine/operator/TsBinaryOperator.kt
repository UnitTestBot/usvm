package org.usvm.machine.operator

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import mu.KotlinLogging
import org.jacodb.ets.model.EtsStringType
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.api.evalTypeEquals
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.mkNumericExpr
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.util.boolToFp
import org.usvm.util.mkStringBackingElementLValue
import org.usvm.util.mkStringBackingLValue
import org.usvm.util.mkStringBackingLengthLValue

private val logger = KotlinLogging.logger {}

/** Strings are primitive values even though the TS heap stores their UTF-16 contents behind references. */
private fun TsContext.stringValueEquals(
    lhs: UHeapRef,
    rhs: UHeapRef,
    sameReference: UBoolExpr,
    activeGuard: UBoolExpr,
    scope: TsStepScope,
): UBoolExpr? {
    val lhsConstant = (lhs as? UConcreteHeapRef)?.let(::getStringConstantValue)
    val rhsConstant = (rhs as? UConcreteHeapRef)?.let(::getStringConstantValue)
    if (lhsConstant != null && rhsConstant != null) {
        return if (lhsConstant == rhsConstant) trueExpr else falseExpr
    }

    val (lhsIsString, rhsIsString) = scope.calcOnState {
        memory.types.evalTypeEquals(lhs, EtsStringType) to memory.types.evalTypeEquals(rhs, EtsStringType)
    }
    if (lhsIsString.isFalse || rhsIsString.isFalse) return falseExpr

    val bothStrings = mkAnd(lhsIsString, rhsIsString)
    val missingBacking = scope.calcOnState {
        (lhsConstant == null && lhs !in boundedStringBackingRefs) ||
            (rhsConstant == null && rhs !in boundedStringBackingRefs)
    }
    // An alias of a known literal has that literal's value without reading a symbolic backing array.
    val knownLiteralAlias = if (lhsConstant != null || rhsConstant != null) sameReference else falseExpr
    val notBothStrings = mkNot(bothStrings)
    val lhsNullish = mkOr(mkHeapRefEq(lhs, mkTsNullValue()), mkHeapRefEq(lhs, mkUndefinedValue()))
    val rhsNullish = mkOr(mkHeapRefEq(rhs, mkTsNullValue()), mkHeapRefEq(rhs, mkUndefinedValue()))
    if (missingBacking) {
        val supportedWithoutBacking = mkOr(
            mkNot(activeGuard),
            knownLiteralAlias,
            lhsNullish,
            rhsNullish,
            notBothStrings,
        )
        scope.fork(supportedWithoutBacking, blockOnFalseState = {
            terminateAsUnsupported(reason = "String equality needs a modeled string backing for dynamic references")
        }) ?: return null
        return falseExpr
    }

    val comparison = scope.calcOnState {
        val lhsChars = memory.read(mkStringBackingLValue(lhs))
        val rhsChars = memory.read(mkStringBackingLValue(rhs))
        val lhsLength = memory.read(mkStringBackingLengthLValue(lhsChars))
        val rhsLength = memory.read(mkStringBackingLengthLValue(rhsChars))

        StringComparisonData(
            lhsChars = lhsChars,
            rhsChars = rhsChars,
            lhsLength = lhsLength,
            rhsLength = rhsLength,
            maxLength = maxStringLength,
        )
    }

    val zero = mkBv(0)
    val maximum = mkBv(comparison.maxLength)
    val boundedLengths = mkAnd(
        if (lhsConstant == null) {
            mkAnd(
                mkBvSignedGreaterOrEqualExpr(comparison.lhsLength, zero),
                mkBvSignedLessOrEqualExpr(comparison.lhsLength, maximum),
            )
        } else {
            trueExpr
        },
        if (rhsConstant == null) {
            mkAnd(
                mkBvSignedGreaterOrEqualExpr(comparison.rhsLength, zero),
                mkBvSignedLessOrEqualExpr(comparison.rhsLength, maximum),
            )
        } else {
            trueExpr
        },
    )
    val supported = mkOr(
        mkNot(activeGuard),
        knownLiteralAlias,
        lhsNullish,
        rhsNullish,
        notBothStrings,
        boundedLengths,
    )
    scope.fork(supported, blockOnFalseState = {
        terminateAsUnsupported(reason = "String equality requires symbolic string length in 0..$maxStringLength")
    }) ?: return null

    // Known literals supply a tighter comparison bound; all other string lengths are constrained above.
    val comparisonLength = lhsConstant?.length ?: rhsConstant?.length ?: comparison.maxLength
    val equalCharacters = (0 until comparisonLength).map { index ->
        val position = mkBv(index)
        val lhsCharacter = scope.calcOnState {
            memory.read(mkStringBackingElementLValue(comparison.lhsChars, position))
        }
        val rhsCharacter = scope.calcOnState {
            memory.read(mkStringBackingElementLValue(comparison.rhsChars, position))
        }
        mkImplies(mkBvSignedLessExpr(position, comparison.lhsLength), mkEq(lhsCharacter, rhsCharacter))
    }

    return mkAnd(lhsIsString, rhsIsString, mkEq(comparison.lhsLength, comparison.rhsLength), mkAnd(equalCharacters))
}

private data class StringComparisonData(
    val lhsChars: UHeapRef,
    val rhsChars: UHeapRef,
    val lhsLength: UExpr<TsSizeSort>,
    val rhsLength: UExpr<TsSizeSort>,
    val maxLength: Int,
)

private fun TsContext.referenceOrStringValueEquals(
    lhs: UHeapRef,
    rhs: UHeapRef,
    scope: TsStepScope,
    activeGuard: UBoolExpr = trueExpr,
): UBoolExpr? {
    val sameReference = mkHeapRefEq(lhs, rhs)
    if (sameReference.isTrue) return trueExpr

    val equalStringValues = stringValueEquals(lhs, rhs, sameReference, activeGuard, scope) ?: return null
    return mkOr(sameReference, equalStringValues)
}

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

    fun TsContext.onRefWithGuard(
        lhs: UHeapRef,
        rhs: UHeapRef,
        scope: TsStepScope,
        activeGuard: UBoolExpr,
    ): UExpr<*>? = onRef(lhs, rhs, scope)

    fun TsContext.resolveFakeObject(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.resolveFakeObjectWithGuard(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
        activeGuard: UBoolExpr,
    ): UExpr<*>? = resolveFakeObject(lhs, rhs, scope)

    fun TsContext.internalResolve(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
    ): UExpr<*>?

    fun TsContext.resolve(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
        activeGuard: UBoolExpr = trueExpr,
    ): UExpr<*>? {
        if (lhs is UIteExpr<*>) {
            val trueBranchGuard = mkAnd(activeGuard, lhs.condition)
            val falseBranchGuard = mkAnd(activeGuard, mkNot(lhs.condition))
            val trueBranch = resolve(lhs.trueBranch, rhs, scope, trueBranchGuard) ?: return null
            val falseBranch = resolve(lhs.falseBranch, rhs, scope, falseBranchGuard) ?: return null
            return lhs.ctx.mkIte(
                lhs.condition,
                trueBranch.asExpr(falseBranch.sort),
                falseBranch.asExpr(trueBranch.sort)
            )
        }

        if (rhs is UIteExpr<*>) {
            val trueBranchGuard = mkAnd(activeGuard, rhs.condition)
            val falseBranchGuard = mkAnd(activeGuard, mkNot(rhs.condition))
            val trueBranch = resolve(lhs, rhs.trueBranch, scope, trueBranchGuard) ?: return null
            val falseBranch = resolve(lhs, rhs.falseBranch, scope, falseBranchGuard) ?: return null
            return lhs.ctx.mkIte(
                rhs.condition,
                trueBranch.asExpr(falseBranch.sort),
                falseBranch.asExpr(trueBranch.sort)
            )
        }

        val lhsValue = lhs.extractSingleValueFromFakeObjectOrNull(scope) ?: lhs
        val rhsValue = rhs.extractSingleValueFromFakeObjectOrNull(scope) ?: rhs

        if (lhsValue.isFakeObject() || rhsValue.isFakeObject()) {
            return resolveFakeObjectWithGuard(lhsValue, rhsValue, scope, activeGuard)
        }

        val lhsSort = lhsValue.sort
        if (lhsSort == rhsValue.sort) {
            return when (lhsSort) {
                boolSort -> onBool(lhsValue.asExpr(boolSort), rhsValue.asExpr(boolSort), scope)
                fp64Sort -> onFp(lhsValue.asExpr(fp64Sort), rhsValue.asExpr(fp64Sort), scope)
                addressSort -> onRefWithGuard(
                    lhsValue.asExpr(addressSort),
                    rhsValue.asExpr(addressSort),
                    scope,
                    activeGuard,
                )
                else -> TODO("Unsupported sort $lhsSort")
            }
        }

        return internalResolve(lhsValue, rhsValue, scope)
    }

    @Suppress("LongMethod")
    fun <R : USort> TsContext.commonResolveFakeObject(
        lhs: UExpr<*>,
        rhs: UExpr<*>,
        scope: TsStepScope,
        resultSort: R,
        activeGuard: UBoolExpr = trueExpr,
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
                val refRefGuard = mkAnd(activeGuard, lhsType.refTypeExpr, rhsType.refTypeExpr)
                val refRefExpr = onRefWithGuard(lhsRef, rhsRef, scope, refRefGuard)?.asExpr(resultSort) ?: return null
                conjuncts += ExprWithTypeConstraint(
                    constraint = refRefGuard,
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
                        val refRefExpr = onRefWithGuard(
                            lhsRef,
                            rhsRef,
                            scope,
                            mkAnd(activeGuard, lhsType.refTypeExpr),
                        )?.asExpr(resultSort) ?: return null
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
                        val refRefExpr = onRefWithGuard(
                            lhsRef,
                            rhsRef,
                            scope,
                            mkAnd(activeGuard, rhsType.refTypeExpr),
                        )?.asExpr(resultSort) ?: return null
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
        ): UBoolExpr? = onRefWithGuard(lhs, rhs, scope, trueExpr)

        override fun TsContext.onRefWithGuard(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UBoolExpr? {
            // Note: in JavaScript, `null == undefined`
            val lhsIsNull = mkEq(lhs, mkTsNullValue())
            val rhsIsNull = mkEq(rhs, mkTsNullValue())
            val lhsIsUndefined = mkEq(lhs, mkUndefinedValue())
            val rhsIsUndefined = mkEq(rhs, mkUndefinedValue())
            val referenceEquality = referenceOrStringValueEquals(lhs, rhs, scope, activeGuard) ?: return null
            return mkOr(
                mkAnd(lhsIsUndefined, rhsIsNull),
                mkAnd(lhsIsNull, rhsIsUndefined),
                referenceEquality,
            )
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? = resolveFakeObjectWithGuard(lhs, rhs, scope, trueExpr)

        override fun TsContext.resolveFakeObjectWithGuard(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UBoolExpr? {
            return commonResolveFakeObject(
                lhs,
                rhs,
                scope,
                boolSort,
                activeGuard,
            ) { conjuncts -> mkAnd(conjuncts.map { (condition, value) -> mkImplies(condition, value) }) }
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? {
            check(!lhs.isFakeObject() && !rhs.isFakeObject())

            // bool == bool
            if (lhs.sort == boolSort && rhs.sort == boolSort) {
                val lhs = lhs.asExpr(boolSort)
                val rhs = rhs.asExpr(boolSort)
                return onBool(lhs, rhs, scope)
            }

            // fp == fp
            if (lhs.sort == fp64Sort && rhs.sort == fp64Sort) {
                val lhs = lhs.asExpr(fp64Sort)
                val rhs = rhs.asExpr(fp64Sort)
                return onFp(lhs, rhs, scope)
            }

            // bool == fp
            if (lhs.sort == boolSort && rhs.sort == fp64Sort) {
                val lhs = lhs.asExpr(boolSort)
                val rhs = rhs.asExpr(fp64Sort)
                return onFp(boolToFp(lhs), rhs, scope)
            }

            // fp == bool
            if (lhs.sort == fp64Sort && rhs.sort == boolSort) {
                val lhs = lhs.asExpr(fp64Sort)
                val rhs = rhs.asExpr(boolSort)
                return onFp(lhs, boolToFp(rhs), scope)
            }

            // ref == ref
            if (lhs.sort == addressSort && rhs.sort == addressSort) {
                val lhs = lhs.asExpr(addressSort)
                val rhs = rhs.asExpr(addressSort)
                return onRef(lhs, rhs, scope)
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
        ): UExpr<*>? {
            return with(Eq) {
                onRef(lhs, rhs, scope)?.not()
            }
        }

        override fun TsContext.onRefWithGuard(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UExpr<*>? = with(Eq) {
            onRefWithGuard(lhs, rhs, scope, activeGuard)?.not()
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*>? {
            return with(Eq) {
                resolveFakeObject(lhs, rhs, scope)?.not()
            }
        }

        override fun TsContext.resolveFakeObjectWithGuard(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UExpr<*>? = with(Eq) {
            resolveFakeObjectWithGuard(lhs, rhs, scope, activeGuard)?.not()
        }

        override fun TsContext.internalResolve(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UExpr<*>? {
            return with(Eq) {
                internalResolve(lhs, rhs, scope)?.not()
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
        ): UBoolExpr? = onRefWithGuard(lhs, rhs, scope, trueExpr)

        override fun TsContext.onRefWithGuard(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UBoolExpr? = referenceOrStringValueEquals(lhs, rhs, scope, activeGuard)

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? = resolveFakeObjectWithGuard(lhs, rhs, scope, trueExpr)

        override fun TsContext.resolveFakeObjectWithGuard(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UBoolExpr? {
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
                        lhsType.refTypeExpr eq rhsType.refTypeExpr,
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

            check(!lhsValue.isFakeObject()) { "Nested fake objects are not supported" }
            check(!rhsValue.isFakeObject()) { "Nested fake objects are not supported" }

            // Note: this is the case 'ref === ref',
            // which should be `true` only if both have the same reference.
            // It is not correct to delegate to `Eq.resolve` in this case,
            // since `==` treats `null == undefined`, while `null !== undefined`.
            if (lhsValue.sort == addressSort && rhsValue.sort == addressSort) {
                val left = lhsValue.asExpr(addressSort)
                val right = rhsValue.asExpr(addressSort)
                val lhsRefGuard = if (lhs.isFakeObject()) lhs.getFakeType(scope).refTypeExpr else trueExpr
                val rhsRefGuard = if (rhs.isFakeObject()) rhs.getFakeType(scope).refTypeExpr else trueExpr
                val refComparisonGuard = mkAnd(activeGuard, typeConstraint, lhsRefGuard, rhsRefGuard)
                return mkAnd(
                    typeConstraint,
                    onRefWithGuard(left, right, scope, refComparisonGuard) ?: return null
                )
            }

            val looseEqualityConstraint = with(Eq) {
                resolve(lhsValue, rhsValue, scope, activeGuard)?.asExpr(boolSort) ?: return null
            }

            return mkAnd(typeConstraint, looseEqualityConstraint)
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
        ): UBoolExpr? {
            return with(StrictEq) {
                onRef(lhs, rhs, scope)?.not()
            }
        }

        override fun TsContext.onRefWithGuard(
            lhs: UHeapRef,
            rhs: UHeapRef,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UBoolExpr? = with(StrictEq) {
            onRefWithGuard(lhs, rhs, scope, activeGuard)?.not()
        }

        override fun TsContext.resolveFakeObject(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
        ): UBoolExpr? {
            return with(StrictEq) {
                resolveFakeObject(lhs, rhs, scope)?.not()
            }
        }

        override fun TsContext.resolveFakeObjectWithGuard(
            lhs: UExpr<*>,
            rhs: UExpr<*>,
            scope: TsStepScope,
            activeGuard: UBoolExpr,
        ): UBoolExpr? = with(StrictEq) {
            resolveFakeObjectWithGuard(lhs, rhs, scope, activeGuard)?.not()
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
