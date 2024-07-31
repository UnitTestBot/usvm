package org.usvm

import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsBinaryOperation
import org.jacodb.ets.base.EtsBooleanConstant
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsDeleteExpr
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsInstanceCallExpr
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsInstanceOfExpr
import org.jacodb.ets.base.EtsLengthExpr
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNewArrayExpr
import org.jacodb.ets.base.EtsNewExpr
import org.jacodb.ets.base.EtsNullConstant
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsObjectLiteral
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsRelationOperation
import org.jacodb.ets.base.EtsStaticCallExpr
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryOperation
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue

@Suppress("UNUSED_PARAMETER")
class TSExprResolver(
    private val ctx: TSContext,
    private val scope: TSStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsEntity.Visitor<UExpr<out USort>> {

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

    override fun visit(expr: EtsBinaryOperation): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsCastExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLengthExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsRelationOperation): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsUnaryOperation): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsArrayAccess): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsInstanceFieldRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsParameterRef): UExpr<out USort> {
        return simpleValueResolver.visit(ref)
    }

    override fun visit(ref: EtsStaticFieldRef): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsThis): UExpr<out USort> {
        return simpleValueResolver.visit(ref)
    }
}

class TSSimpleValueResolver(
    private val ctx: TSContext,
    private val scope: TSStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsEntity.Visitor<UExpr<out USort>> {

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

    override fun visit(expr: EtsBinaryOperation): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsCastExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsLengthExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsNewExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsRelationOperation): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(expr: EtsUnaryOperation): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsArrayAccess): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsInstanceFieldRef): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsParameterRef): UExpr<out USort> = with(ctx) {
        val lValue = resolveLocal(ref)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(ref: EtsStaticFieldRef): UExpr<out USort> = with(ctx) {
        TODO("Not yet implemented")
    }

    override fun visit(ref: EtsThis): UExpr<out USort> = with(ctx) {
        val lValue = resolveLocal(ref)
        scope.calcOnState { memory.read(lValue) }
    }

    fun resolveLocal(local: EtsValue): URegisterStackLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = ctx.typeToSort(local.type)
        return URegisterStackLValue(sort, localIdx)
    }
}
