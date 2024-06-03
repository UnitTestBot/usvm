package org.usvm.machine

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaBinaryExpr
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaBuiltInError
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
import org.jacodb.panda.dynamic.api.PandaFieldRef
import org.jacodb.panda.dynamic.api.PandaGeExpr
import org.jacodb.panda.dynamic.api.PandaGtExpr
import org.jacodb.panda.dynamic.api.PandaInstanceVirtualCallExpr
import org.jacodb.panda.dynamic.api.PandaLeExpr
import org.jacodb.panda.dynamic.api.PandaLengthExpr
import org.jacodb.panda.dynamic.api.PandaLoadedValue
import org.jacodb.panda.dynamic.api.PandaLocal
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaLtExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMethodConstant
import org.jacodb.panda.dynamic.api.PandaModExpr
import org.jacodb.panda.dynamic.api.PandaMulExpr
import org.jacodb.panda.dynamic.api.PandaNegExpr
import org.jacodb.panda.dynamic.api.PandaNeqExpr
import org.jacodb.panda.dynamic.api.PandaNewExpr
import org.jacodb.panda.dynamic.api.PandaNullConstant
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaPhiValue
import org.jacodb.panda.dynamic.api.PandaStaticCallExpr
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStrictNeqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaUndefinedConstant
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaValueByInstance
import org.jacodb.panda.dynamic.api.PandaVirtualCallExpr
import org.jacodb.panda.dynamic.api.TODOConstant
import org.jacodb.panda.dynamic.api.TODOExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.readArrayIndex
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.state.PandaMethodResult
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.sizeSort

