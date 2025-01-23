package org.usvm.machine

import org.jacodb.ets.base.EtsType
import org.usvm.UBoolExpr

class FakeType(
    val boolTypeExpr: UBoolExpr,
    val fpTypeExpr: UBoolExpr,
    val refTypeExpr: UBoolExpr,
    // TODO string,
) : EtsType {
    override val typeName: String
        get() = "FakeType"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }

    fun mkExactlyOneTypeConstraint(ctx: TSContext): UBoolExpr = with(ctx) {
        return mkAnd(
            mkImplies(boolTypeExpr, fpTypeExpr.not()),
            mkImplies(boolTypeExpr, refTypeExpr.not()),
            mkImplies(fpTypeExpr, refTypeExpr.not()),
            mkOr(boolTypeExpr, fpTypeExpr, refTypeExpr),
        )
    }
}
