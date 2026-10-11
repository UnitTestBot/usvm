package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope

/** Recognizes the Number, Boolean, null and undefined conversions modeled without ToPrimitive or StringToNumber. */
fun TsContext.mkNumberPrimitiveConstraint(expr: UExpr<out USort>, scope: TsStepScope): UBoolExpr {
    if (expr.isFakeObject()) {
        val type = expr.getFakeType(scope)
        val reference = expr.extractRef(scope)
        val nullishReference = mkIsNullOrUndefined(reference)
        val supportedReference = mkAnd(type.refTypeExpr, nullishReference)

        return mkOr(type.fpTypeExpr, type.boolTypeExpr, supportedReference)
    }

    return when (expr.sort) {
        fp64Sort, boolSort -> trueExpr
        addressSort -> mkIsNullOrUndefined(expr.asExpr(addressSort))
        else -> falseExpr
    }
}

/** Converts modeled primitives and retains unsupported conversion alternatives as explicit analysis outcomes. */
fun TsContext.mkNumberPrimitiveExpr(
    expr: UExpr<out USort>,
    scope: TsStepScope,
    unsupportedReason: String,
): UExpr<KFp64Sort>? {
    val supported = mkNumberPrimitiveConstraint(expr, scope)
    if (supported.isFalse) {
        throw UnsupportedOperationException(unsupportedReason)
    }

    scope.fork(supported, blockOnFalseState = {
        terminateAsUnsupported(reason = unsupportedReason)
    }) ?: return null

    // The guard excludes every reference conversion for which mkNumericExpr currently has only a placeholder.
    return mkNumericExpr(expr, scope)
}
