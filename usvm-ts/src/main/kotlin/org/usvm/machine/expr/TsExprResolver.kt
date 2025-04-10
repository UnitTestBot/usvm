package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRawEntity
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidExpr
import org.jacodb.ets.model.EtsYieldExpr
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.allocateArray
import org.usvm.api.allocateArrayInitialized
import org.usvm.isTrue
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.isInitialized
import org.usvm.machine.interpreter.markInitialized
import org.usvm.machine.operator.TsBinaryOperator
import org.usvm.machine.operator.TsUnaryOperator
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.mkFakeValue
import org.usvm.memory.ULValue
import org.usvm.sizeSort
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.throwExceptionWithoutStackFrameDrop
import org.usvm.util.type

private val logger = KotlinLogging.logger {}

class TsExprResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsEntity.Visitor<UExpr<*>?> {

    val simpleValueResolver: TsSimpleValueResolver =
        TsSimpleValueResolver(ctx, scope, localToIdx)

    fun resolve(expr: EtsEntity): UExpr<*>? {
        return expr.accept(this)
    }

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        expr: EtsUnaryExpr,
    ): UExpr<*>? = resolveUnaryOperator(operator, expr.arg)

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        arg: EtsEntity,
    ): UExpr<*>? = resolveAfterResolved(arg) { resolved ->
        with(operator) { ctx.resolve(resolved, scope) }
    }

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        expr: EtsBinaryExpr,
    ): UExpr<*>? = resolveBinaryOperator(operator, expr.left, expr.right)

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        lhv: EtsEntity,
        rhv: EtsEntity,
    ): UExpr<*>? = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        with(operator) { ctx.resolve(lhs, rhs, scope) }
    }

    private inline fun <T> resolveAfterResolved(
        dependency: EtsEntity,
        block: (UExpr<*>) -> T,
    ): T? {
        val result = resolve(dependency) ?: return null
        return block(result)
    }

    private inline fun <T> resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (UExpr<*>, UExpr<*>) -> T,
    ): T? {
        val result0 = resolve(dependency0) ?: return null
        val result1 = resolve(dependency1) ?: return null
        return block(result0, result1)
    }

    // region DEFAULT

    override fun visit(value: EtsRawEntity): UExpr<*>? {
        return null
    }

    // endregion

    // region SIMPLE VALUE

    override fun visit(value: EtsLocal): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsParameterRef): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsThis): UExpr<*>? {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region CONSTANT

    override fun visit(value: EtsBooleanConstant): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsStringConstant): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNullConstant): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<*> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region UNARY

    override fun visit(expr: EtsNotExpr): UExpr<*>? {
        return resolveUnaryOperator(TsUnaryOperator.Not, expr)
    }

    // TODO move into TsUnaryOperator
    override fun visit(expr: EtsNegExpr): UExpr<*>? {
        return resolveUnaryOperator(TsUnaryOperator.Neg, expr)
    }

    override fun visit(expr: EtsUnaryPlusExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPostIncExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPostDecExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPreIncExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsPreDecExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitNotExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsCastExpr): UExpr<*>? = with(ctx) {
        if (expr.type == EtsNumberType) {
            val arg = resolve(expr.arg) ?: return null

            if (arg.isFakeObject()) {
                val type = arg.getFakeType(scope)
                scope.assert(type.fpTypeExpr)
                return arg.extractFp(scope)
            }

            return resolve(expr.arg)?.asExpr(ctx.fp64Sort)
        }

        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        // error("Not supported $expr")
        logger.warn { "stop" }
        scope.assert(ctx.falseExpr)
        return null
    }

    override fun visit(expr: EtsDeleteExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsVoidExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsAwaitExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsYieldExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region BINARY

    override fun visit(expr: EtsAddExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Add, expr)
    }

    override fun visit(expr: EtsSubExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Sub, expr)
    }

    override fun visit(expr: EtsMulExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Mul, expr)
    }

    override fun visit(expr: EtsAndExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.And, expr)
    }

    override fun visit(expr: EtsOrExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Or, expr)
    }

    override fun visit(expr: EtsNullishCoalescingExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsDivExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsRemExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsExpExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitAndExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitOrExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsBitXorExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsLeftShiftExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsRightShiftExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }
    // endregion

    // region RELATION

    override fun visit(expr: EtsEqExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Eq, expr)
    }

    override fun visit(expr: EtsNotEqExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Neq, expr)
    }

    override fun visit(expr: EtsStrictEqExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictEq, expr)
    }

    override fun visit(expr: EtsStrictNotEqExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictNeq, expr)
    }

    override fun visit(expr: EtsLtExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Lt, expr)
    }

    override fun visit(expr: EtsGtExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.Gt, expr)
    }

    override fun visit(expr: EtsLtEqExpr): UExpr<*>? {
        return resolveBinaryOperator(TsBinaryOperator.LtEq, expr)
    }

    override fun visit(expr: EtsGtEqExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<*>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region CALL

    private fun handleNumberIsNaN(arg: UExpr<*>): UBoolExpr? = with(ctx) {
        // 21.1.2.4 Number.isNaN ( number )
        // 1. If number is not a Number, return false.
        // 2. If number is NaN, return true.
        // 3. Otherwise, return false.

        if (arg.isFakeObject()) {
            val fakeType = arg.getFakeType(scope)
            val value = arg.extractFp(scope)
            return mkIte(
                condition = fakeType.fpTypeExpr,
                trueBranch = mkFpIsNaNExpr(value),
                falseBranch = mkFalse(),
            )
        }

        if (arg.sort == fp64Sort) {
            mkFpIsNaNExpr(arg.asExpr(fp64Sort))
        } else {
            mkFalse()
        }
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        if (expr.instance.name == "Number") {
            if (expr.callee.name == "isNaN") {
                return resolveAfterResolved(expr.args.single()) { arg ->
                    handleNumberIsNaN(arg)
                }
            }
        }

        if (expr.callee.name == "toString") {
            return mkFp64(42.0)
        }

        resolveInvoke(
            method = expr.callee,
            instance = expr.instance,
            args = expr.args,
        ) { arguments ->
            doWithState {
                // TODO: fix sorts for arguments - they should be pushed not here, but when the MethodCallStmt is handled
                // pushSortsForArguments(expr.instance, expr.args, localToIdx)
                pushSortsForActualArguments(arguments)
                val virtualCall = TsVirtualMethodCallStmt(
                    location = lastStmt.location,
                    callee = expr.callee,
                    arguments = arguments,
                    returnSite = lastStmt,
                )
                newStmt(virtualCall)
            }
        }
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
        if (expr.callee.name == "Number" && expr.callee.enclosingClass.name == "") {
            check(expr.args.size == 1) { "Number constructor should have exactly one argument" }
            return resolveAfterResolved(expr.args.single()) {
                mkNumericExpr(it, scope)
            }
        }

        resolveInvoke(
            method = expr.callee,
            instance = null,
            args = expr.args,
        ) { arguments ->
            val method = resolveStaticCall(expr.callee) ?: run {
                logger.error { "Could not resolve static call: ${expr.callee}" }
                scope.assert(falseExpr)
                return null
            }
            doWithState {
                // pushSortsForArguments(null, expr.args, localToIdx)
                pushSortsForActualArguments(arguments)
                val concreteCall = TsConcreteMethodCallStmt(
                    location = lastStmt.location,
                    callee = method,
                    arguments = arguments,
                    returnSite = lastStmt,
                )
                newStmt(concreteCall)
            }
        }
    }

    override fun visit(expr: EtsPtrCallExpr): UExpr<*>? {
        // TODO: IMPORTANT do not forget to fill sorts of arguments map
        TODO("Not supported ${expr::class.simpleName}: $expr")
    }

    private fun resolveStaticCall(
        method: EtsMethodSignature,
    ): EtsMethod? {
        // Perfect signature:
        if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
            val classes = ctx.scene.projectAndSdkClasses.filter { it.name == method.enclosingClass.name }
            if (classes.size != 1) return null
            val clazz = classes.single()
            val methods = clazz.methods.filter { it.name == method.name }
            if (methods.size != 1) return null
            return methods.single()
        }

        // Unknown signature:
        val methods = ctx.scene.projectAndSdkClasses
            .flatMap { it.methods }
            .filter { it.name == method.name }
        if (methods.size == 1) return methods.single()

        // error("Cannot resolve method $method")
        return null
    }

    private inline fun resolveInvoke(
        method: EtsMethodSignature,
        instance: EtsLocal?,
        args: List<EtsValue>,
        onNoCallPresent: TsStepScope.(List<UExpr<*>>) -> Unit,
    ): UExpr<*>? {
        val instanceExpr = if (instance != null) {
            val resolved = resolve(instance) ?: return null
            if (resolved.sort != ctx.addressSort) {
                // TODO: handle "<number>.toString()" and similar calls on non-ref instances
                // logger.warn { "Calling method on non-ref instance is not yet supported" }
                // scope.assert(ctx.falseExpr)
                // return null
                // ctx.mkNumericExpr(resolved, scope)
                scope.calcOnState {
                    val numberType = EtsClassType(
                        EtsClassSignature(
                            name = "Number",
                            file = EtsFileSignature.UNKNOWN
                        )
                    )
                    val x = memory.allocConcrete(numberType)
                    // TODO:  val lValue = "x.value"
                    // TODO memory.write(lValue, mkNumeric)
                    x
                }
            } else {
                resolved.asExpr(ctx.addressSort)
            }
        } else {
            null
        }

        val resolvedArgs = args.map { resolve(it) ?: return null }

        // function f(x: any, ...args: any[]) {}
        // f(1, 2, 3) -> f(1, [2, 3])
        // f(1, 2) -> f(1, [2])
        // f(1) -> f(1, [])
        // f() -> f(undefined, [])

        // function g(x: any, y: any) {}
        // g(1, 2) -> g(1, 2)
        // g(1) -> g(1, undefined)
        // g() -> g(undefined, undefined)
        // g(1, 2, 3) -> g(1, 2)

        val arguments = mutableListOf<UExpr<*>>()
        val numActual = resolvedArgs.size
        val numFormal = method.parameters.size

        if (method.parameters.isNotEmpty() && method.parameters.last().isRest) {
            // vararg call

            // first n-1 args are normal
            arguments += resolvedArgs.take(numFormal - 1)

            // fill with undefined
            repeat(numFormal - 1 - numActual) {
                arguments += ctx.mkUndefinedValue()
            }

            // wrap rest args in array
            val content = resolvedArgs.drop(numFormal - 1).map {
                with(ctx) { it.toFakeObject(scope) }
            }
            val array = scope.calcOnState {
                memory.allocateArrayInitialized(
                    type = EtsArrayType(EtsUnknownType, 1),
                    sort = ctx.addressSort,
                    sizeSort = ctx.sizeSort,
                    contents = content.asSequence(),
                )
            }
            arguments += array
        } else {
            // normal call

            // ignore extra args
            // arguments += resolvedArgs.take(numFormal)

            // TODO: do not ignore...
            arguments += resolvedArgs

            // fill with undefined
            repeat(numFormal - numActual) {
                arguments += ctx.mkUndefinedValue()
            }
        }

        // check(arguments.size == method.parameters.size)

        // Note: currently, 'this' has index 'n', so we must add it LAST, *after* all other arguments.
        // See `TsInterpreter::mapLocalToIdx`.
        if (instanceExpr != null) {
            // TODO: checkNullPointer(instanceRef) ?: return null
            // TODO: if (!assertIsSubtype(instanceRef, method.enclosingType)) return null

            arguments += instanceExpr
        }

        val result = scope.calcOnState { methodResult }
        return when (result) {
            is TsMethodResult.NoCall -> {
                scope.onNoCallPresent(arguments)
                null
            }

            is TsMethodResult.Success -> {
                scope.doWithState {
                    methodResult = TsMethodResult.NoCall
                }
                result.value
            }

            is TsMethodResult.TsException -> {
                error("Exception should be handled earlier")
            }
        }
    }

    // endregion

    // region ACCESS

    override fun visit(value: EtsArrayAccess): UExpr<*>? = with(ctx) {
        val instance = resolve(value.array)?.asExpr(addressSort) ?: return null
        val index = resolve(value.index)?.asExpr(fp64Sort) ?: return null
        val bvIndex = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = index,
            bvSize = 32,
            isSigned = true
        ).asExpr(sizeSort)

        // val elementType = scope.calcOnState { lastEnteredMethod }.getLocalType(value.array)
        val elementType = EtsUnknownType // TODO
        val lValue = mkArrayIndexLValue(
            addressSort,
            instance,
            bvIndex,
            EtsArrayType(elementType, 1),
        )
        val expr = scope.calcOnState { memory.read(lValue) }

        // check(expr.isFakeObject()) { "Only fake objects are allowed in arrays" }

        return expr
    }

    private fun checkUndefinedOrNullPropertyRead(instance: UHeapRef) = with(ctx) {
        val notNull = mkAnd(
            mkNot(mkEq(instance, mkUndefinedValue())),
            mkNot(mkEq(instance, mkTsNullValue())),
        )
        scope.fork(
            notNull,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    private fun allocateException(type: EtsType): (TsState) -> Unit = { state ->
        val address = state.memory.allocConcrete(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    private fun handleFieldRef(
        instanceLocal: EtsLocal?,
        instance: UHeapRef,
        field: EtsFieldSignature,
    ): UExpr<*>? = with(ctx) {
        // val etsFields = resolveEtsFields(instanceLocal, field)
        // if (etsFields.isEmpty()) {
        //     logger.warn { "Could not resolve field: $field" }
        //     scope.assert(falseExpr)
        //     return null
        // }
        // val etsFieldTypes = etsFields.map { it.type }.distinct()
        // if (etsFieldTypes.size != 1) {
        //     logger.warn { "Could not determine a unique field type for '$field', found ${etsFieldTypes.size}: $etsFieldTypes" }
        //     scope.assert(falseExpr)
        //     return null
        // }
        // val etsFieldType = etsFieldTypes.single()
        val etsFieldType = EtsUnknownType
        val sort = typeToSort(etsFieldType)

        val expr = scope.calcOnState {
            if (sort == unresolvedSort) {
                val refLValue = mkFieldLValue(addressSort, instance, field)
                val ref = memory.read(refLValue)

                // If a fake object is already created and assigned to the field,
                // there is no need to recreate another one
                val fakeRef = if (ref.isFakeObject()) {
                    ref
                } else {
                    val boolLValue = mkFieldLValue(boolSort, instance, field)
                    val fpLValue = mkFieldLValue(fp64Sort, instance, field)
                    val bool = memory.read(boolLValue)
                    val fp = memory.read(fpLValue)
                    mkFakeValue(scope, bool, fp, ref)
                }

                memory.write(refLValue, fakeRef, guard = trueExpr)

                fakeRef
            } else {
                val lValue = mkFieldLValue(sort, instance, field)
                memory.read(lValue)
            }
        }

        // TODO: check 'field.type' vs 'etsField.type'
        if (assertIsSubtype(expr, etsFieldType)) {
            expr
        } else {
            null
        }
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<*>? = with(ctx) {
        val instance = resolve(value.instance)?.asExpr(addressSort) ?: return null

        checkUndefinedOrNullPropertyRead(instance) ?: return null

        // TODO It is a hack for array's length
        if (value.instance.type is EtsArrayType && value.field.name == "length") {
            val length = scope.calcOnState {
                val lengthLValue = mkArrayLengthLValue(instance, value.instance.type as EtsArrayType)
                memory.read(lengthLValue)
            }
            return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), length.asExpr(sizeSort), signed = true)
        }

        // TODO: handle "length" property for arrays inside fake objects
        if (value.field.name == "length" && instance.isFakeObject()) {
            val fakeType = instance.getFakeType(scope)
            if (fakeType.refTypeExpr.isTrue) {
                val obj = instance.extractRef(scope)
                // TODO: fix array type. It should be the same as the type used when "writing" the length.
                //  However, current value.instance typically has 'unknown' type, and the best we can do here is
                //  to pretend that this is an array-like object (with "array length", not just "length" field),
                //  and "cast" instance to "unknown[]". The same could be done for any length writes, making
                //  the array type (for length) consistent (unknown everywhere), but less precise.
                val lengthLValue = mkArrayLengthLValue(obj, EtsArrayType(EtsUnknownType, 1))
                val length = scope.calcOnState { memory.read(lengthLValue) }
                return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), length.asExpr(sizeSort), signed = true)
            }
        }

        if (instance.isFakeObject()) {
            // TODO: process fields of primitives
            // For now, we assume that reading any field of a primitive value is 'undefined'

            val instanceRef = instance.extractRef(scope)
            checkUndefinedOrNullPropertyRead(instanceRef) ?: return null
            val instanceType = instance.getFakeType(scope)
            val expr = handleFieldRef(value.instance, instanceRef, value.field) ?: return null
            check(expr.isFakeObject())
            val exprType = expr.getFakeType(scope)
            val exprRef = expr.extractRef(scope)
            val undefined = mkUndefinedValue()

            scope.assert(
                mkAnd(
                    mkImplies(
                        instanceType.boolTypeExpr,
                        mkAnd(exprType.refTypeExpr, mkEq(exprRef, undefined))
                    ),
                    mkImplies(
                        instanceType.fpTypeExpr,
                        mkAnd(exprType.refTypeExpr, mkEq(exprRef, undefined))
                    ),
                )
            )

            return expr
        }

        return handleFieldRef(value.instance, instance, value.field)
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<*>? = with(ctx) {
        val clazz = scene.projectAndSdkClasses.singleOrNull {
            it.name == value.field.enclosingClass.name
        } ?: run {
            logger.warn { "Could not resolve static field: ${value.field}" }
            scope.assert(falseExpr)
            return null
        }

        val instance = scope.calcOnState { getStaticInstance(clazz) }

        val initializer = clazz.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
        if (initializer != null) {
            val isInitialized = scope.calcOnState { isInitialized(clazz) }
            if (isInitialized) {
                scope.doWithState {
                    // TODO: Handle static initializer result
                    val result = methodResult
                    if (result is TsMethodResult.Success && result.method == initializer) {
                        methodResult = TsMethodResult.NoCall
                    }
                }
            } else {
                scope.doWithState {
                    markInitialized(clazz)
                    pushSortsForArguments(instance = null, args = emptyList(), localToIdx)
                    callStack.push(initializer, currentStatement)
                    memory.stack.push(arrayOf(instance), initializer.localsCount)
                    newStmt(initializer.cfg.stmts.first())
                }
                return null
            }
        }

        return handleFieldRef(instanceLocal = null, instance, value.field)
    }

    // endregion

    // region OTHER

    override fun visit(expr: EtsNewExpr): UExpr<*>? = scope.calcOnState {
        memory.allocConcrete(EtsUnknownType) // TODO: expr.type
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<*>? = with(ctx) {
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

            val type = EtsArrayType(EtsUnknownType, 1) // TODO: fix array element type
            val address = memory.allocateArray(type, sizeSort, bvSize)
            // Note: overwrite array type
            // TODO: do we really need it?
            memory.types.allocate(address.address, type)

            address
        }
    }

    // endregion

    // TODO incorrect implementation
    private fun assertIsSubtype(expr: UExpr<*>, type: EtsType): Boolean {
        return true
    }
}

class TsSimpleValueResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int,
) : EtsValue.Visitor<UExpr<*>?> {

    private fun resolveLocal(local: EtsValue): ULValue<*, *> = with(ctx) {
        val currentMethod = scope.calcOnState { lastEnteredMethod }

        val idx = localToIdx(currentMethod, local)
        val sort = scope.calcOnState {
            val type = if (local is EtsLocal) {
                local.type
            } else if (local is EtsParameterRef) {
                // TODO: check if parameter indices are actually 0-based
                //       (they might start at 3 in ABC...)
                currentMethod.parameters[local.index].type
            } else if (local is EtsThis && currentMethod.enclosingClass != null) {
                currentMethod.enclosingClass!!.type
            } else {
                EtsUnknownType // TODO
            }
            getOrPutSortForLocal(idx, type)
        }

        // If we are not in the entrypoint, all correct values are already resolved and we can just return
        // a registerStackLValue for the local
        // if (currentMethod != entrypoint) {
        //     return mkRegisterStackLValue(sort, idx)
        // }

        // arguments and this for the first stack frame
        return when (sort) {
            is UBoolSort -> mkRegisterStackLValue(sort, idx)
            is KFp64Sort -> mkRegisterStackLValue(sort, idx)
            is UAddressSort -> mkRegisterStackLValue(sort, idx)
            is TsUnresolvedSort -> {
                if (local is EtsLocal) {
                    return@with mkRegisterStackLValue(addressSort, idx)
                }

                check(local is EtsThis || local is EtsParameterRef) {
                    "Only This and ParameterRef are expected here"
                }

                val lValue = mkRegisterStackLValue(addressSort, idx)

                val boolRValue = mkRegisterReading(idx, boolSort)
                val fpRValue = mkRegisterReading(idx, fp64Sort)
                val refRValue = mkRegisterReading(idx, addressSort)

                val fakeObject = mkFakeValue(scope, boolRValue, fpRValue, refRValue)
                scope.calcOnState {
                    memory.write(lValue, fakeObject.asExpr(addressSort), guard = trueExpr)
                }

                lValue
            }

            else -> error("Unsupported sort $sort")
        }
    }

    override fun visit(value: EtsLocal): UExpr<*> = with(ctx) {
        if (value.name == "NaN") {
            return mkFp64NaN()
        }
        if (value.name == "Infinity") {
            return mkFpInf(false, fp64Sort)
        }

        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsParameterRef): UExpr<*> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsThis): UExpr<*>? {
        val currentMethod = scope.calcOnState { lastEnteredMethod }
        if (currentMethod.isStatic || currentMethod.name == STATIC_INIT_METHOD_NAME) {
            currentMethod.enclosingClass?.let { clazz ->
                val instanceRef = scope.calcOnState { getStaticInstance(clazz) }
                val initializer = clazz.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
                if (initializer != null && initializer !== currentMethod) {
                    val isInitialized = scope.calcOnState { isInitialized(clazz) }
                    if (isInitialized) {
                        scope.doWithState {
                            // TODO: Handle static initializer result
                            val result = methodResult
                            if (result is TsMethodResult.Success && result.method == initializer) {
                                methodResult = TsMethodResult.NoCall
                            }
                        }
                        return instanceRef
                    } else {
                        logger.info { "Initializing ${clazz.name} using ${initializer.name}" }
                        scope.doWithState {
                            markInitialized(clazz)
                            pushSortsForArguments(instance = null, args = emptyList(), localToIdx)
                            callStack.push(initializer, currentStatement)
                            memory.stack.push(arrayOf(instanceRef), initializer.localsCount)
                            newStmt(initializer.cfg.stmts.first())
                        }
                        return null
                    }
                }
            }
        }

        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: EtsBooleanConstant): UExpr<*> = with(ctx) {
        mkBool(value.value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<*> = with(ctx) {
        mkFp64(value.value)
    }

    override fun visit(value: EtsStringConstant): UExpr<*> = with(ctx) {
        mkFp64(42.0)
    }

    override fun visit(value: EtsNullConstant): UExpr<*> = with(ctx) {
        mkTsNullValue()
    }

    override fun visit(value: EtsUndefinedConstant): UExpr<*> = with(ctx) {
        mkUndefinedValue()
    }

    override fun visit(value: EtsArrayAccess): UExpr<*> {
        error("Should not be called")
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<*> {
        error("Should not be called")
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<*> {
        error("Should not be called")
    }
}
