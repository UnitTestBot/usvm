package org.usvm.machine

import io.ksmt.utils.mkConst
import org.jacodb.ets.base.EtsType
import org.usvm.UBoolExpr

class FakeType(
    ctx: TSContext,
    val address: Int,
    val boolTypeExpr: UBoolExpr = ctx.boolSort.mkConst("boolType$address"),
    val fpTypeExpr: UBoolExpr = ctx.boolSort.mkConst("fpType$address"),
    val refTypeExpr: UBoolExpr = ctx.boolSort.mkConst("refType$address")
    // TODO string,
) : EtsType {
    override val typeName: String
        get() = "FakeType"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }

    fun mkAtLeastOneTypeConstraint(ctx: TSContext): UBoolExpr {
        return ctx.mkOr(boolTypeExpr, fpTypeExpr, refTypeExpr)
    }
}