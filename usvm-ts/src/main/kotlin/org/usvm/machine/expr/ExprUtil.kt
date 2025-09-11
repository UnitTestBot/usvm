package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.types.single
import org.usvm.util.boolToFp

fun TsContext.checkNotFake(expr: UExpr<*>) {
    require(!expr.isFakeObject()) {
        "Fake object handling should be done outside of this function"
    }
}

fun TsContext.mkTruthyExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UBoolExpr = scope.calcOnState {
    if (expr.isFakeObject()) {
        val falseBranchGround = makeSymbolicPrimitive(boolSort)

        val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()
        val possibleType = memory.types.getTypeStream(expr.asExpr(addressSort)).single() as EtsFakeType

        scope.doWithState {
            pathConstraints += possibleType.mkExactlyOneTypeConstraint(this@mkTruthyExpr)
        }

        if (!possibleType.boolTypeExpr.isFalse) {
            conjuncts += ExprWithTypeConstraint(
                constraint = possibleType.boolTypeExpr,
                expr = memory.read(getIntermediateBoolLValue(expr.address))
            )
        }

        if (!possibleType.fpTypeExpr.isFalse) {
            val value = memory.read(getIntermediateFpLValue(expr.address))
            val numberCondition = mkAnd(
                mkFpEqualExpr(value.asExpr(fp64Sort), mkFp(0.0, fp64Sort)).not(),
                mkFpIsNaNExpr(value.asExpr(fp64Sort)).not()
            )
            conjuncts += ExprWithTypeConstraint(
                constraint = possibleType.fpTypeExpr,
                expr = numberCondition
            )
        }

        if (!possibleType.refTypeExpr.isFalse) {
            val value = memory.read(getIntermediateRefLValue(expr.address))
            conjuncts += ExprWithTypeConstraint(
                constraint = possibleType.refTypeExpr,
                expr = mkAnd(
                    mkHeapRefEq(value, mkTsNullValue()).not(),
                    mkHeapRefEq(value, mkUndefinedValue()).not(),
                )
            )
        }

        conjuncts.foldRight(falseBranchGround) { (condition, value), acc ->
            mkIte(condition, value, acc)
        }
    } else {
        // TODO: simply convert `expr` to bool by implementing ToBoolean(arg):
        //  if arg is Boolean : return arg
        //  if arg is undefined | null | +0f | -0f | NaN | 0 | "" : return false
        //  else return true // non-negative numbers, any living objects, non-empty strings, etc
        //  (https://tc39.es/ecma262/#sec-toboolean)
        //  This conversion might be useful in other places as well, not just for truthy in ifs.

        when (expr.sort) {
            boolSort -> expr.asExpr(boolSort)

            fp64Sort -> mkAnd(
                mkFpEqualExpr(expr.asExpr(fp64Sort), mkFp(0.0, fp64Sort)).not(),
                mkFpIsNaNExpr(expr.asExpr(fp64Sort)).not()
            )

            addressSort -> mkAnd(
                mkHeapRefEq(expr.asExpr(addressSort), mkTsNullValue()).not(),
                mkHeapRefEq(expr.asExpr(addressSort), mkUndefinedValue()).not(),
            )

            else -> TODO("Unsupported sort: ${expr.sort}")
        }
    }
}

