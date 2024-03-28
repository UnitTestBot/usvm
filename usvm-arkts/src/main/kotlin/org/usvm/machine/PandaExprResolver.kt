package org.usvm.machine

import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaCastExpr
import org.jacodb.panda.dynamic.api.PandaCmpExpr
import org.jacodb.panda.dynamic.api.PandaCreateEmptyArrayExpr
import org.jacodb.panda.dynamic.api.PandaDivExpr
import org.jacodb.panda.dynamic.api.PandaEqExpr
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaExprVisitor
import org.jacodb.panda.dynamic.api.PandaFieldRef
import org.jacodb.panda.dynamic.api.PandaGeExpr
import org.jacodb.panda.dynamic.api.PandaGtExpr
import org.jacodb.panda.dynamic.api.PandaLeExpr
import org.jacodb.panda.dynamic.api.PandaLoadedValue
import org.jacodb.panda.dynamic.api.PandaLocal
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaLtExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMulExpr
import org.jacodb.panda.dynamic.api.PandaNeqExpr
import org.jacodb.panda.dynamic.api.PandaNewExpr
import org.jacodb.panda.dynamic.api.PandaNullConstant
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaStaticCallExpr
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaUndefinedConstant
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaVirtualCallExpr
import org.jacodb.panda.dynamic.api.TODOConstant
import org.jacodb.panda.dynamic.api.TODOExpr
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
) : PandaExprVisitor<UExpr<out USort>?> {
    fun resolveLValue(value: PandaValue, alternativeSortInfo: USort? = null): ULValue<*, *>? =
        when (value) {
            is PandaFieldRef -> TODO()
            is PandaArrayAccess -> TODO()
            is PandaLocal -> resolveLocal(value, alternativeSortInfo)
            else -> error("Unexpected value: $value")
        }

    fun resolveLocal(local: PandaLocal, alternativeSortInfo: USort?): URegisterStackLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localIdxMapper(method, local)
        val sort = alternativeSortInfo
            ?: (local as? PandaLocalVar)?.let { extractSortInfo(it, method) }
            ?: ctx.typeToSort(local.type)
        return URegisterStackLValue(sort, localIdx)
    }

    // TODO do we need a type?
    fun resolvePandaExpr(expr: PandaExpr): UExpr<out USort>? = expr.accept(this)

    private fun resolveBinaryOperator(
        operator: PandaBinaryOperator,
        expr: PandaBinaryExpr
    ): UExpr<out USort>? = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        operator(lhs, rhs) // TODO fix issues
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: PandaExpr,
        dependency1: PandaExpr,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolvePandaExpr(dependency0) ?: return null
        val result1 = resolvePandaExpr(dependency1) ?: return null
        return block(result0, result1)
    }


    override fun visitCommonCallExpr(expr: CommonExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitCommonInstanceCallExpr(expr: CommonExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonExpr(expr: CommonExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitExternalCommonValue(value: CommonValue): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaAddExpr(expr: PandaAddExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Add, expr)

    override fun visitPandaArgument(expr: PandaArgument): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaArrayAccess(expr: PandaArrayAccess): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCastExpr(expr: PandaCastExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCmpExpr(expr: PandaCmpExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCreateEmptyArrayExpr(expr: PandaCreateEmptyArrayExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaDivExpr(expr: PandaDivExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaEqExpr(expr: PandaEqExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaFieldRef(expr: PandaFieldRef): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGeExpr(expr: PandaGeExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGtExpr(expr: PandaGtExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLeExpr(expr: PandaLeExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadedValue(expr: PandaLoadedValue): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLocalVar(expr: PandaLocalVar): UExpr<out USort> {
        val ref = resolveLocal(expr, alternativeSortInfo = null)
        return scope.calcOnState { memory.read(ref) }
    }

    override fun visitPandaLtExpr(expr: PandaLtExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaMulExpr(expr: PandaMulExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNeqExpr(expr: PandaNeqExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNewExpr(expr: PandaNewExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNullConstant(expr: PandaNullConstant): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNumberConstant(expr: PandaNumberConstant): UExpr<out USort> = with(ctx) {
        mkFp64(expr.value.toDouble())
    }

    override fun visitPandaStaticCallExpr(expr: PandaStaticCallExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStrictEqExpr(expr: PandaStrictEqExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStringConstant(expr: PandaStringConstant): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaSubExpr(expr: PandaSubExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaTODOConstant(expr: TODOConstant): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaThis(expr: PandaThis): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaToNumericExpr(expr: PandaToNumericExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaTypeofExpr(expr: PandaTypeofExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaUndefinedConstant(expr: PandaUndefinedConstant): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitTODOExpr(expr: TODOExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }
}
