package org.usvm.machine

import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.api.*
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue

class PandaExprResolver(
    private val ctx: PandaContext,
    private val scope: PandaStepScope,
    private val localIdxMapper: (PandaMethod, PandaLocal) -> Int,
    private val saveSortInfo: (PandaLocalVar, PandaMethod, USort) -> Unit,
    private val extractSortInfo: (PandaLocalVar, PandaMethod) -> USort
) : PandaExprVisitor<PandaUExprWrapper?> {
    fun resolveLValue(value: PandaValue, alternativeSortInfo: USort? = null): ULValue<*, *>? =
        when (value) {
            is PandaFieldRef -> TODO()
            is PandaArrayAccess -> TODO()
            is PandaLocal -> resolveLocal(value, alternativeSortInfo)
            else -> error("Unexpected value: $value")
        }

    fun resolveLocal(local: PandaLocal, alternativeSortInfo: USort? = null): URegisterStackLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localIdxMapper(method, local)
        val sort = alternativeSortInfo
            ?: (local as? PandaLocalVar)?.let { extractSortInfo(it, method) }
            ?: ctx.typeToSort(local.type)
        return URegisterStackLValue(sort, localIdx)
    }

    // TODO do we need a type?
    fun resolvePandaExpr(expr: PandaExpr): PandaUExprWrapper? = expr.accept(this)

    private fun resolveBinaryOperator(
        operator: PandaBinaryOperator,
        expr: PandaBinaryExpr
    ): UExpr<out USort>? = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        operator(lhs, rhs) // TODO fix issues
    }

    private fun wrap(expr: PandaExpr, wrapper: () -> UExpr<out USort>?) : PandaUExprWrapper? {
        wrapper()?.let {
            return PandaUExprWrapper(expr, it)
        } ?: return null
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: PandaExpr,
        dependency1: PandaExpr,
        block: (PandaUExprWrapper, PandaUExprWrapper) -> T,
    ): T? {
        val result0 = resolvePandaExpr(dependency0) ?: return null
        val result1 = resolvePandaExpr(dependency1) ?: return null
        return block(result0, result1)
    }


    override fun visitCommonCallExpr(expr: CommonExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitCommonInstanceCallExpr(expr: CommonExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonExpr(expr: CommonExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonValue(value: CommonValue): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaAddExpr(expr: PandaAddExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Add, expr)
    }

    override fun visitPandaArgument(expr: PandaArgument): PandaUExprWrapper? = wrap(expr) {
        val ref = resolveLocal(expr)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitPandaArrayAccess(expr: PandaArrayAccess): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCastExpr(expr: PandaCastExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    // TODO: saw Cmp objects in JCBinaryOperator, needs checking
    override fun visitPandaCmpExpr(expr: PandaCmpExpr): PandaUExprWrapper? = wrap(expr) {
        when (expr.cmpOp) {
            PandaCmpOp.GT -> resolveBinaryOperator(PandaBinaryOperator.Gt, expr)
            PandaCmpOp.EQ -> resolveBinaryOperator(PandaBinaryOperator.Eq, expr)
            PandaCmpOp.NE -> resolveBinaryOperator(PandaBinaryOperator.Neq, expr)
            PandaCmpOp.LT -> TODO()
            PandaCmpOp.LE -> TODO()
            PandaCmpOp.GE -> TODO()
        }
    }

    override fun visitPandaCreateEmptyArrayExpr(expr: PandaCreateEmptyArrayExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaDivExpr(expr: PandaDivExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Div, expr)
    }

    override fun visitPandaEqExpr(expr: PandaEqExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Eq, expr)
    }

    override fun visitPandaExpr(expr: PandaExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }


    override fun visitPandaFieldRef(expr: PandaFieldRef): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGeExpr(expr: PandaGeExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGtExpr(expr: PandaGtExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Gt, expr)
    }


    override fun visitPandaLeExpr(expr: PandaLeExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadedValue(expr: PandaLoadedValue): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLocalVar(expr: PandaLocalVar): PandaUExprWrapper? = wrap(expr) {
        val ref = resolveLocal(expr, alternativeSortInfo = null)
        scope.calcOnState { memory.read(ref) }
    }

    override fun visitPandaLtExpr(expr: PandaLtExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaMulExpr(expr: PandaMulExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Mul, expr)
    }

    override fun visitPandaNegExpr(expr: PandaNegExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNeqExpr(expr: PandaNeqExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Neq, expr)
    }


    override fun visitPandaNewExpr(expr: PandaNewExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNullConstant(expr: PandaNullConstant): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaBoolConstant(expr: PandaBoolConstant): PandaUExprWrapper? = wrap(expr) {
        ctx.mkBool(expr.value)
    }

    override fun visitPandaNumberConstant(expr: PandaNumberConstant): PandaUExprWrapper? = wrap(expr) {
        ctx.mkFp64(expr.value.toDouble())
    }

    override fun visitPandaStaticCallExpr(expr: PandaStaticCallExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStrictEqExpr(expr: PandaStrictEqExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStringConstant(expr: PandaStringConstant): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaSubExpr(expr: PandaSubExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Sub, expr)
    }

    override fun visitPandaTODOConstant(expr: TODOConstant): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaThis(expr: PandaThis): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaToNumericExpr(expr: PandaToNumericExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaTypeofExpr(expr: PandaTypeofExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaUndefinedConstant(expr: PandaUndefinedConstant): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitTODOExpr(expr: TODOExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }
}
