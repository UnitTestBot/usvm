package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.base.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.base.UNKNOWN_CLASS_NAME
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateArray
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
import org.usvm.model.TsAddExpr
import org.usvm.model.TsAndExpr
import org.usvm.model.TsArrayAccess
import org.usvm.model.TsArrayType
import org.usvm.model.TsAwaitExpr
import org.usvm.model.TsBinaryExpr
import org.usvm.model.TsBitAndExpr
import org.usvm.model.TsBitNotExpr
import org.usvm.model.TsBitOrExpr
import org.usvm.model.TsBitXorExpr
import org.usvm.model.TsBooleanConstant
import org.usvm.model.TsCastExpr
import org.usvm.model.TsClassType
import org.usvm.model.TsDeleteExpr
import org.usvm.model.TsDivExpr
import org.usvm.model.TsEntity
import org.usvm.model.TsEqExpr
import org.usvm.model.TsExpExpr
import org.usvm.model.TsGtEqExpr
import org.usvm.model.TsGtExpr
import org.usvm.model.TsInExpr
import org.usvm.model.TsInstanceCallExpr
import org.usvm.model.TsInstanceFieldRef
import org.usvm.model.TsInstanceOfExpr
import org.usvm.model.TsLeftShiftExpr
import org.usvm.model.TsLocal
import org.usvm.model.TsLtEqExpr
import org.usvm.model.TsLtExpr
import org.usvm.model.TsMethod
import org.usvm.model.TsMethodSignature
import org.usvm.model.TsMulExpr
import org.usvm.model.TsNegExpr
import org.usvm.model.TsNewArrayExpr
import org.usvm.model.TsNewExpr
import org.usvm.model.TsNotEqExpr
import org.usvm.model.TsNotExpr
import org.usvm.model.TsNullConstant
import org.usvm.model.TsNumberConstant
import org.usvm.model.TsNumberType
import org.usvm.model.TsOrExpr
import org.usvm.model.TsParameterRef
import org.usvm.model.TsPostDecExpr
import org.usvm.model.TsPostIncExpr
import org.usvm.model.TsPreDecExpr
import org.usvm.model.TsPreIncExpr
import org.usvm.model.TsPtrCallExpr
import org.usvm.model.TsRawEntity
import org.usvm.model.TsRemExpr
import org.usvm.model.TsRightShiftExpr
import org.usvm.model.TsStaticCallExpr
import org.usvm.model.TsStaticFieldRef
import org.usvm.model.TsStrictEqExpr
import org.usvm.model.TsStrictNotEqExpr
import org.usvm.model.TsStringConstant
import org.usvm.model.TsStringType
import org.usvm.model.TsSubExpr
import org.usvm.model.TsThis
import org.usvm.model.TsType
import org.usvm.model.TsTypeOfExpr
import org.usvm.model.TsUnaryExpr
import org.usvm.model.TsUnaryPlusExpr
import org.usvm.model.TsUndefinedConstant
import org.usvm.model.TsUnknownType
import org.usvm.model.TsUnsignedRightShiftExpr
import org.usvm.model.TsValue
import org.usvm.model.TsVoidExpr
import org.usvm.model.TsYieldExpr
import org.usvm.sizeSort
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.throwExceptionWithoutStackFrameDrop

private val logger = KotlinLogging.logger {}

class TsExprResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (TsMethod, TsValue) -> Int,
) : TsEntity.Visitor<UExpr<out USort>?> {

    val simpleValueResolver: TsSimpleValueResolver =
        TsSimpleValueResolver(ctx, scope, localToIdx)

    fun resolve(expr: TsEntity): UExpr<out USort>? {
        return expr.accept(this)
    }

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        expr: TsUnaryExpr,
    ): UExpr<out USort>? = resolveUnaryOperator(operator, expr.arg)

    private fun resolveUnaryOperator(
        operator: TsUnaryOperator,
        arg: TsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(arg) { resolved ->
        with(operator) { ctx.resolve(resolved, scope) }
    }

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        expr: TsBinaryExpr,
    ): UExpr<out USort>? = resolveBinaryOperator(operator, expr.left, expr.right)

    private fun resolveBinaryOperator(
        operator: TsBinaryOperator,
        lhv: TsEntity,
        rhv: TsEntity,
    ): UExpr<out USort>? = resolveAfterResolved(lhv, rhv) { lhs, rhs ->
        with(operator) { ctx.resolve(lhs, rhs, scope) }
    }

    private inline fun <T> resolveAfterResolved(
        dependency: TsEntity,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolve(dependency) ?: return null
        return block(result)
    }

    private inline fun<T> resolveAfterResolved(
        dependency0: TsEntity,
        dependency1: TsEntity,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolve(dependency0) ?: return null
        val result1 = resolve(dependency1) ?: return null
        return block(result0, result1)
    }

    // region DEFAULT

    override fun visit(value: TsRawEntity): UExpr<out USort>? {
        return null
    }

    // endregion

    // region SIMPLE VALUE

    override fun visit(value: TsLocal): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: TsParameterRef): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: TsThis): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region CONSTANT

    override fun visit(value: TsBooleanConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: TsNumberConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: TsStringConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: TsNullConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: TsUndefinedConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    // endregion

    // region UNARY

    override fun visit(expr: TsNotExpr): UExpr<out USort>? {
        return resolveUnaryOperator(TsUnaryOperator.Not, expr)
    }

    // TODO move into TsUnaryOperator
    override fun visit(expr: TsNegExpr): UExpr<out USort>? {
        return resolveUnaryOperator(TsUnaryOperator.Neg, expr)
    }

    override fun visit(expr: TsUnaryPlusExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsPostIncExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsPostDecExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsPreIncExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsPreDecExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsBitNotExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsCastExpr): UExpr<out USort>? {
        if (expr.type == TsNumberType) {
            return resolve(expr.arg)?.asExpr(ctx.fp64Sort)
        }

        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsTypeOfExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        // error("Not supported $expr")
        logger.warn { "stop" }
        scope.assert(ctx.falseExpr)
        return null
    }

    override fun visit(expr: TsDeleteExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsVoidExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsAwaitExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsYieldExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region BINARY

    override fun visit(expr: TsAddExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Add, expr)
    }

    override fun visit(expr: TsSubExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Sub, expr)
    }

    override fun visit(expr: TsMulExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Mul, expr)
    }

    override fun visit(expr: TsAndExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.And, expr)
    }

    override fun visit(expr: TsOrExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Or, expr)
    }

    override fun visit(expr: TsDivExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsRemExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsExpExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsBitAndExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsBitOrExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsBitXorExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsLeftShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsRightShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsUnsignedRightShiftExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region RELATION

    override fun visit(expr: TsEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Eq, expr)
    }

    override fun visit(expr: TsNotEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Neq, expr)
    }

    override fun visit(expr: TsStrictEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictEq, expr)
    }

    override fun visit(expr: TsStrictNotEqExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.StrictNeq, expr)
    }

    override fun visit(expr: TsLtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Lt, expr)
    }

    override fun visit(expr: TsGtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Gt, expr)
    }

    override fun visit(expr: TsLtEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsGtEqExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsInExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: TsInstanceOfExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    // endregion

    // region CALL

    private fun handleNumberIsNaN(arg: UExpr<out USort>): UBoolExpr? = with(ctx) {
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

    override fun visit(expr: TsInstanceCallExpr): UExpr<out USort>? = with(ctx) {
        if (expr.instance.name == "Number") {
            if (expr.method.name == "isNaN") {
                return resolveAfterResolved(expr.args.single()) { arg ->
                    handleNumberIsNaN(arg)
                }
            }
        }

        resolveInvoke(
            instance = expr.instance,
            arguments = expr.args,
        ) { args ->
            doWithState {
                // TODO: fix sorts for arguments - they should be pushed not here, but when the MethodCallStmt is handled
                pushSortsForArguments(expr.instance, expr.args, localToIdx)
                val virtualCall = TsVirtualMethodCallStmt(
                    location = lastStmt.location,
                    callee = expr.method,
                    arguments = args,
                    returnSite = lastStmt,
                )
                newStmt(virtualCall)
            }
        }

        //     resolveInvoke(
        //         method = expr.method,
        //         instance = expr.instance,
        //         arguments = { expr.args },
        //         argumentTypes = { expr.method.parameters.map { it.type } },
        //     ) { args ->
        //         doWithState {
        //             check(args.size == method.parametersWithThisCount)
        //             pushSortsForArguments(expr.instance, expr.args, localToIdx)
        //             callStack.push(method, currentStatement)
        //             memory.stack.push(args.toTypedArray(), method.localsCount)
        //             newStmt(method.cfg.stmts.first())
        //         }
        //     }
    }

    override fun visit(expr: TsStaticCallExpr): UExpr<out USort>? = with(ctx) {
        if (expr.method.name == "Number" && expr.method.enclosingClass.name == "") {
            check(expr.args.size == 1) { "Number constructor should have exactly one argument" }
            return resolveAfterResolved(expr.args.single()) {
                mkNumericExpr(it, scope)
            }
        }

        resolveInvoke(
            instance = null,
            arguments = expr.args,
        ) { args ->
            val method = resolveStaticCall(expr.method) ?: run {
                logger.error { "Could not resolve static call: ${expr.method}" }
                scope.assert(falseExpr)
                return null
            }
            doWithState {
                pushSortsForArguments(null, expr.args, localToIdx)
                val concreteCall = TsConcreteMethodCallStmt(
                    location = lastStmt.location,
                    callee = method,
                    arguments = args,
                    returnSite = lastStmt,
                )
                newStmt(concreteCall)
            }
        }
    }

    override fun visit(expr: TsPtrCallExpr): UExpr<out USort>? {
        // TODO: IMPORTANT do not forget to fill sorts of arguments map
        TODO("Not supported ${expr::class.simpleName}: $expr")
    }

    private fun resolveInstanceCall(
        instance: TsLocal,
        method: TsMethodSignature,
    ): TsMethod? {
        // Perfect signature:
        if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
            val clazz = ctx.scene.projectAndSdkClasses.single { it.name == method.enclosingClass.name }
            val methods = (clazz.methods + clazz.ctor).filter { it.name == method.name }
            if (methods.size != 1) return null
            return methods.single()
        }

        // Unknown signature:
        // val instanceType = TsUnknownType // TODO: instance.type
        val instanceType  = scope.calcOnState { lastEnteredMethod }.getLocalType(instance)
        if (instanceType is TsClassType) {
            val classes = ctx.scene.projectAndSdkClasses
                .filter { it.name == instanceType.signature.name }
            if (classes.size == 1) {
                val clazz = classes.single()
                val methods = (clazz.methods + clazz.ctor).filter { it.name == method.name }
                if (methods.size != 1) return null
                return methods.single()
            }
            val methods = classes
                .flatMap { it.methods + it.ctor }
                .filter { it.name == method.name }
            if (methods.size == 1) return methods.single()
        } else {
            val methods = ctx.scene.projectAndSdkClasses
                .flatMap { it.methods + it.ctor }
                .filter { it.name == method.name }
            if (methods.size == 1) return methods.single()
        }
        // error("Cannot resolve method $method")
        return null
    }

    private fun resolveStaticCall(
        method: TsMethodSignature,
    ): TsMethod? {
        // Perfect signature:
        if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
            val classes = ctx.scene.projectAndSdkClasses.filter { it.name == method.enclosingClass.name }
            if (classes.size != 1) return null
            val clazz = classes.single()
            val methods = (clazz.methods + clazz.ctor).filter { it.name == method.name }
            if (methods.size != 1) return null
            return methods.single()
        }

        // Unknown signature:
        val methods = ctx.scene.projectAndSdkClasses
            .flatMap { it.methods + it.ctor }
            .filter { it.name == method.name }
        if (methods.size == 1) return methods.single()

        // error("Cannot resolve method $method")
        return null
    }

    private inline fun resolveInvoke(
        instance: TsLocal?,
        arguments: List<TsValue>,
        onNoCallPresent: TsStepScope.(List<UExpr<out USort>>) -> Unit,
    ): UExpr<out USort>? {
        val instanceExpr = if (instance != null) {
            val resolved = resolve(instance) ?: return null
            resolved.asExpr(ctx.addressSort)
        } else {
            null
        }

        val argumentExprs = mutableListOf<UExpr<out USort>>()

        for (arg in arguments) {
            val resolved = resolve(arg) ?: return null
            argumentExprs += resolved
        }

        // Note: currently, 'this' has index 'n', so we must add it LAST, *after* all other arguments.
        // See `TsInterpreter::mapLocalToIdx`.
        if (instanceExpr != null) {
            // TODO: checkNullPointer(instanceRef) ?: return null
            // TODO: if (!assertIsSubtype(instanceRef, method.enclosingType)) return null

            argumentExprs += instanceExpr
        }

        return resolveInvokeNoStaticInitializationCheck { onNoCallPresent(argumentExprs) }
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

    override fun visit(value: TsArrayAccess): UExpr<out USort>? = with(ctx) {
        val instance = resolve(value.array)?.asExpr(ctx.addressSort) ?: return null
        val index = resolve(value.index)?.asExpr(ctx.fp64Sort) ?: return null
        val bvIndex = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = index,
            bvSize = 32,
            isSigned = true
        )

        val elementType = scope.calcOnState { lastEnteredMethod.getLocalType(value.array) }
        val lValue = mkArrayIndexLValue(
            addressSort,
            instance,
            bvIndex.asExpr(ctx.sizeSort),
            TsArrayType(elementType, 1),
        )
        val expr = scope.calcOnState { memory.read(lValue) }

        check(expr.isFakeObject()) { "Only fake objects are allowed in arrays" }

        return expr
    }

    private fun checkUndefinedOrNullPropertyRead(instance: UHeapRef) = with(ctx) {
        val neqNull = mkAnd(
            mkHeapRefEq(instance, ctx.mkUndefinedValue()).not(),
            mkHeapRefEq(instance, ctx.mkTsNullValue()).not()
        )

        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(TsStringType) // TODO incorrect exception type
        )
    }

    private fun allocateException(type: TsType): (TsState) -> Unit = { state ->
        val address = state.memory.allocConcrete(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    private fun handleFieldRef(
        instance: TsLocal?,
        instanceRef: UHeapRef,
        fieldName: String,
    ): UExpr<out USort>? = with(ctx) {
        // val etsFields = resolveTsFields(instance, field)
        // if (etsFields.isEmpty()) return null
        // val etsFieldTypes = etsFields.map { it.type }.distinct()
        // if (etsFieldTypes.size != 1) return null
        // val etsFieldType = etsFieldTypes.single()
        val etsFieldType = TsUnknownType
        val sort = typeToSort(etsFieldType)

        val expr = if (sort == unresolvedSort) {
            val boolLValue = mkFieldLValue(boolSort, instanceRef, fieldName)
            val fpLValue = mkFieldLValue(fp64Sort, instanceRef, fieldName)
            val refLValue = mkFieldLValue(addressSort, instanceRef, fieldName)

            scope.calcOnState {
                val bool = memory.read(boolLValue)
                val fp = memory.read(fpLValue)
                val ref = memory.read(refLValue)

                // If a fake object is already created and assigned to the field,
                // there is no need to recreate another one
                val fakeRef = if (ref.isFakeObject()) {
                    ref
                } else {
                    mkFakeValue(scope, bool, fp, ref)
                }

                memory.write(refLValue, fakeRef.asExpr(addressSort), guard = trueExpr)

                fakeRef
            }
        } else {
            val lValue = mkFieldLValue(sort, instanceRef, fieldName)
            scope.calcOnState { memory.read(lValue) }
        }

        // TODO: check 'field.type' vs 'etsField.type'
        if (assertIsSubtype(expr, etsFieldType)) {
            expr
        } else {
            null
        }
    }

    override fun visit(value: TsInstanceFieldRef): UExpr<out USort>? = with(ctx) {
        val instanceRef = resolve(value.instance)?.asExpr(addressSort) ?: return null

        checkUndefinedOrNullPropertyRead(instanceRef) ?: return null

        // TODO It is a hack for array's length
        // if (value.instance.type is TsArrayType && value.field.name == "length") {
        //     val lengthLValue = mkArrayLengthLValue(instanceRef, value.instance.type as TsArrayType)
        //     val length = scope.calcOnState { memory.read(lengthLValue) }
        //     return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), length.asExpr(sizeSort), signed = true)
        // }

        // TODO: handle "length" property for arrays inside fake objects
        // if (value.field.name == "length" && instanceRef.isFakeObject()) {
        //     val fakeType = scope.calcOnState {
        //         memory.types.getTypeStream(instanceRef).single() as FakeType
        //     }
        //     if (fakeType.refTypeExpr.isTrue) {
        //         val refLValue = getIntermediateRefLValue(instanceRef.address)
        //         val obj = scope.calcOnState { memory.read(refLValue) }
        //         // TODO: fix array type. It should be the same as the type used when "writing" the length.
        //         //  However, current value.instance typically has 'unknown' type, and the best we can do here is
        //         //  to pretend that this is an array-like object (with "array length", not just "length" field),
        //         //  and "cast" instance to "unknown[]". The same could be done for any length writes, making
        //         //  the array type (for length) consistent (unknown everywhere), but less precise.
        //         val lengthLValue = mkArrayLengthLValue(obj, TsArrayType(TsUnknownType, 1))
        //         val length = scope.calcOnState { memory.read(lengthLValue) }
        //         return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), length.asExpr(sizeSort), signed = true)
        //     }
        // }

        return handleFieldRef(value.instance, instanceRef, value.fieldName)
    }

    override fun visit(value: TsStaticFieldRef): UExpr<out USort>? = with(ctx) {
        val clazz = scene.projectAndSdkClasses.singleOrNull {
            it.name == value.enclosingClass.typeName
        } ?: run {
            scope.assert(falseExpr)
            return null
        }

        val instanceRef = scope.calcOnState { getStaticInstance(clazz) }

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
                    memory.stack.push(arrayOf(instanceRef), initializer.localsCount)
                    newStmt(initializer.cfg.stmts.first())
                }
                return null
            }
        }

        return handleFieldRef(instance = null, instanceRef, value.fieldName)
    }

    // endregion

    // region OTHER

    override fun visit(expr: TsNewExpr): UExpr<out USort>? = scope.calcOnState {
        memory.allocConcrete(TsUnknownType) // TODO: expr.type
    }

    override fun visit(expr: TsNewArrayExpr): UExpr<out USort>? = with(ctx) {
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
                blockOnFalseState = allocateException(TsStringType) // TODO incorrect exception type
            )

            val type = TsArrayType(TsUnknownType, 1) // TODO: fix array element type
            val address = memory.allocateArray(type, sizeSort, bvSize)
            memory.types.allocate(address.address, type)

            address
        }
    }

    // endregion

    // TODO incorrect implementation
    private fun assertIsSubtype(expr: UExpr<out USort>, type: TsType): Boolean {
        return true
    }
}

class TsSimpleValueResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (TsMethod, TsValue) -> Int,
) : TsValue.Visitor<UExpr<out USort>> {

    private fun resolveLocal(local: TsValue): ULValue<*, USort> = with(ctx) {
        val currentMethod = scope.calcOnState { lastEnteredMethod }
        val entrypoint = scope.calcOnState { entrypoint }

        val localIdx = localToIdx(currentMethod, local)
        val sort = scope.calcOnState {
            val method = lastEnteredMethod
            val type = if (local is TsLocal) method.getLocalType(local) else TsUnknownType
            getOrPutSortForLocal(localIdx, type)
        }

        // If we are not in the entrypoint, all correct values are already resolved and we can just return
        // a registerStackLValue for the local
        if (currentMethod != entrypoint) {
            return mkRegisterStackLValue(sort, localIdx)
        }

        // arguments and this for the first stack frame
        return when (sort) {
            is UBoolSort -> mkRegisterStackLValue(sort, localIdx)
            is KFp64Sort -> mkRegisterStackLValue(sort, localIdx)
            is UAddressSort -> mkRegisterStackLValue(sort, localIdx)
            is TsUnresolvedSort -> {
                if (local is TsLocal) {
                    return@with mkRegisterStackLValue(addressSort, localIdx)
                }

                check(local is TsThis || local is TsParameterRef) {
                    "Only This and ParameterRef are expected here"
                }

                val lValue = mkRegisterStackLValue(addressSort, localIdx)

                val boolRValue = mkRegisterReading(localIdx, boolSort)
                val fpRValue = mkRegisterReading(localIdx, fp64Sort)
                val refRValue = mkRegisterReading(localIdx, addressSort)

                val fakeObject = mkFakeValue(scope, boolRValue, fpRValue, refRValue)
                scope.calcOnState {
                    memory.write(lValue, fakeObject.asExpr(addressSort), guard = trueExpr)
                }

                lValue
            }

            else -> error("Unsupported sort $sort")
        }
    }

    override fun visit(value: TsLocal): UExpr<out USort> = with(ctx) {
        if (value.name == "NaN") {
            return mkFp64NaN()
        }
        if (value.name == "Infinity") {
            return mkFpInf(false, fp64Sort)
        }

        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: TsParameterRef): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: TsThis): UExpr<out USort> {
        val lValue = resolveLocal(value)
        return scope.calcOnState { memory.read(lValue) }
    }

    override fun visit(value: TsBooleanConstant): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visit(value: TsNumberConstant): UExpr<out USort> = with(ctx) {
        mkFp64(value.value)
    }

    override fun visit(value: TsStringConstant): UExpr<out USort> = with(ctx) {
        mkFp64(42.0)
    }

    override fun visit(value: TsNullConstant): UExpr<out USort> = with(ctx) {
        mkTsNullValue()
    }

    override fun visit(value: TsUndefinedConstant): UExpr<out USort> = with(ctx) {
        mkUndefinedValue()
    }

    override fun visit(value: TsArrayAccess): UExpr<out USort> {
        error("Should not be called")
    }

    override fun visit(value: TsInstanceFieldRef): UExpr<out USort> {
        error("Should not be called")
    }

    override fun visit(value: TsStaticFieldRef): UExpr<out USort> {
        error("Should not be called")
    }
}
