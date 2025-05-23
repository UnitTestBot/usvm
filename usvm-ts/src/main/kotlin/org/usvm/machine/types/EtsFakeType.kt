package org.usvm.machine.types

import org.jacodb.ets.model.EtsType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext

class EtsFakeType(
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
            mkImplies(boolTypeExpr, mkNot(fpTypeExpr)),
            mkImplies(boolTypeExpr, mkNot(refTypeExpr)),
            mkImplies(fpTypeExpr, mkNot(refTypeExpr)),
            mkOr(boolTypeExpr, fpTypeExpr, refTypeExpr),
        )
    }

    companion object {
        fun mkBool(ctx: TsContext): EtsFakeType {
            return EtsFakeType(ctx.mkTrue(), ctx.mkFalse(), ctx.mkFalse())
        }

        fun mkFp(ctx: TsContext): EtsFakeType {
            return EtsFakeType(ctx.mkFalse(), ctx.mkTrue(), ctx.mkFalse())
        }

        fun mkRef(ctx: TsContext): EtsFakeType {
            return EtsFakeType(ctx.mkFalse(), ctx.mkFalse(), ctx.mkTrue())
        }
    }
}

data class ExprWithTypeConstraint<Sort : USort>(
    val constraint: UBoolExpr,
    val expr: UExpr<Sort>,
)
