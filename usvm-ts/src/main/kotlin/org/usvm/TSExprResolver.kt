package org.usvm

import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsBitAndExpr
import org.jacodb.ets.base.EtsBitNotExpr
import org.jacodb.ets.base.EtsBitOrExpr
import org.jacodb.ets.base.EtsBitXorExpr
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsCommaExpr
import org.jacodb.ets.base.EtsDeleteExpr
import org.jacodb.ets.base.EtsDivExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsEqExpr
import org.jacodb.ets.base.EtsExpExpr
import org.jacodb.ets.base.EtsGtEqExpr
import org.jacodb.ets.base.EtsGtExpr
import org.jacodb.ets.base.EtsInExpr
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLeftShiftExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsLtEqExpr
import org.jacodb.ets.base.EtsLtExpr
import org.jacodb.ets.base.EtsMulExpr
import org.jacodb.ets.base.EtsNegExpr
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNotEqExpr
import org.jacodb.ets.base.EtsNotExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNullishCoalescingExpr
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsObjectLiteral
import org.jacodb.ets.base.EtsOrExpr
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsPostDecExpr
import org.jacodb.ets.base.EtsPostIncExpr
import org.jacodb.ets.base.EtsPreDecExpr
import org.jacodb.ets.base.EtsPreIncExpr
import org.jacodb.ets.base.EtsRemExpr
import org.jacodb.ets.base.EtsRightShiftExpr
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStrictEqExpr
import org.jacodb.ets.base.EtsStrictNotEqExpr
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsSubExpr
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryPlusExpr
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUnsignedRightShiftExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.base.EtsVoidExpr
import org.jacodb.ets.model.EtsMethod
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue

@Suppress("UNUSED_PARAMETER")
class TSExprResolver(
    private val ctx: TSContext,
    private val scope: TSStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsEntity.Visitor<UExpr<out USort>?> {

    val simpleValueResolver: TSSimpleValueResolver = TSSimpleValueResolver(
        ctx,
        scope,
        localToIdx
    )

    fun resolveTSExpr(expr: EtsEntity, type: EtsType = expr.type): UExpr<out USort>? {
        return expr.accept(this)
    }

    fun resolveLValue(value: EtsValue): ULValue<*, *>? =
        when (value) {
            is EtsParameterRef,
            is EtsLocal -> simpleValueResolver.resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    private fun resolveBinaryOperator(
        operator: TSBinaryOperator,
        expr: EtsBinaryExpr,
    ): UExpr<out USort>? = resolveBinaryOperator(operator, expr.left, expr.right)

    private fun resolveBinaryOperator(
        operator: TSBinaryOperator,
        lhv: EtsEntity,
        rhv: EtsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        operator(lhs, rhs)
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolveTSExpr(dependency0) ?: return null
        val result1 = resolveTSExpr(dependency1) ?: return null
        return block(result0, result1)
    }



    override fun visit(value: EtsLocal): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsArrayLiteral): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsObjectLiteral): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsAddExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsAndExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsBitAndExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsBitNotExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsBitOrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsBitXorExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsCastExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsCommaExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsDivExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TSBinaryOperator.Eq, expr)
    }

    override fun visit(expr: EtsExpExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsGtEqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsGtExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLeftShiftExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLengthExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLtEqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLtExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsMulExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNegExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNotEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TSBinaryOperator.Neq, expr)
    }

    override fun visit(expr: EtsNotExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNullishCoalescingExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsOrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsPostDecExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsPostIncExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsPreDecExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsPreIncExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsRemExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsRightShiftExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsStrictEqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsStrictNotEqExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsSubExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsTernaryExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsUnaryPlusExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsVoidExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsArrayAccess): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsThis): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }
}

class TSSimpleValueResolver(
    private val ctx: TSContext,
    private val scope: TSStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsValue.Visitor<UExpr<out USort>?> {

    override fun visit(value: EtsLocal): UExpr<out USort> = with(ctx) {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsArrayLiteral): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> = with(ctx) {
        nullRef
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> = with(ctx) {
        mkFp64(value.value)
    }

    override fun visit(value: EtsObjectLiteral): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsArrayAccess): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> = with(ctx) {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(value: EtsThis): UExpr<out USort> = with(ctx) {
        val lValue = resolveLocal(value)
        scope.calcOnState { memory.read(lValue) }
    }

    fun resolveLocal(local: EtsValue): URegisterStackLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = ctx.typeToSort(local.type)
        return URegisterStackLValue(sort, localIdx)
    }
}
