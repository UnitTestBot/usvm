package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaCastExpr
import org.jacodb.panda.dynamic.api.PandaCmpExpr
import org.jacodb.panda.dynamic.api.PandaCmpOp
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
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaStaticCallExpr
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaUndefinedConstant
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaVirtualCallExpr
import org.jacodb.panda.dynamic.api.TODOConstant
import org.jacodb.panda.dynamic.api.TODOExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaCastExpr
import org.jacodb.panda.dynamic.api.PandaCmpExpr
import org.jacodb.panda.dynamic.api.PandaCmpOp
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

@Suppress("unused")
class PandaExprResolver(
    private val ctx: PandaContext,
    private val scope: PandaStepScope,
    private val localIdxMapper: (PandaMethod, PandaLocal) -> Int,
) : PandaExprVisitor<PandaUExprWrapper?> {
    fun resolveLValue(value: PandaValue): ULValue<*, *>? =
        when (value) {
            is PandaFieldRef -> TODO()
            is PandaArrayAccess -> TODO()
            is PandaLocal -> resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    fun resolveLocal(local: PandaLocal): URegisterStackLValue<*> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localIdxMapper(method, local)
        val sort = ctx.addressSort // TODO ?????
         return URegisterStackLValue(sort, localIdx)
    }

    // TODO do we need a type?
    fun resolvePandaExpr(expr: PandaExpr): PandaUExprWrapper? = expr.accept(this)

    private fun resolveBinaryOperator(
        operator: PandaBinaryOperator,
        expr: PandaBinaryExpr
    ): UExpr<out USort>? = resolveAfterResolved(expr.lhv, expr.rhv) { lhs, rhs ->
        val cornerTypes = mutableListOf<PandaType>(PandaNumberType, PandaBoolType)

        val conditions = scope.calcOnState {
            val lhsRef = lhs.asExpr(ctx.addressSort)
            val rhsRef = rhs.asExpr(ctx.addressSort)
            cornerTypes.flatMap { fstType ->
                cornerTypes.map { sndType ->
                    ctx.mkAnd(
                        memory.types.evalIsSubtype(lhsRef, fstType),
                        memory.types.evalIsSubtype(rhsRef, sndType)
                    ) to {
                        operator(lhs, rhs)
                        newStmt(TODO())
                    } // todo cast bool to int
                }
            }
        }

        val actions = conditions.map {

        }

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
        val ref = resolveLocal(expr)
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