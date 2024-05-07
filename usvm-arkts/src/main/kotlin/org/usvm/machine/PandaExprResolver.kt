package org.usvm.machine

import io.ksmt.expr.KInterpretedValue
import io.ksmt.utils.asExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaCastExpr
import org.jacodb.panda.dynamic.api.PandaCaughtError
import org.jacodb.panda.dynamic.api.PandaCmpExpr
import org.jacodb.panda.dynamic.api.PandaCmpOp
import org.jacodb.panda.dynamic.api.PandaCreateEmptyArrayExpr
import org.jacodb.panda.dynamic.api.PandaDivExpr
import org.jacodb.panda.dynamic.api.PandaEqExpr
import org.jacodb.panda.dynamic.api.PandaExpExpr
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaExprVisitor
import org.jacodb.panda.dynamic.api.PandaField
import org.jacodb.panda.dynamic.api.PandaFieldRef
import org.jacodb.panda.dynamic.api.PandaGeExpr
import org.jacodb.panda.dynamic.api.PandaGtExpr
import org.jacodb.panda.dynamic.api.PandaLeExpr
import org.jacodb.panda.dynamic.api.PandaLengthExpr
import org.jacodb.panda.dynamic.api.PandaLoadedValue
import org.jacodb.panda.dynamic.api.PandaLocal
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaLtExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMulExpr
import org.jacodb.panda.dynamic.api.PandaNegExpr
import org.jacodb.panda.dynamic.api.PandaNeqExpr
import org.jacodb.panda.dynamic.api.PandaNewExpr
import org.jacodb.panda.dynamic.api.PandaNullConstant
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaPhiValue
import org.jacodb.panda.dynamic.api.PandaStaticCallExpr
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaTypeName
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaUndefinedConstant
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaVirtualCallExpr
import org.jacodb.panda.dynamic.api.TODOConstant
import org.jacodb.panda.dynamic.api.TODOExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue

