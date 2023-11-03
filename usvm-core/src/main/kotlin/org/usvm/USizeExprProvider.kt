package org.usvm

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KInt32NumExpr
import io.ksmt.sort.KIntSort

/**
 * Provides operations with a configurable size sort.
 */
interface USizeExprProvider<USizeSort : USort> {
    fun getSizeSort(ctx: UContext<USizeSort>): USizeSort

    fun mkSizeExpr(ctx: UContext<USizeSort>, size: Int): UExpr<USizeSort>
    fun getIntValue(expr: UExpr<USizeSort>): Int?

    fun mkSizeSubExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UExpr<USizeSort>
    fun mkSizeAddExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UExpr<USizeSort>
    fun mkSizeGtExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
    fun mkSizeGeExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
    fun mkSizeLtExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
    fun mkSizeLeExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
}

object UBv32SizeExprProvider : USizeExprProvider<UBv32Sort> {
    override fun getSizeSort(ctx: UContext<UBv32Sort>): UBv32Sort = ctx.bv32Sort

    override fun mkSizeExpr(ctx: UContext<UBv32Sort>, size: Int): UExpr<UBv32Sort> = ctx.mkBv(size)
    override fun getIntValue(expr: UExpr<UBv32Sort>): Int? = (expr as? KBitVec32Value)?.numberValue

    override fun mkSizeSubExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UExpr<UBv32Sort> =
        lhs.ctx.mkBvSubExpr(lhs, rhs)

    override fun mkSizeAddExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UExpr<UBv32Sort> =
        lhs.ctx.mkBvAddExpr(lhs, rhs)

    override fun mkSizeGtExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr =
        lhs.ctx.mkBvSignedGreaterExpr(lhs, rhs)

    override fun mkSizeGeExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr =
        lhs.ctx.mkBvSignedGreaterOrEqualExpr(lhs, rhs)

    override fun mkSizeLtExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr =
        lhs.ctx.mkBvSignedLessExpr(lhs, rhs)

    override fun mkSizeLeExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr =
        lhs.ctx.mkBvSignedLessOrEqualExpr(lhs, rhs)
}

object UInt32SizeExprProvider : USizeExprProvider<KIntSort> {
    override fun getSizeSort(ctx: UContext<KIntSort>): KIntSort = ctx.intSort

    override fun mkSizeExpr(ctx: UContext<KIntSort>, size: Int): UExpr<KIntSort> = ctx.mkIntNum(size)
    override fun getIntValue(expr: UExpr<KIntSort>): Int? = (expr as? KInt32NumExpr)?.value

    override fun mkSizeSubExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UExpr<KIntSort> = lhs.ctx.mkArithSub(lhs, rhs)
    override fun mkSizeAddExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UExpr<KIntSort> = lhs.ctx.mkArithAdd(lhs, rhs)
    override fun mkSizeGtExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = lhs.ctx.mkArithGt(lhs, rhs)
    override fun mkSizeGeExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = lhs.ctx.mkArithGe(lhs, rhs)
    override fun mkSizeLtExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = lhs.ctx.mkArithLt(lhs, rhs)
    override fun mkSizeLeExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = lhs.ctx.mkArithLe(lhs, rhs)
}
