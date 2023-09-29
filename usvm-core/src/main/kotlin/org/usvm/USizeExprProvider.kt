package org.usvm

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KInt32NumExpr
import io.ksmt.sort.KIntSort

/**
 * Provides operations with a configurable size sort.
 */
interface USizeExprProvider<USizeSort : USort> {
    val ctx: UContext<*>
    val sizeSort: USizeSort

    fun mkSizeExpr(size: Int): UExpr<USizeSort>
    fun getIntValue(expr: UExpr<USizeSort>): Int?

    fun mkSizeSubExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UExpr<USizeSort>
    fun mkSizeAddExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UExpr<USizeSort>
    fun mkSizeGtExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
    fun mkSizeGeExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
    fun mkSizeLtExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
    fun mkSizeLeExpr(lhs: UExpr<USizeSort>, rhs: UExpr<USizeSort>): UBoolExpr
}

class UBv32SizeExprProvider(
    override val ctx: UContext<*>
) : USizeExprProvider<UBv32Sort> {
    override val sizeSort: UBv32Sort = ctx.bv32Sort

    override fun mkSizeExpr(size: Int): UExpr<UBv32Sort> = ctx.mkBv(size)
    override fun getIntValue(expr: UExpr<UBv32Sort>): Int? = (expr as? KBitVec32Value)?.numberValue

    override fun mkSizeSubExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UExpr<UBv32Sort> = ctx.mkBvSubExpr(lhs, rhs)
    override fun mkSizeAddExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UExpr<UBv32Sort> = ctx.mkBvAddExpr(lhs, rhs)
    override fun mkSizeGtExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr = ctx.mkBvSignedGreaterExpr(lhs, rhs)
    override fun mkSizeGeExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr = ctx.mkBvSignedGreaterOrEqualExpr(lhs, rhs)
    override fun mkSizeLtExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr = ctx.mkBvSignedLessExpr(lhs, rhs)
    override fun mkSizeLeExpr(lhs: UExpr<UBv32Sort>, rhs: UExpr<UBv32Sort>): UBoolExpr = ctx.mkBvSignedLessOrEqualExpr(lhs, rhs)
}

class UInt32SizeExprProvider(
    override val ctx: UContext<*>
) : USizeExprProvider<KIntSort> {
    override val sizeSort: KIntSort = ctx.intSort

    override fun mkSizeExpr(size: Int): UExpr<KIntSort> = ctx.mkIntNum(size)
    override fun getIntValue(expr: UExpr<KIntSort>): Int? = (expr as? KInt32NumExpr)?.value

    override fun mkSizeSubExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UExpr<KIntSort> = ctx.mkArithSub(lhs, rhs)
    override fun mkSizeAddExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UExpr<KIntSort> = ctx.mkArithAdd(lhs, rhs)
    override fun mkSizeGtExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = ctx.mkArithGt(lhs, rhs)
    override fun mkSizeGeExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = ctx.mkArithGe(lhs, rhs)
    override fun mkSizeLtExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = ctx.mkArithLt(lhs, rhs)
    override fun mkSizeLeExpr(lhs: UExpr<KIntSort>, rhs: UExpr<KIntSort>): UBoolExpr = ctx.mkArithLe(lhs, rhs)
}