@Suppress("unused")
class PandaExprResolver(
    private val ctx: PandaContext,
    private val scope: PandaStepScope,
    private val localIdxMapper: (PandaMethod, PandaLocal) -> Int,
) : PandaExprVisitor<PandaUExprWrapper?> {
    fun resolveLValue(value: PandaValue): List<ULValue<*, *>> =
        when (value) {
            is PandaFieldRef -> TODO()
            is PandaArrayAccess -> TODO()
            is PandaLocal -> resolveLocal(value)
            else -> error("Unexpected value: $value")
        }

    fun resolveLocal(local: PandaLocal, type: PandaType? = null): List<URegisterStackLValue<*>> {
        val method = requireNotNull(scope.calcOnState { lastEnteredMethod })
        val localIdx = localIdxMapper(method, local)
        if (type != null) {
            return listOf(URegisterStackLValue(ctx.typeToSort(type), localIdx))
        }
        return listOf(URegisterStackLValue(ctx.addressSort, localIdx), URegisterStackLValue(ctx.undefinedSort, localIdx)) + (ctx.typeSystem<PandaType>().topTypeStream() as PandaTopTypeStream).primitiveTypes.map { t ->
            URegisterStackLValue(ctx.typeToSort(t), localIdx)
        }
    }

    // TODO do we need a type?
    fun resolvePandaExpr(expr: PandaExpr, sort: USort? = null): PandaUExprWrapper? {
        return /* if (expr.type != type && type is PandaPrimitiveType) resolvePrimitiveCast() else */ expr.accept(this)
    }

    private fun wrap(expr: PandaExpr, wrapper: () -> UExpr<out USort>?) : PandaUExprWrapper? {
        wrapper()?.let {
            return PandaUExprWrapper(expr, it)
        } ?: return null
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
        block: (PandaUExprWrapper, PandaUExprWrapper) -> T,
    ): T? {
        val result0 = resolvePandaExpr(dependency0) ?: return null
        val result1 = resolvePandaExpr(dependency1) ?: return null
        return block(result0, result1)
    }

    override fun visitPandaExpr(expr: PandaExpr): PandaUExprWrapper? = wrap(expr) {
        resolvePandaExpr(expr)?.uExpr
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
        resolveLocal(expr).forEach { ref ->
            try {
                return@wrap scope.calcOnState { memory.read(ref) }
            } catch (_: Exception) { }
        }

        null
    }

    override fun visitPandaArrayAccess(expr: PandaArrayAccess): PandaUExprWrapper? = wrap(expr) {
        val ref = resolvePandaExpr(expr.array)?.uExpr?.asExpr(ctx.addressSort) ?: return@wrap null
        val uIndex = resolvePandaExpr(expr.index)?.uExpr ?: return@wrap null
        val index = ctx.extractPrimitiveValueIfRequired(uIndex, scope).asExpr(ctx.fp64Sort)
        scope.calcOnState {
            memory.readArrayIndex(ref, index, expr.array.type, ctx.fp64Sort)
        }
    }

    override fun visitPandaCastExpr(expr: PandaCastExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaCaughtError(expr: PandaCaughtError): PandaUExprWrapper? {
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

    override fun visitPandaExpExpr(expr: PandaExpExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaFieldRef(expr: PandaFieldRef): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaGeExpr(expr: PandaGeExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Ge, expr)
    }

    override fun visitPandaGtExpr(expr: PandaGtExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Gt, expr)
    }

    override fun visitPandaInstanceVirtualCallExpr(expr: PandaInstanceVirtualCallExpr): PandaUExprWrapper? =
        wrap(expr) {
            resolveInvoke(
                method = expr.method,
                instanceExpr = expr.instance,
                args = expr.args.drop(1)
            ) { arguments ->
                scope.doWithState {
                    addVirtualMethodCallStmt(expr.method, arguments)
                }
            }
        }


    override fun visitPandaLeExpr(expr: PandaLeExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Le, expr)
    }

    override fun visitPandaLengthExpr(expr: PandaLengthExpr): PandaUExprWrapper? = wrap(expr) {
        val arrayAddress = resolvePandaExpr(expr.array)?.uExpr?.asExpr(ctx.addressSort) ?: return@wrap null
        val lengthLValue = UArrayLengthLValue(arrayAddress, expr.array.type, ctx.sizeSort)
        scope.calcOnState { memory.read(lengthLValue) }
    }

    override fun visitPandaLoadedValue(expr: PandaLoadedValue): PandaUExprWrapper? = wrap(expr) {
        val instance = resolvePandaExpr(expr.instance)
        val uExpr = instance?.uExpr ?: return@wrap null
        if (uExpr.sort == ctx.stringSort) return@wrap null
        if (uExpr.sort == ctx.addressSort) return@wrap uExpr
        val newAddr = scope.calcOnState { memory.allocConcrete(PandaAnyType) }
        scope.calcOnState { memory.write(ctx.constructAuxiliaryFieldLValue(newAddr, ctx.stringSort), uExpr) }
        newAddr
//        // TODO this is field reading for now only
//        val fieldReading = UFieldLValue(
//            ctx.addressSort,
//            instance.uExpr.asExpr(ctx.addressSort),
//            PandaField(
//                name = "", // TODO ?????
//                type = PandaTypeName(PandaAnyType.typeName),
//                signature = null // TODO ?????
//            )
//        )
//
//        scope.calcOnState { memory.read(fieldReading) }
    }

    override fun visitPandaLocalVar(expr: PandaLocalVar): PandaUExprWrapper? = wrap(expr) {
        resolveLocal(expr).forEach { ref ->
            try {
                return@wrap scope.calcOnState { memory.read(ref) }
            } catch (_: Exception) { }
        }

         null
    }

    override fun visitPandaLtExpr(expr: PandaLtExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Lt, expr)
    }

    override fun visitPandaMethodConstant(expr: PandaMethodConstant): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaModExpr(expr: PandaModExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaMulExpr(expr: PandaMulExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Mul, expr)
    }

    override fun visitPandaNegExpr(expr: PandaNegExpr): PandaUExprWrapper? = wrap(expr) {
        TODO("Not yet implemented")
    }

    override fun visitPandaNeqExpr(expr: PandaNeqExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Neq, expr)
    }


    override fun visitPandaNewExpr(expr: PandaNewExpr): PandaUExprWrapper? = wrap(expr) {
        val address = scope.calcOnState { memory.allocConcrete(expr.type) }
        address
    }

    override fun visitPandaNullConstant(expr: PandaNullConstant): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaBoolConstant(expr: PandaBoolConstant): PandaUExprWrapper? = wrap(expr) {
        ctx.mkBool(expr.value)
    }

    override fun visitPandaBuiltInError(expr: PandaBuiltInError): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaNumberConstant(expr: PandaNumberConstant): PandaUExprWrapper? = wrap(expr) {
        ctx.mkFp64(expr.value.toDouble())
    }

    override fun visitPandaPhiValue(expr: PandaPhiValue): PandaUExprWrapper? = wrap(expr) {
        val value = expr.valueFromBB(scope.calcOnState { prevBBId })
        resolvePandaExpr(value)?.uExpr // TODO wrap????
    }

    override fun visitPandaStaticCallExpr(expr: PandaStaticCallExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStrictEqExpr(expr: PandaStrictEqExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Eq, expr)
//        var lhs = resolvePandaExpr(expr.lhv) ?: return null
//        var rhs = resolvePandaExpr(expr.rhv) ?: return null
//
//        lhs = ctx.extractPrimitiveValueIfRequired(lhs, scope)
//        rhs = ctx.extractPrimitiveValueIfRequired(rhs, scope)
//
//        if (lhs is KInterpretedValue && rhs is KInterpretedValue) {
//            if (lhs.sort != rhs.sort) {
//                return ctx.falseExpr
//            }
//
//            return scope.calcOnState {
//                memory.wrapField(ctx.mkEq(lhs.asExpr(lhs.sort), rhs.asExpr(lhs.sort)), PandaBoolType)
//            }
//        }
//
//        if (lhs is KInterpretedValue) {
//            TODO()
//        }
//
//        if (rhs is KInterpretedValue) {
//            return when (rhs.sort) {
//                fp64Sort -> {
//                    val value = if (lhs.sort == ctx.addressSort) {
//                        scope.calcOnState {
//                            val lvalue = constructAuxiliaryFieldLValue(lhs.asExpr(addressSort), fp64Sort)
//                            memory.read(lvalue)
//                        }
//                    } else {
//                        lhs
//                    }
//
//                    scope.calcOnState {
//                        val equalityValue = mkEq(value.asExpr(value.sort), rhs.asExpr(value.sort))
//                        memory.wrapField(equalityValue, PandaBoolType)
//                    }
//                }
//
//                boolSort -> TODO()
//                stringSort -> TODO()
//                else -> TODO()
//            }
//        }
//
//        TODO()
    }

    override fun visitPandaStrictNeqExpr(expr: PandaStrictNeqExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaStringConstant(expr: PandaStringConstant): PandaUExprWrapper? = wrap(expr) {
        val address = scope.calcOnState { memory.allocConcrete(PandaStringType) }
        val lValue = ctx.constructAuxiliaryFieldLValue(address, ctx.stringSort)
        val value = PandaConcreteString(ctx, expr.value)
        scope.doWithState { memory.write(lValue, value) }

        value
    }

    override fun visitPandaSubExpr(expr: PandaSubExpr): PandaUExprWrapper? = wrap(expr) {
        resolveBinaryOperator(PandaBinaryOperator.Sub, expr)
    }

    override fun visitPandaTODOConstant(expr: TODOConstant): PandaUExprWrapper? = wrap(expr) {
        TODO("Not yet implemented")
    }

    override fun visitPandaThis(expr: PandaThis): PandaUExprWrapper? = wrap(expr) {
        resolveLocal(expr, PandaAnyType).forEach { ref ->
            try {
                return@wrap scope.calcOnState { memory.read(ref) }
            } catch (_: Exception) { }
        }

        null
    }

    override fun visitPandaToNumericExpr(expr: PandaToNumericExpr): PandaUExprWrapper? = wrap(expr) {
        val arg = resolvePandaExpr(expr.arg) ?: return@wrap null
        ctx.mkFpToFpExpr(ctx.fp64Sort, ctx.fpRoundingModeSortDefaultValue(), arg.cast())
    }

    override fun visitPandaTypeofExpr(expr: PandaTypeofExpr): PandaUExprWrapper? {
        TODO("Not yet implemented")
    }

    override fun visitPandaUndefinedConstant(expr: PandaUndefinedConstant): PandaUExprWrapper? = wrap(expr) {
        // TODO intern
        ctx.undefinedObject
    }

    override fun visitPandaValueByInstance(expr: PandaValueByInstance): PandaUExprWrapper? = wrap(expr) {
        val lValue = resolveFieldRef(expr.instance, expr.property) ?: return@wrap null
        scope.calcOnState { memory.read(lValue) }
    }

    private fun resolveFieldRef(instance: PandaValue?, field: String): ULValue<*, *>? {
        with(ctx) {
            val instanceRef = if (instance != null) {
                resolvePandaExpr(instance)?.uExpr?.asExpr(addressSort) ?: return null
            } else {
                null
            }

            if (instanceRef != null) {
                return UFieldLValue(anySort, instanceRef, field)
            }

            TODO()
        }
    }

    override fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): PandaUExprWrapper? = wrap(expr) {
        resolveInvoke(
            method = expr.method,
            instanceExpr = null,
            args = expr.args
        ) { arguments ->
            scope.doWithState {
                addVirtualMethodCallStmt(expr.method, arguments)
            }
        }
    }

    private fun resolveInvoke(
        method: PandaMethod,
        instanceExpr: PandaValue?,
        args: List<PandaValue>,
        onNoCallPresent: PandaStepScope.(List<UExpr<out USort>>) -> Unit,
    ): UExpr<out USort>? {
        val instanceRef = instanceExpr?.let { resolvePandaExpr(it)?.uExpr }

        val arguments = mutableListOf<UExpr<out USort>>()

        if (instanceRef != null) {
            arguments += instanceRef
        }

        arguments += args.map { resolvePandaExpr(it)?.uExpr ?: return null }

        return resolveInvokeNoStaticInitializationCheck { onNoCallPresent(arguments) }
    }

    private inline fun resolveInvokeNoStaticInitializationCheck(
        onNoCallPresent: PandaStepScope.() -> Unit,
    ): UExpr<out USort>? {
        val result = scope.calcOnState { methodResult }
        return when (result) {
            is PandaMethodResult.Success -> {
                scope.doWithState { methodResult = PandaMethodResult.NoCall }
                result.value
            }

            is PandaMethodResult.NoCall -> {
                scope.onNoCallPresent()
                null
            }

            is PandaMethodResult.PandaException -> error("Exception should be handled earlier")
        }
    }

    override fun visitTODOExpr(expr: TODOExpr): PandaUExprWrapper? {
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
