package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAndExpr
import org.jacodb.ets.base.EtsArrayAccess
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
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsSubExpr
import org.jacodb.ets.base.EtsTernaryExpr
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsTypeOfExpr
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.base.EtsUnaryPlusExpr
import org.jacodb.ets.base.EtsUndefinedConstant
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsUnsignedRightShiftExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.base.EtsVoidExpr
import org.jacodb.ets.base.EtsYieldExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateArray
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.operator.TsBinaryOperator
import org.usvm.machine.operator.TsUnaryOperator
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.FakeType
import org.usvm.machine.types.mkFakeValue
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.sizeSort
import org.usvm.util.fieldLookUp
import org.usvm.util.throwExceptionWithoutStackFrameDrop

private val logger = KotlinLogging.logger {}

class TsExprResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsEntity.Visitor<UExpr<out USort>?> {

    private val simpleValueResolver: TsSimpleValueResolver =
        TsSimpleValueResolver(ctx, scope, localToIdx)

    fun resolve(expr: EtsEntity): UExpr<out USort>? {
        return expr.accept(this)
    }

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        expr: EtsUnaryExpr,
    ): UExpr<out USort>? = resolveUnaryOperator(operator, expr.arg)

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        arg: EtsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(arg) { resolved ->
        with(operator) { ctx.resolve(resolved, scope) }
    }

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        expr: EtsBinaryExpr,
    ): UExpr<out USort>? = resolveBinaryOperator(operator, expr.left, expr.right)

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        lhv: EtsEntity,
        rhv: EtsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        with(operator) { ctx.resolve(lhs, rhs, scope) }
    }

    private inline fun <T> resolveAfterResolved(
        dependency: EtsEntity,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolve(dependency) ?: return null
        return block(result)
    }

    private inline fun resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort>,
    ): UExpr<out USort>? {
        val result0 = resolve(dependency0) ?: return null
        val result1 = resolve(dependency1) ?: return null
        return block(result0, result1)
    }

    // region SIMPLE VALUE

    override fun visit(value: EtsLocal): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsThis): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region CONSTANT

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort>? {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region UNARY

    override fun visit(expr: EtsNotExpr): UExpr<out USort>? {
        return resolveUnaryOperator(TsUnaryOperator.Not, expr)
    }

    // TODO move into TsUnaryOperator
    override fun visit(expr: EtsNegExpr): UExpr<out USort>? {
        return resolveUnaryOperator(TsUnaryOperator.Neg, expr)
    }

    override fun visit(expr: EtsUnaryPlusExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPostIncExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPostDecExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPreIncExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPreDecExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitNotExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsCastExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsVoidExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsAwaitExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsYieldExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region BINARY

    override fun visit(expr: EtsAddExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Add, expr)
    }

    override fun visit(expr: EtsSubExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Sub, expr)
    }

    override fun visit(expr: EtsMulExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsAndExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.And, expr)
    }

    override fun visit(expr: EtsOrExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Or, expr)
    }

    override fun visit(expr: EtsDivExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsRemExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsExpExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitAndExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitOrExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitXorExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsLeftShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsRightShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsNullishCoalescingExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsCommaExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region RELATION

    override fun visit(expr: EtsEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Eq, expr)
    }

    override fun visit(expr: EtsNotEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Neq, expr)
    }

    override fun visit(expr: EtsStrictEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictEq, expr)
    }

    override fun visit(expr: EtsStrictNotEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsLtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Lt, expr)
    }

    override fun visit(expr: EtsGtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Gt, expr)
    }

    override fun visit(expr: EtsLtEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsGtEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region CALL

    override fun visit(expr: EtsInstanceCallExpr): UExpr<out USort>? {
        return resolveInvoke(
            method = expr.method,
            instance = expr.instance,
            arguments = { expr.args },
            argumentTypes = { expr.method.parameters.map { it.type } },
        ) { args ->
            doWithState {
                val method = ctx.scene
                    .projectAndSdkClasses
                    .flatMap { it.methods }
                    .singleOrNull { it.signature == expr.method }
                    ?: error("Couldn't find a unique method with the signature ${expr.method}")

                pushSortsForArguments(expr.instance, expr.args, localToIdx)

                callStack.push(method, currentStatement)
                memory.stack.push(args.toTypedArray(), method.localsCount)

                newStmt(method.cfg.stmts.first())
            }
        }
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<out USort>? {
        return resolveInvoke(
            method = expr.method,
            instance = null,
            arguments = { expr.args },
            argumentTypes = { expr.method.parameters.map { it.type } },
        ) { args ->
            // TODO: IMPORTANT do not forget to fill sorts of arguments map
            TODO("Unsupported static methods")
        }
    }

    override fun visit(expr: EtsPtrCallExpr): UExpr<out USort>? {
        // TODO: IMPORTANT do not forget to fill sorts of arguments map
        TODO("Not supported ${expr::class.simpleName}: $expr")
    }

    private inline fun resolveInvoke(
        method: EtsMethodSignature,
        instance: EtsLocal?,
        arguments: () -> List<EtsValue>,
        argumentTypes: () -> List<EtsType>,
        onNoCallPresent: TsStepScope.(List<UExpr<out USort>>) -> Unit,
    ): UExpr<out USort>? {
        val instanceExpr = if (instance != null) {
            val resolved = resolve(instance) ?: return null
            resolved.asExpr(ctx.addressSort)
        } else {
            null
        }

        val args = mutableListOf<UExpr<out USort>>()

        for (arg in arguments()) {
            val resolved = resolve(arg) ?: return null
            args += resolved
        }

        // Note: currently, 'this' has index 'n', so we must add it LAST, *after* all other arguments.
        // See `TsInterpreter::mapLocalToIdx`.
        if (instanceExpr != null) {
            // TODO: checkNullPointer(instanceRef) ?: return null
            // TODO: if (!assertIsSubtype(instanceRef, method.enclosingType)) return null

            args += instanceExpr
        }

        return resolveInvokeNoStaticInitializationCheck { onNoCallPresent(args) }
    }

    private inline fun resolveInvokeNoStaticInitializationCheck(
        onNoCallPresent: TsStepScope.() -> Unit,
    ): UExpr<out USort>? {
        val result = scope.calcOnState { methodResult }
        return when (result) {
            is TsMethodResult.NoCall -> {
                scope.onNoCallPresent()
                null
            }

            is TsMethodResult.Success -> {
                scope.doWithState {
                    methodResult = TsMethodResult.NoCall
                }
                result.value
            }

            is TsMethodResult.TsException -> error("Exception should be handled earlier")
        }
    }

    // endregion

    // region ACCESS

    override fun visit(value: EtsArrayAccess): UExpr<out USort>? {
        val instance = resolve(value.array)?.asExpr(ctx.addressSort) ?: return null
        val index = resolve(value.index)?.asExpr(ctx.fp64Sort) ?: return null
        val bvIndex = ctx.mkFpToBvExpr(
            roundingMode = ctx.fpRoundingModeSortDefaultValue(),
            value = index,
            bvSize = 32,
            isSigned = true
        )

        val sort = if (value.type is EtsUnknownType) ctx.addressSort else ctx.typeToSort(value.type)
        val lValue = UArrayIndexLValue(sort, instance, bvIndex, value.array.type)

        return scope.calcOnState { memory.read(lValue) }
    }

    private fun checkUndefinedOrNullPropertyRead(instance: UHeapRef) = with(ctx) {
        val neqNull = mkAnd(
            mkHeapRefEq(instance, ctx.mkUndefinedValue()).not(),
            mkHeapRefEq(instance, ctx.mkTsNullValue()).not()
        )

        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    private fun allocateException(type: EtsType): (TsState) -> Unit = { state ->
        val address = state.memory.allocConcrete(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort>? = with(ctx) {
        val instanceRef = resolve(value.instance)?.asExpr(addressSort) ?: return null

        checkUndefinedOrNullPropertyRead(instanceRef) ?: return null

        // TODO: checkNullPointer(instanceRef)
        val fieldType = scene.fieldLookUp(value.field).type
        val sort = typeToSort(fieldType)
        val lValue = UFieldLValue(sort, instanceRef, value.field.name)
        val expr = scope.calcOnState { memory.read(lValue) }

        if (assertIsSubtype(expr, value.type)) {
            expr
        } else {
            null
        }
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort>? {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    // endregion

    // region OTHER

    override fun visit(expr: EtsNewExpr): UExpr<out USort>? = scope.calcOnState {
        memory.allocConcrete(expr.type)
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort>? = with(ctx) {
        scope.calcOnState {
            val size = resolve(expr.size) ?: return@calcOnState null

            if (size.sort != fp64Sort) {
                TODO()
            }

            val bvSize = mkFpToBvExpr(
                fpRoundingModeSortDefaultValue(),
                size.asExpr(fp64Sort),
                bvSize = 32,
                isSigned = true
            )

            val condition = mkAnd(
                mkEq(
                    mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), bvSize, signed = true),
                    size.asExpr(fp64Sort)
                ),
                mkAnd(
                    mkBvSignedLessOrEqualExpr(0.toBv(), bvSize.asExpr(bv32Sort)),
                    mkBvSignedLessOrEqualExpr(bvSize, Int.MAX_VALUE.toBv().asExpr(bv32Sort))
                )
            )

            scope.fork(
                condition,
                blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
            )

            val address = memory.allocateArray(expr.type, sizeSort, bvSize)
            memory.types.allocate(address.address, expr.type)

            address
        }
    }

    override fun visit(expr: EtsLengthExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsTernaryExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // TODO incorrect implementation
    private fun assertIsSubtype(expr: UExpr<out USort>, type: EtsType): Boolean {
        return true
    }
}

class TsSimpleValueResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsValue.Visitor<UExpr<out USort>?> {

    private fun resolveLocal(local: EtsValue): ULValue<*, USort> {
        val currentMethod = scope.calcOnState { lastEnteredMethod }
        val entrypoint = scope.calcOnState { entrypoint }

        val localIdx = localToIdx(currentMethod, local)
        val sort = scope.calcOnState {
            getOrPutSortForLocal(localIdx, local.type)
        }

        // If we are not in the entrypoint, all correct values are already resolved and we can just return
        // a registerStackLValue for the local
        if (currentMethod != entrypoint) {
            return URegisterStackLValue(sort, localIdx)
        }

        // arguments and this for the first stack frame
        return when (sort) {
            is UBoolSort -> URegisterStackLValue(sort, localIdx)
            is KFp64Sort -> URegisterStackLValue(sort, localIdx)
            is UAddressSort -> URegisterStackLValue(sort, localIdx)
            is TsUnresolvedSort -> {
                check(local is EtsThis || local is EtsParameterRef) {
                    "Only This and ParameterRef are expected here"
                }

                val lValue = URegisterStackLValue(ctx.addressSort, localIdx)

                val boolRValue = ctx.mkRegisterReading(localIdx, ctx.boolSort)
                val fpRValue = ctx.mkRegisterReading(localIdx, ctx.fp64Sort)
                val refRValue = ctx.mkRegisterReading(localIdx, ctx.addressSort)

                val fakeObject = ctx.mkFakeValue(scope, boolRValue, fpRValue, refRValue)
                scope.calcOnState {
                    with(ctx) {
                        val type = FakeType(
                            boolTypeExpr = makeSymbolicPrimitive(boolSort),
                            fpTypeExpr = makeSymbolicPrimitive(boolSort),
                            refTypeExpr = makeSymbolicPrimitive(boolSort)
                        )

                        scope.assert(type.mkExactlyOneTypeConstraint(ctx))

                        memory.types.allocate(fakeObject.address, type)
                        memory.write(lValue, fakeObject.asExpr(addressSort), guard = trueExpr)
                    }
                }

                lValue
            }

            else -> error("Unsupported sort $sort")
        }
    }

    override fun visit(value: EtsLocal): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsThis): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> = with(ctx) {
        mkFp64(value.value)
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort> = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsNullConstant): UExpr<out USort> = with(ctx) {
        mkTsNullValue()
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<out USort> = with(ctx) {
        mkUndefinedValue()
    }

    override fun visit(value: EtsArrayAccess): UExpr<out USort>? = with(ctx) {
        error("Should not be called")
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort> = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort> = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }
}
