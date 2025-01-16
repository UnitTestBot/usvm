package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.cast
import mu.KotlinLogging
import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsArrayLiteral
import org.jacodb.ets.base.EtsAwaitExpr
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
import org.jacodb.ets.base.EtsPtrCallExpr
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
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryPlusExpr
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUnsignedRightShiftExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.base.EtsVoidExpr
import org.jacodb.ets.base.EtsYieldExpr
import org.jacodb.ets.model.EtsMethod
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TSContext
import org.usvm.machine.interpreter.TSStepScope
import org.usvm.machine.operator.TSBinaryOperator
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue

private val logger = KotlinLogging.logger {}

class TSExprResolver(
    private val ctx: TSContext,
    private val scope: TSStepScope,
    localToIdx: (EtsMethod, EtsValue) -> Int,
    localToSort: (EtsMethod, Int) -> USort? = { _, _ -> null },
) : EtsEntity.Visitor<MultiExpr?> {

    private val simpleValueResolver: TSSimpleValueResolver =
        TSSimpleValueResolver(
            ctx,
            scope,
            localToIdx,
            localToSort,
        )

    fun resolveTSExpr(expr: EtsEntity): MultiExpr? {
        return expr.accept(this)
    }

    fun resolveLValue(value: EtsValue): MultiLValue<*> =
        when (value) {
            is EtsParameterRef, is EtsLocal -> simpleValueResolver.resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    private fun resolveBinaryOperator(
        operator: TSBinaryOperator,
        expr: EtsBinaryExpr,
    ): MultiExpr?  = resolveBinaryOperator(operator, expr.left, expr.right)

    private fun resolveBinaryOperator(
        operator: TSBinaryOperator,
        lhv: EtsEntity,
        rhv: EtsEntity,
    ): MultiExpr?  = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        operator.resolve(lhs, rhs, scope)
    }

    private inline fun <T> resolveAfterResolved(
        dependency: EtsEntity,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolveTSExpr(dependency) ?: return null
        return TODO()
    }

    private inline fun resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (MultiExpr, MultiExpr) -> MultiExpr,
    ): MultiExpr? {
        val result0 = resolveTSExpr(dependency0) ?: return null
        val result1 = resolveTSExpr(dependency1) ?: return null

        return block(result0, result1)
    }

    override fun visit(value: EtsLocal): MultiExpr? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsParameterRef): MultiExpr? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsThis): MultiExpr? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsBooleanConstant): MultiExpr? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNumberConstant): MultiExpr? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNullConstant): MultiExpr? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(expr: EtsEqExpr): MultiExpr?  {
        return resolveBinaryOperator(TSBinaryOperator.Eq, expr)
    }

    override fun visit(expr: EtsAddExpr): MultiExpr?  {
        return resolveBinaryOperator(TSBinaryOperator.Add, expr)
    }

    override fun visit(expr: EtsAndExpr): MultiExpr?  {
        return resolveBinaryOperator(TSBinaryOperator.And, expr)
    }

    override fun visit(expr: EtsNotEqExpr): MultiExpr?  {
        return resolveBinaryOperator(TSBinaryOperator.Neq, expr)
    }

    override fun visit(expr: EtsNotExpr): MultiExpr?  {
        return resolveAfterResolved(expr.arg) { arg ->
            // TSUnaryOperator.Not(arg, scope)
            error("")
        }
    }

    override fun visit(value: EtsArrayLiteral): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsObjectLiteral): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsStringConstant): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsUndefinedConstant): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsAwaitExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsBitAndExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsBitNotExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsBitOrExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsBitXorExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsCastExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsCommaExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsDeleteExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsDivExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsExpExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsGtEqExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsGtExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsInExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsInstanceCallExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsInstanceOfExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsLeftShiftExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsLengthExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsLtEqExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsLtExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsMulExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsNegExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsNewArrayExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsNewExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsNullishCoalescingExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsOrExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsPostDecExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsPostIncExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsPreDecExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsPreIncExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsRemExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsRightShiftExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsStaticCallExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsPtrCallExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsStrictEqExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsStrictNotEqExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsSubExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsTernaryExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsTypeOfExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsUnaryPlusExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsVoidExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(expr: EtsYieldExpr): MultiExpr?  {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsArrayAccess): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsInstanceFieldRef): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsStaticFieldRef): MultiExpr?  {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }
}

