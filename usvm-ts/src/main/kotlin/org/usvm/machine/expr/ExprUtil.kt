package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.isFalse
import org.usvm.machine.TSContext
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.types.ExprWithTypeConstraint
import org.usvm.machine.types.FakeType
import org.usvm.types.single

fun TSContext.mkTruthyExpr(expr: UExpr<out USort>, scope: TSStepScope): UBoolExpr = scope.calcOnState {
    if (expr.isFakeObject()) {
        val falseBranchGround = makeSymbolicPrimitive(boolSort)

        val conjuncts = mutableListOf<ExprWithTypeConstraint<UBoolSort>>()
        val possibleType = scope.calcOnState {
            memory.types.getTypeStream(expr.asExpr(addressSort))
        }.single() as FakeType

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
                // TODO how to support undefined here? I guess it's not a case, and it is supposed to be inside of fake type
                expr = mkHeapRefEq(value, nullRef).not()
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

            // TODO add support for both null and undefined values
            addressSort -> mkHeapRefEq(expr.asExpr(addressSort), nullRef).not()

            else -> TODO("Unsupported sort: ${expr.sort}")
        }
    }
}
