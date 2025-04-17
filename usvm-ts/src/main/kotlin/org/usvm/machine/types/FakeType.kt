package org.usvm.machine.types

import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext

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

    fun mkExactlyOneTypeConstraint(ctx: TsContext): UBoolExpr = with(ctx) {
        return mkAnd(
            mkImplies(boolTypeExpr, fpTypeExpr.not()),
            mkImplies(boolTypeExpr, refTypeExpr.not()),
            mkImplies(fpTypeExpr, refTypeExpr.not()),
            mkOr(boolTypeExpr, fpTypeExpr, refTypeExpr),
        )
    }

    companion object {
        fun mkBool(ctx: TsContext): FakeType {
            return FakeType(ctx.mkTrue(), ctx.mkFalse(), ctx.mkFalse())
        }

        fun mkFp(ctx: TsContext): FakeType {
            return FakeType(ctx.mkFalse(), ctx.mkTrue(), ctx.mkFalse())
        }

        fun mkRef(ctx: TsContext): FakeType {
            return FakeType(ctx.mkFalse(), ctx.mkFalse(), ctx.mkTrue())
        }
    }
}

data class ExprWithTypeConstraint<T : USort>(val constraint: UBoolExpr, val expr: UExpr<T>)