fun TsContext.mkNumericExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UExpr<KFp64Sort> {
    if (expr.isFakeObject()) {
        val type = expr.getFakeType(scope)
        return mkIte(
            condition = type.fpTypeExpr,
            trueBranch = expr.extractFp(scope),
            falseBranch = mkIte(
                condition = type.boolTypeExpr,
                trueBranch = mkNumericExpr(expr.extractBool(scope), scope),
                falseBranch = mkNumericExpr(expr.extractRef(scope), scope)
            )
        )
    }

    // 7.1.4 ToNumber ( argument )
    //
    // 1. If argument is a Number, return argument.
    // 2. If argument is either a Symbol or a BigInt, throw a TypeError exception.
    // 3. If argument is undefined, return NaN.
    // 4. If argument is either null or false, return +0𝔽.
    // 5. If argument is true, return 1𝔽.
    // 6. If argument is a String, return StringToNumber(argument).
    // 7. Assert: argument is an Object.
    // 8. Let primValue be ToPrimitive(argument, "number").
    // 9. Assert: primValue is not an Object.
    // 10. Return ToNumber(primValue).

    if (expr.sort == fp64Sort) {
        return expr.asExpr(fp64Sort)
    }

    if (expr == mkUndefinedValue()) {
        return mkFp64NaN()
    }

    if (expr == mkTsNullValue()) {
        return mkFp64(0.0)
    }

    if (expr.sort == boolSort) {
        return boolToFp(expr.asExpr(boolSort))
    }

    // TODO: ToPrimitive, then ToNumber again
    // TODO: probably we need to implement Object (Ref/Fake) -> Number conversion here directly, without ToPrimitive

    // TODO incorrect implementation, returns some number that is not equal to 0 and NaN
    //      https://github.com/UnitTestBot/usvm/issues/280
    return mkIte(
        condition = mkEq(expr.asExpr(addressSort), mkTsNullValue()),
        trueBranch = mkFp(0.0, fp64Sort),
        falseBranch = mkIte(
            mkEq(expr.asExpr(addressSort), mkUndefinedValue()),
            mkFp64NaN(),
            mkFp64NaN()
        )
    )
}

fun TsContext.mkNullishExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UBoolExpr {
    // Handle fake objects specially
    if (expr.isFakeObject()) {
        val fakeType = expr.getFakeType(scope)
        val ref = expr.extractRef(scope)
        // Only check for nullish if the fake object represents a reference type.
        // If it represents a primitive type (bool/number), it's never nullish.
        return mkIte(
            condition = fakeType.refTypeExpr,
            trueBranch = mkOr(
                mkHeapRefEq(ref, mkTsNullValue()),
                mkHeapRefEq(ref, mkUndefinedValue())
            ),
            falseBranch = mkFalse(),
        )
    }

    // Regular reference is nullish if it is either null or undefined
    if (expr.sort == addressSort) {
        val ref = expr.asExpr(addressSort)
        return mkOr(
            mkHeapRefEq(ref, mkTsNullValue()),
            mkHeapRefEq(ref, mkUndefinedValue())
        )
    }

    // Non-reference types (numbers, booleans, strings) are never nullish
    return mkFalse()
}

fun TsState.throwException(reason: String) {
    val ref = ctx.mkStringConstantRef(reason)
    methodResult = TsMethodResult.TsException(ref, EtsStringType)
}

fun TsContext.mkNotNullOrUndefined(ref: UHeapRef): UBoolExpr {
    require(!ref.isFakeObject()) {
        "Fake object handling should be done outside of this function"
    }
    return mkNot(
        mkOr(
            mkHeapRefEq(ref, mkTsNullValue()),
            mkHeapRefEq(ref, mkUndefinedValue())
        )
    )
}

fun TsContext.checkUndefinedOrNullPropertyRead(
    scope: TsStepScope,
    instance: UHeapRef,
    propertyName: String,
): Unit? {
    require(!instance.isFakeObject()) {
        "Fake object handling should be done outside of this function"
    }
    val condition = mkNotNullOrUndefined(instance)
    return scope.fork(
        condition,
        blockOnFalseState = { throwException("Undefined or null property access: $propertyName of $instance") }
    )
}

fun TsContext.checkNegativeIndexRead(
    scope: TsStepScope,
    index: UExpr<TsSizeSort>,
): Unit? {
    val condition = mkBvSignedGreaterOrEqualExpr(index, mkBv(0))
    return scope.fork(
        condition,
        blockOnFalseState = { throwException("Negative index access: $index") }
    )
}

fun TsContext.checkReadingInRange(
    scope: TsStepScope,
    index: UExpr<TsSizeSort>,
    length: UExpr<TsSizeSort>,
): Unit? {
    val condition = mkBvSignedLessExpr(index, length)
    return scope.fork(
        condition,
        blockOnFalseState = { throwException("Index out of bounds: $index, length: $length") }
    )
}

fun TsContext.checkLengthBounds(
    scope: TsStepScope,
    length: UExpr<TsSizeSort>,
    maxLength: Int,
): Unit? {
    // Check that length is non-negative and does not exceed `maxLength`.
    val condition = mkAnd(
        mkBvSignedGreaterOrEqualExpr(length, mkBv(0)),
        mkBvSignedLessOrEqualExpr(length, mkBv(maxLength))
    )
    return scope.assert(condition)
}
