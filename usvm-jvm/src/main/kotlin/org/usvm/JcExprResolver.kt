package org.usvm

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.cfg.JcAddExpr
import org.jacodb.api.cfg.JcAndExpr
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcBool
import org.jacodb.api.cfg.JcByte
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcChar
import org.jacodb.api.cfg.JcClassConstant
import org.jacodb.api.cfg.JcCmpExpr
import org.jacodb.api.cfg.JcCmpgExpr
import org.jacodb.api.cfg.JcCmplExpr
import org.jacodb.api.cfg.JcDivExpr
import org.jacodb.api.cfg.JcDouble
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcExprVisitor
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcFloat
import org.jacodb.api.cfg.JcGeExpr
import org.jacodb.api.cfg.JcGtExpr
import org.jacodb.api.cfg.JcInstanceOfExpr
import org.jacodb.api.cfg.JcInt
import org.jacodb.api.cfg.JcLambdaExpr
import org.jacodb.api.cfg.JcLeExpr
import org.jacodb.api.cfg.JcLengthExpr
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcLong
import org.jacodb.api.cfg.JcLtExpr
import org.jacodb.api.cfg.JcMethodConstant
import org.jacodb.api.cfg.JcMulExpr
import org.jacodb.api.cfg.JcNegExpr
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcOrExpr
import org.jacodb.api.cfg.JcPhiExpr
import org.jacodb.api.cfg.JcRemExpr
import org.jacodb.api.cfg.JcShlExpr
import org.jacodb.api.cfg.JcShort
import org.jacodb.api.cfg.JcShrExpr
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcSubExpr
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcUshrExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.cfg.JcXorExpr
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.short

@Suppress("UNUSED_PARAMETER")
class JcExprResolver(
    private val cp: JcClasspath,
    private val scope: JcStepScope,
    private val hardMaxArrayLength: Int = 1_500,
) : JcExprVisitor<UExpr<out USort>?> {
    fun resolveExpr(value: JcExpr): UExpr<out USort>? {
        return value.accept(this)
    }

    fun resolveLValue(value: JcValue): ULValue? {
        TODO()
    }

    override fun visitExternalJcExpr(value: JcExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcAddExpr(expr: JcAddExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Add(lhs, rhs) }

    override fun visitJcAndExpr(expr: JcAndExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.And(lhs, rhs) }

    override fun visitJcArgument(value: JcArgument): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcArrayAccess(value: JcArrayAccess): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcBool(value: JcBool): UExpr<out USort> = with(scope.uctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcByte(value: JcByte): UExpr<out USort> = with(scope.uctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcCastExpr(expr: JcCastExpr): UExpr<out USort> = with(scope.uctx) {
        when (expr.type) {
            cp.boolean -> TODO()
            cp.short -> TODO()
            cp.int -> TODO()
            cp.long -> TODO()
            cp.float -> TODO()
            cp.double -> TODO()
            cp.byte -> TODO()
            cp.char -> TODO()
            is JcRefType -> TODO()
            else -> error("unexpected cast expression: $expr")
        }
    }

    override fun visitJcChar(value: JcChar): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcClassConstant(value: JcClassConstant): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcCmpExpr(expr: JcCmpExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcCmpgExpr(expr: JcCmpgExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcCmplExpr(expr: JcCmplExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcDivExpr(expr: JcDivExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Div(lhs, rhs) }

    override fun visitJcDouble(value: JcDouble): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcEqExpr(expr: JcEqExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Eq(lhs, rhs) }

    override fun visitJcFieldRef(value: JcFieldRef): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcFloat(value: JcFloat): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcGeExpr(expr: JcGeExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Ge(lhs, rhs) }

    override fun visitJcGtExpr(expr: JcGtExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Gt(lhs, rhs) }


    override fun visitJcInstanceOfExpr(expr: JcInstanceOfExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcInt(value: JcInt): UExpr<out USort> = with(scope.uctx) {
        mkBv(value.value)
    }

    override fun visitJcLambdaExpr(expr: JcLambdaExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcLeExpr(expr: JcLeExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Le(lhs, rhs) }

    override fun visitJcLengthExpr(expr: JcLengthExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcLocalVar(value: JcLocalVar): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcLong(value: JcLong): UExpr<out USort> = with(scope.uctx) {
        mkBv(value.value)
    }

    override fun visitJcLtExpr(expr: JcLtExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Lt(lhs, rhs) }

    override fun visitJcMethodConstant(value: JcMethodConstant): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcMulExpr(expr: JcMulExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Mul(lhs, rhs) }

    override fun visitJcNegExpr(expr: JcNegExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.operand) { operand -> JcUnaryOperator.Neg(operand) }

    override fun visitJcNeqExpr(expr: JcNeqExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Neq(lhs, rhs) }

    override fun visitJcNewArrayExpr(expr: JcNewArrayExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcNewExpr(expr: JcNewExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcNullConstant(value: JcNullConstant): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcOrExpr(expr: JcOrExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Or(lhs, rhs) }

    override fun visitJcPhiExpr(value: JcPhiExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcRemExpr(expr: JcRemExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Rem(lhs, rhs) }

    override fun visitJcShlExpr(expr: JcShlExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcShort(value: JcShort): UExpr<out USort> = with(scope.uctx) {
        mkBv(value.value, bv32Sort)
    }

    override fun visitJcShrExpr(expr: JcShrExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcStringConstant(value: JcStringConstant): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcSubExpr(expr: JcSubExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Sub(lhs, rhs) }

    override fun visitJcThis(value: JcThis): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcUshrExpr(expr: JcUshrExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): UExpr<out USort> = with(scope.uctx) {
        TODO("Not yet implemented")
    }

    override fun visitJcXorExpr(expr: JcXorExpr): UExpr<out USort>? =
        resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs -> JcBinOperator.Xor(lhs, rhs) }

    private fun resolveAfterResolved(
        dependency0: JcExpr,
        block: (UExpr<out USort>) -> UExpr<out USort>?,
    ): UExpr<out USort>? {
        val result0 = resolveExpr(dependency0) ?: return null
        return block(result0)
    }

    private fun resolveAfterResolved(
        dependency0: JcExpr,
        dependency1: JcExpr,
        block: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>?,
    ): UExpr<out USort>? {
        val result0 = resolveExpr(dependency0) ?: return null
        val result1 = resolveExpr(dependency0) ?: return null
        return block(result0, result1)
    }

    private fun resolveAfterResolved(
        dependencies: List<JcExpr>,
        block: (List<UExpr<out USort>>) -> UExpr<out USort>?,
    ): UExpr<out USort>? {
        val results = dependencies.map { resolveExpr(it) ?: return null }
        return block(results)
    }
}