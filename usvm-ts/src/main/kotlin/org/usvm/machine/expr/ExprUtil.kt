package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.FakeType
import org.usvm.types.single
import org.usvm.util.boolToFp

fun TsContext.mkTruthyExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
): UBoolExpr = scope.calcOnState {
    if (expr.isFakeObject()) {
        val falseBranchGround = makeSymbolicPrimitive(boolSort)

        val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()
        val possibleType = memory.types.getTypeStream(expr.asExpr(addressSort)).single() as FakeType

        scope.assert(possibleType.mkExactlyOneTypeConstraint(this@mkTruthyExpr))

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
): UExpr<KFp64Sort> = scope.calcOnState {

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
        return@calcOnState expr.asExpr(fp64Sort)
    }

    if (expr == mkUndefinedValue()) {
        return@calcOnState mkFp64NaN()
    }

    if (expr == mkTsNullValue()) {
        return@calcOnState mkFp64(0.0)
    }

    if (expr.sort == boolSort) {
        return@calcOnState boolToFp(expr.asExpr(boolSort))
    }

    // TODO: ToPrimitive, then ToNumber again
    // TODO: probably we need to implement Object (Ref/Fake) -> Number conversion here directly, without ToPrimitive

    error("Unsupported sort: ${expr.sort}")
}