class TSSimpleValueResolver(
    private val ctx: TSContext,
    private val scope: TSStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
    private val localToSort: (EtsMethod, Int) -> USort? = { _, _ -> null },
) : EtsValue.Visitor<MultiExpr?> {

    @Suppress("UNCHECKED_CAST")
    fun resolveLocal(local: EtsValue): MultiLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localToIdx(method, local)
        val sort = localToSort(method, localIdx) ?: ctx.typeToSort(local.type)
        return when (sort) {
            is UBoolSort -> MultiLValue(boolLValue = URegisterStackLValue(sort, localIdx) as ULValue<*, UBoolSort>)
            is KFp64Sort -> MultiLValue(fpLValue = URegisterStackLValue(sort, localIdx) as ULValue<*, KFp64Sort>)
            is UAddressSort -> MultiLValue(refLValue = URegisterStackLValue(sort, localIdx) as ULValue<*, UAddressSort>)
            is TSUnresolvedSort -> MultiLValue(
                boolLValue = URegisterStackLValue(sort, localIdx) as ULValue<URegisterStackLValue<*>, UBoolSort>,
                fpLValue = URegisterStackLValue(sort, localIdx) as ULValue<URegisterStackLValue<*>, KFp64Sort>,
                refLValue = URegisterStackLValue(sort, localIdx) as ULValue<URegisterStackLValue<*>, UAddressSort>
            )

            else -> error("Unsupported sort $sort")
        }
    }

    fun makeMultiExpr(lValue: MultiLValue<*>, block: ULValue<*, *>.() -> UExpr<out USort>): MultiExpr? {
        return MultiExpr(
            boolValue = lValue.boolLValue?.block()?.cast(),
            fpValue = lValue.fpLValue?.block()?.cast(),
            refValue = lValue.refLValue?.block()?.cast()
        )
    }

    override fun visit(value: EtsLocal): MultiExpr? = with(ctx) {
        val lValue = resolveLocal(value)
        return makeMultiExpr(lValue) {
            scope.calcOnState { memory.read(this@makeMultiExpr) }
        }
    }

    override fun visit(value: EtsParameterRef): MultiExpr? = with(ctx) {
        val lValue = resolveLocal(value)
        return makeMultiExpr(lValue) {
            scope.calcOnState { memory.read(this@makeMultiExpr) }
        }
    }

    override fun visit(value: EtsThis): MultiExpr? = with(ctx) {
        val lValue = resolveLocal(value)
        makeMultiExpr(lValue) {
            scope.calcOnState { memory.read(this@makeMultiExpr) }
        }
    }

    override fun visit(value: EtsBooleanConstant): MultiExpr? = with(ctx) {
        MultiExpr(boolValue = mkBool(value.value))
    }

    override fun visit(value: EtsNumberConstant): MultiExpr? = with(ctx) {
        MultiExpr(fpValue = mkFp64(value.value))
    }

    override fun visit(value: EtsStringConstant): MultiExpr? = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        null
    }

    override fun visit(value: EtsNullConstant): MultiExpr? = with(ctx) {
        MultiExpr(refValue = nullRef)
    }

    override fun visit(value: EtsUndefinedConstant): MultiExpr?  = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsArrayLiteral): MultiExpr?  = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsObjectLiteral): MultiExpr?  = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsArrayAccess): MultiExpr?  = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsInstanceFieldRef): MultiExpr?  = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }

    override fun visit(value: EtsStaticFieldRef): MultiExpr?  = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        return null
    }
}