@Suppress("unused")
class PandaExprResolver(
    private val ctx: PandaContext,
    private val scope: PandaStepScope,
    private val localIdxMapper: (PandaMethod, PandaLocal) -> Int,
    private val prevBBId: Int
) : PandaExprVisitor<UExpr<out USort>?> {
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
    fun resolvePandaExpr(expr: PandaExpr): UExpr<out USort>? {
        return expr.accept(this)
    }

    private fun resolveBinaryOperator(
        operator: PandaBinaryOperator,
        lhv: PandaValue,
        rhv: PandaValue,
    ): UExpr<out USort>? = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        operator(lhs, rhs, scope)
    }

    private fun resolveBinaryOperator(
        operator: PandaBinaryOperator,
        expr: PandaBinaryExpr,
    ): UExpr<out USort>? = resolveBinaryOperator(operator, expr.lhv, expr.rhv)

    private inline fun <T> resolveAfterResolved(
        dependency0: PandaExpr,
        dependency1: PandaExpr,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolvePandaExpr(dependency0) ?: return null
        val result1 = resolvePandaExpr(dependency1) ?: return null
        return block(result0, result1)
    }

    override fun visitPandaExpr(expr: PandaExpr): UExpr<out USort>? {
        return resolvePandaExpr(expr)
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
        val ref = resolveLocal(expr)
        return scope.calcOnState { memory.read(ref) }
    }

    override fun visitPandaArrayAccess(expr: PandaArrayAccess): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCastExpr(expr: PandaCastExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCaughtError(expr: PandaCaughtError): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    // TODO: saw Cmp objects in JCBinaryOperator, needs checking
    override fun visitPandaCmpExpr(expr: PandaCmpExpr): UExpr<out USort>? =
        when (expr.cmpOp) {
            PandaCmpOp.GT -> resolveBinaryOperator(PandaBinaryOperator.Gt, expr)
            PandaCmpOp.EQ -> resolveBinaryOperator(PandaBinaryOperator.Eq, expr)
            PandaCmpOp.NE -> resolveBinaryOperator(PandaBinaryOperator.Neq, expr)
            PandaCmpOp.LT -> TODO()
            PandaCmpOp.LE -> TODO()
            PandaCmpOp.GE -> TODO()
        }

    override fun visitPandaCreateEmptyArrayExpr(expr: PandaCreateEmptyArrayExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaDivExpr(expr: PandaDivExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Div, expr)

    override fun visitPandaEqExpr(expr: PandaEqExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Eq, expr)

    override fun visitPandaExpExpr(expr: PandaExpExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaFieldRef(expr: PandaFieldRef): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGeExpr(expr: PandaGeExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGtExpr(expr: PandaGtExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Gt, expr)


    override fun visitPandaLeExpr(expr: PandaLeExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLengthExpr(expr: PandaLengthExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaLoadedValue(expr: PandaLoadedValue): UExpr<out USort>? {
        val instance = resolvePandaExpr(expr.instance) ?: return null
        // TODO this is field reading for now only
        val fieldReading = UFieldLValue(
            ctx.addressSort,
            instance.asExpr(ctx.addressSort),
            PandaField(
                name = expr.property,
                type = PandaTypeName(PandaAnyType.typeName),
                signature = null // TODO ?????
            )
        )

        return scope.calcOnState { memory.read(fieldReading) }
    }

    override fun visitPandaLocalVar(expr: PandaLocalVar): UExpr<out USort>? {
        val ref = resolveLocal(expr)
        return scope.calcOnState { memory.read(ref) }
    }

    override fun visitPandaLtExpr(expr: PandaLtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(PandaBinaryOperator.Lt, expr)
    }

    override fun visitPandaMulExpr(expr: PandaMulExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Mul, expr)

    override fun visitPandaNegExpr(expr: PandaNegExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNeqExpr(expr: PandaNeqExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Neq, expr)


    override fun visitPandaNewExpr(expr: PandaNewExpr): UExpr<out USort>? {
        val address = scope.calcOnState { memory.allocConcrete(expr.type) }
        return address
    }

    override fun visitPandaNullConstant(expr: PandaNullConstant): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaBoolConstant(expr: PandaBoolConstant): UExpr<out USort>? =
        ctx.mkBool(expr.value)

    override fun visitPandaNumberConstant(expr: PandaNumberConstant): UExpr<out USort>? =
        ctx.mkFp64(expr.value.toDouble())

    override fun visitPandaPhiValue(expr: PandaPhiValue): UExpr<out USort>? {
        val value = expr.valueFromBB(prevBBId)
        return resolvePandaExpr(value)
    }

    override fun visitPandaStaticCallExpr(expr: PandaStaticCallExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStrictEqExpr(expr: PandaStrictEqExpr): UExpr<out USort>? = with(ctx) {
        val lhs = resolvePandaExpr(expr.lhv) ?: return null
        val rhs = resolvePandaExpr(expr.rhv) ?: return null

        if (lhs is KInterpretedValue && rhs is KInterpretedValue) {
            if (lhs.sort != rhs.sort) {
                return ctx.falseExpr
            }

            return scope.calcOnState {
                memory.wrapField(ctx.mkEq(lhs.asExpr(lhs.sort), rhs.asExpr(lhs.sort)), PandaBoolType)
            }
        }

        if (lhs is KInterpretedValue) {
            TODO()
        }

        if (rhs is KInterpretedValue) {
            return when (rhs.sort) {
                fp64Sort -> {
                    val value = scope.calcOnState {
                        val lvalue = constructAuxiliaryFieldLValue(lhs.asExpr(addressSort), fp64Sort)
                        memory.read(lvalue)
                    }

                    scope.calcOnState {
                        val equalityValue = mkEq(value.asExpr(value.sort), rhs.asExpr(value.sort))
                        memory.wrapField(equalityValue, PandaBoolType)
                    }
                }

                boolSort -> TODO()
                stringSort -> TODO()
                else -> TODO()
            }
        }

        TODO()
    }

    override fun visitPandaStringConstant(expr: PandaStringConstant): UExpr<out USort>? {
        val address = scope.calcOnState { memory.allocConcrete(PandaStringType) }
        val lValue = ctx.constructAuxiliaryFieldLValue(address, ctx.stringSort)
        val value = PandaConcreteString(ctx, expr.value)
        scope.doWithState { memory.write(lValue, value) }

        return value
    }

    override fun visitPandaSubExpr(expr: PandaSubExpr): UExpr<out USort>? =
        resolveBinaryOperator(PandaBinaryOperator.Sub, expr)

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

    // TODO: FIX!!!
    override fun visitPandaUndefinedConstant(expr: PandaUndefinedConstant): UExpr<out USort>? {
        return ctx.mkFp64(0.0)
    }

    override fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    override fun visitTODOExpr(expr: TODOExpr): UExpr<out USort>? {
        TODO("Not yet implemented")
    }

    private fun PandaBinaryExpr.operator(): PandaBinaryOperator = when (this) {
        is PandaAddExpr -> PandaBinaryOperator.Add
        is PandaSubExpr -> PandaBinaryOperator.Sub
        is PandaMulExpr -> PandaBinaryOperator.Mul
        is PandaDivExpr -> PandaBinaryOperator.Div
        is PandaGtExpr -> PandaBinaryOperator.Gt
        is PandaEqExpr -> PandaBinaryOperator.Eq
        is PandaNeqExpr -> PandaBinaryOperator.Neq
        else -> TODO("")
    }
}
