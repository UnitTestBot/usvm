package org.usvm.machine.types

import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsContext
import org.usvm.model.TsType

class FakeType(
    val boolTypeExpr: UBoolExpr,
    val fpTypeExpr: UBoolExpr,
    val refTypeExpr: UBoolExpr,
    // TODO string,
) : TsType {
    override val typeName: String
        get() = "FakeType"

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
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
        fun fromBool(ctx: TsContext): FakeType {
            return FakeType(ctx.mkTrue(), ctx.mkFalse(), ctx.mkFalse())
        }

        fun fromFp(ctx: TsContext): FakeType {
            return FakeType(ctx.mkFalse(), ctx.mkTrue(), ctx.mkFalse())
        }

        fun fromRef(ctx: TsContext): FakeType {
            return FakeType(ctx.mkFalse(), ctx.mkFalse(), ctx.mkTrue())
        }
    }
}

data class ExprWithTypeConstraint<T : USort>(
    val constraint: UBoolExpr,
    val expr: UExpr<T>,
)
