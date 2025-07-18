package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGlobalRef
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLexicalEnvType
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
import org.jacodb.ets.model.EtsRawType
import org.jacodb.ets.model.EtsRefType
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
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.initializeArrayLength
import org.usvm.api.mockMethodCall
import org.usvm.dataflow.ts.infer.tryGetKnownType
import org.usvm.dataflow.ts.util.type
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.machine.Constants
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.getResolvedValue
import org.usvm.machine.interpreter.isInitialized
import org.usvm.machine.interpreter.isResolved
import org.usvm.machine.interpreter.markInitialized
import org.usvm.machine.interpreter.markResolved
import org.usvm.machine.interpreter.setResolvedValue
import org.usvm.machine.operator.TsBinaryOperator
import org.usvm.machine.operator.TsUnaryOperator
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.util.EtsHierarchy
import org.usvm.util.TsResolutionResult
import org.usvm.util.createFakeField
import org.usvm.util.isResolved
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsField
import org.usvm.util.resolveEtsMethods
import org.usvm.util.throwExceptionWithoutStackFrameDrop

private val logger = KotlinLogging.logger {}

class TsExprResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int?,
    private val hierarchy: EtsHierarchy,
) : EtsEntity.Visitor<UExpr<out USort>?> {

    val simpleValueResolver: TsSimpleValueResolver =
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

    private inline fun <T> resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
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

    override fun visit(value: EtsConstant): UExpr<out USort>? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort>? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> {
        return simpleValueResolver.visit(value)
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

    override fun visit(expr: EtsCastExpr): UExpr<*>? = with(ctx) {
        val resolvedExpr = resolve(expr.arg) ?: return@with null
        return when (resolvedExpr.sort) {
            fp64Sort -> {
                logger.error("Unsupported cast from fp ${expr.arg} to ${expr.type}")
                TODO("Not yet implemented https://github.com/UnitTestBot/usvm/issues/299")
            }

            boolSort -> {
                logger.error("Unsupported cast from boolean ${expr.arg} to ${expr.type}")
                TODO("Not yet implemented https://github.com/UnitTestBot/usvm/issues/299")
            }

            addressSort -> {
                scope.calcOnState {
                    val instance = resolvedExpr.asExpr(addressSort)

                    if (instance.isFakeObject()) {
                        val fakeType = instance.getFakeType(scope)
                        pathConstraints += fakeType.refTypeExpr
                        val refValue = instance.extractRef(scope)
                        pathConstraints += memory.types.evalIsSubtype(refValue, expr.type)
                        return@calcOnState instance
                    }

                    if (expr.type !is EtsRefType) {
                        logger.error("Unsupported cast from non-ref ${expr.arg} to ${expr.type}")
                        TODO("Not supported yet https://github.com/UnitTestBot/usvm/issues/299")
                    }

                    pathConstraints += memory.types.evalIsSubtype(instance, expr.type)
                    instance
                }
            }

            else -> {
                error("Unsupported cast from ${expr.arg} to ${expr.type}")
            }
        }
    }

    override fun visit(expr: EtsTypeOfExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg) ?: return null

        if (arg.sort == fp64Sort) {
            return mkStringConstant("number", scope)
        }
        if (arg.sort == boolSort) {
            return mkStringConstant("boolean", scope)
        }
        if (arg.sort == addressSort) {
            val ref = arg.asExpr(addressSort)
            return mkIte(
                condition = mkHeapRefEq(ref, mkTsNullValue()),
                trueBranch = mkStringConstant("object", scope),
                falseBranch = mkIte(
                    condition = mkHeapRefEq(ref, mkUndefinedValue()),
                    trueBranch = mkStringConstant("undefined", scope),
                    falseBranch = mkIte(
                        condition = scope.calcOnState {
                            val unwrappedRef = ref.unwrapRefWithPathConstraint(scope)

                            // TODO: adhoc: "expand" ITE
                            if (unwrappedRef is UIteExpr<*>) {
                                val trueBranch = unwrappedRef.trueBranch
                                val falseBranch = unwrappedRef.falseBranch
                                if (trueBranch.isFakeObject() || falseBranch.isFakeObject()) {
                                    val unwrappedTrueExpr =
                                        trueBranch.asExpr(addressSort).unwrapRefWithPathConstraint(scope)
                                    val unwrappedFalseExpr =
                                        falseBranch.asExpr(addressSort).unwrapRefWithPathConstraint(scope)
                                    return@calcOnState mkIte(
                                        condition = unwrappedRef.condition,
                                        trueBranch = memory.types.evalTypeEquals(unwrappedTrueExpr, EtsStringType),
                                        falseBranch = memory.types.evalTypeEquals(unwrappedFalseExpr, EtsStringType),
                                    )
                                }
                            }

                            memory.types.evalTypeEquals(unwrappedRef, EtsStringType)
                        },
                        trueBranch = mkStringConstant("string", scope),
                        falseBranch = mkStringConstant("object", scope),
                    )
                )
            )
        }

        logger.error { "visit(${expr::class.simpleName}) is not implemented yet" }
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

    override fun visit(expr: EtsAwaitExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg) ?: return null

        // Awaiting primitives does nothing.
        if (arg.sort != addressSort) {
            return arg
        }

        val promise = arg.asExpr(addressSort)
        check(isAllocatedConcreteHeapRef(promise)) {
            "Promise instance should be allocated, but it is not: $promise"
        }

        val promiseState = scope.calcOnState {
            promiseState[promise] ?: PromiseState.PENDING
        }

        val isResolved = scope.calcOnState { isResolved(promise) }
        return if (!isResolved) {
            // If the promise is not resolved yet, we need to call the executor to resolve it.
            check(promiseState == PromiseState.PENDING) {
                "Promise state should be PENDING, but it is $promiseState for $promise"
            }
            val executor = scope.calcOnState {
                promiseExecutor[promise]
                    ?: error("Await expression should have a promise executor, but it is not set for $promise")
            }
            check(executor.cfg.stmts.isNotEmpty())

            val args: MutableList<UExpr<*>> = mutableListOf()

            // 'this':
            // args += mkUndefinedValue()
            args += mkConcreteHeapRef(addressCounter.freshStaticAddress())

            val params = executor.parameters.toMutableList()
            if (params.isNotEmpty() && params[0].type is EtsLexicalEnvType) {
                params.removeFirst()
                // TODO: handle closures
                args += mkUndefinedValue()
            }
            if (params.isNotEmpty()) {
                args += resolveFunctionRef
                scope.doWithState {
                    setBoundThis(resolveFunctionRef, promise)
                }
                if (params.size >= 2) {
                    args += rejectFunctionRef
                    scope.doWithState {
                        setBoundThis(rejectFunctionRef, promise)
                    }
                    if (params.size >= 3) {
                        error(
                            "Promise executor should have at most 3 parameters" +
                                " (closures, resolve, reject), but got ${params.size}"
                        )
                    }
                }
            }
            scope.doWithState {
                pushSortsForActualArguments(args)
                memory.stack.push(args.toTypedArray(), executor.localsCount)
                registerCallee(currentStatement, executor.cfg)
                callStack.push(executor, currentStatement)
                newStmt(executor.cfg.stmts.first())
            }
            null
        } else {
            when (promiseState) {
                PromiseState.PENDING -> {
                    error("Promise state should not be PENDING, but it is for $promise")
                }

                PromiseState.FULFILLED -> {
                    // If the promise is already resolved, we can return this value.
                    // val sort = typeToSort(expr.arg.type)
                    val sort = typeToSort(EtsUnknownType)
                    if (sort == unresolvedSort) {
                        val value = scope.calcOnState {
                            getResolvedValue(promise, addressSort)
                        }
                        check(value.isFakeObject())
                        value
                    } else {
                        scope.calcOnState {
                            getResolvedValue(promise, sort)
                        }
                    }
                }

                PromiseState.REJECTED -> {
                    // If the promise is rejected, we throw an exception.
                    val reason = scope.calcOnState {
                        getResolvedValue(promise, addressSort)
                    }
                    scope.doWithState {
                        // TODO: create proper exception
                        methodResult = TsMethodResult.TsException(reason, EtsStringType)
                    }
                    null
                }
            }
        }
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
        return resolveBinaryOperator(TsBinaryOperator.Mul, expr)
    }

    override fun visit(expr: EtsAndExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.And, expr)
    }

    override fun visit(expr: EtsOrExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Or, expr)
    }

    override fun visit(expr: EtsDivExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Div, expr)
    }

    override fun visit(expr: EtsRemExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Rem, expr)
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
        return resolveBinaryOperator(TsBinaryOperator.StrictNeq, expr)
    }

    override fun visit(expr: EtsLtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Lt, expr)
    }

    override fun visit(expr: EtsGtExpr): UExpr<out USort>? {
        return resolveBinaryOperator(TsBinaryOperator.Gt, expr)
    }

    override fun visit(expr: EtsLtEqExpr): UExpr<out USort>? {
        val eq = resolve(EtsEqExpr(expr.left, expr.right)) ?: return null
        val lt = resolve(EtsLtExpr(expr.left, expr.right)) ?: return null

        return ctx.mkOr(eq.asExpr(ctx.boolSort), lt.asExpr(ctx.boolSort))
    }

    override fun visit(expr: EtsGtEqExpr): UExpr<out USort>? {
        val eq = resolve(EtsEqExpr(expr.left, expr.right)) ?: return null
        val gt = resolve(EtsGtExpr(expr.left, expr.right)) ?: return null

        return ctx.mkOr(eq.asExpr(ctx.boolSort), gt.asExpr(ctx.boolSort))
    }

    override fun visit(expr: EtsInExpr): UExpr<out USort>? {
        logger.warn { "visit(${expr::class.simpleName}) is not implemented yet" }
        error("Not supported $expr")
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg)?.asExpr(addressSort) ?: return null
        scope.calcOnState {
            memory.types.evalIsSubtype(arg, expr.checkType)
        }
    }

    // endregion

    // region CALL

    private fun handleValueOf(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        if (expr.args.isNotEmpty()) {
            logger.warn { "valueOf() should have no arguments, but got ${expr.args.size}" }
        }

        val instance = resolve(expr.instance) ?: return null
        instance
    }

    private fun handleNumberIsNaN(expr: EtsInstanceCallExpr): UBoolExpr? = with(ctx) {
        check(expr.args.size == 1) { "Number.isNaN should have one argument" }
        val arg = resolve(expr.args.single()) ?: return null

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

    private fun handleArrayPush(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        val instance = resolve(expr.instance)?.asExpr(addressSort) ?: return null
        check(expr.args.size == 1) {
            "Array::push() should have exactly one argument, but got ${expr.args.size}"
        }
        val arg = resolve(expr.args.single()) ?: return null
        val arrayType = EtsArrayType(EtsUnknownType, dimensions = 1)

        scope.calcOnState {
            // Update the length of the array
            val lengthLValue = mkArrayLengthLValue(instance, arrayType)
            val length = memory.read(lengthLValue)
            val newLength = mkBvAddExpr(length, 1.toBv())
            memory.write(lengthLValue, newLength, guard = trueExpr)

            // Write the new element to the end of the array
            // TODO check sorts compatibility https://github.com/UnitTestBot/usvm/issues/300
            val newIndexLValue = mkArrayIndexLValue(
                sort = arg.sort,
                ref = instance,
                index = length,
                type = arrayType,
            )
            memory.write(newIndexLValue, arg.asExpr(newIndexLValue.sort), guard = ctx.trueExpr)

            // Return the new length of the array (as per ECMAScript spec for Array.push)
            newLength
        }
    }

    private fun handlePromiseConstructor(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        val instance = resolve(expr.instance) ?: return null
        val promise = instance.asExpr(addressSort)
        check(isAllocatedConcreteHeapRef(promise)) {
            "Promise instance should be allocated, but it is not: $promise"
        }
        check(expr.args.size == 1) {
            "Promise constructor should have exactly one argument, but got ${expr.args.size}"
        }
        val executorLocal = expr.args.single()

        // Lookup the executor method
        val executors = resolveEtsMethods(
            EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN,
                name = executorLocal.name,
                parameters = emptyList(),
                returnType = EtsUnknownType,
            )
        )
        if (executors.isEmpty()) {
            logger.error { "Could not resolve executor method: ${executorLocal.name}" }
            scope.assert(falseExpr)
            return null
        }
        if (executors.size > 1) {
            logger.error { "Ambiguous executor method: ${executorLocal.name}, resolved ${executors.size} times" }
            scope.assert(falseExpr)
            return null
        }
        val executor = executors.single()

        // Save the executor for the promise in the state
        scope.doWithState {
            setPromiseExecutor(promise, executor)
        }

        promise
    }

    private fun handlePromiseResolveReject(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        val promise = allocateConcreteRef()
        val newState = when (expr.callee.name) {
            "resolve" -> PromiseState.FULFILLED
            "reject" -> PromiseState.REJECTED
            else -> error("Unexpected: $expr")
        }
        check(expr.args.size == 1) {
            "Promise.${expr.callee.name}() should have exactly one argument, but got ${expr.args.size}"
        }
        val value = resolve(expr.args.single()) ?: return null
        val fakeValue = value.toFakeObject(scope)
        scope.doWithState {
            markResolved(promise)
            setPromiseState(promise, newState)
            setResolvedValue(promise, fakeValue)
        }
        promise
    }

    override fun visit(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
        // Mock all calls to `Logger` methods
        if (expr.instance.name == "Logger") {
            return mkUndefinedValue()
        }

        // Mock `toString()` method calls
        if (expr.callee.name == "toString") {
            if (expr.args.isNotEmpty()) {
                logger.warn { "toString() should have no arguments, but got ${expr.args.size}" }
            }
            return mkStringConstant("I am a string", scope)
        }

        // Handle `valueOf()` method calls
        if (expr.callee.name == "valueOf") {
            return handleValueOf(expr)
        }

        // Handle `Number.isNaN(...)` calls
        if (expr.instance.name == "Number") {
            if (expr.callee.name == "isNaN") {
                return handleNumberIsNaN(expr)
            }
        }

        // Handle `push` method calls on arrays
        // TODO write tests https://github.com/UnitTestBot/usvm/issues/300
        if (expr.callee.name == "push" && expr.instance.type is EtsArrayType) {
            return handleArrayPush(expr)
        }

        // Handle `Promise` constructor calls
        if (expr.callee.enclosingClass.name == "Promise" && expr.callee.name == CONSTRUCTOR_NAME) {
            return handlePromiseConstructor(expr)
        }

        // Handle `Promise.resolve(value)` and `Promise.reject(reason)` calls
        if (expr.instance.name == "Promise") {
            if (expr.callee.name in listOf("resolve", "reject")) {
                return handlePromiseResolveReject(expr)
            }
        }

        return when (val result = scope.calcOnState { methodResult }) {
            is TsMethodResult.Success -> {
                scope.doWithState { methodResult = TsMethodResult.NoCall }
                result.value
            }

            is TsMethodResult.TsException -> {
                error("Exception should be handled earlier")
            }

            is TsMethodResult.NoCall -> {
                val instance = run {
                    val resolved = resolve(expr.instance) ?: return null

                    if (resolved.sort != addressSort) {
                        if (expr.callee.name == "valueOf") {
                            if (expr.args.isNotEmpty()) {
                                logger.warn {
                                    "valueOf() should have no arguments, but got ${expr.args.size}"
                                }
                            }
                            return resolved
                        }

                        logger.warn { "Calling method on non-ref instance is not yet supported: $expr" }
                        scope.assert(falseExpr)
                        return null
                    }

                    resolved.asExpr(addressSort)
                }

                val resolvedArgs = expr.args.map { resolve(it) ?: return null }

                val virtualCall = TsVirtualMethodCallStmt(
                    callee = expr.callee,
                    instance = instance,
                    args = resolvedArgs,
                    returnSite = scope.calcOnState { lastStmt },
                )
                scope.doWithState { newStmt(virtualCall) }

                null
            }
        }
    }

    private fun handleR(): UExpr<*>? = with(ctx) {
        val mockSymbol = scope.calcOnState {
            memory.mocker.createMockSymbol(trackedLiteral = null, addressSort, ownership)
        }
        scope.assert(mkNot(mkEq(mockSymbol, mkTsNullValue())))
        mockSymbol
    }

    private fun handleNumberConverter(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
        check(expr.args.size == 1) {
            "Number() should have exactly one argument, but got ${expr.args.size}"
        }
        val arg = resolve(expr.args.single()) ?: return null
        return mkNumericExpr(arg, scope)
    }

    private fun handleBooleanConverter(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
        check(expr.args.size == 1) {
            "Boolean() should have exactly one argument, but got ${expr.args.size}"
        }
        val arg = resolve(expr.args.single()) ?: return null
        return mkTruthyExpr(arg, scope)
    }

    override fun visit(expr: EtsStaticCallExpr): UExpr<*>? = with(ctx) {
        // Mock `$r` calls
        if (expr.callee.name == "\$r") {
            return handleR()
        }

        // Handle `Number(...)` calls
        if (expr.callee.name == "Number") {
            return handleNumberConverter(expr)
        }

        // Handle `Boolean(...)` calls
        if (expr.callee.name == "Boolean") {
            return handleBooleanConverter(expr)
        }

        return when (val result = scope.calcOnState { methodResult }) {
            is TsMethodResult.Success -> {
                scope.doWithState { methodResult = TsMethodResult.NoCall }
                result.value
            }

            is TsMethodResult.TsException -> {
                error("Exception should be handled earlier")
            }

            is TsMethodResult.NoCall -> {
                when (val resolved = resolveStaticMethod(expr.callee)) {
                    TsResolutionResult.Empty -> {
                        logger.error { "Could not resolve static call: ${expr.callee}" }
                        scope.assert(falseExpr)
                    }

                    // Static virtual call
                    is TsResolutionResult.Ambiguous -> {
                        val resolvedArgs = expr.args.map { resolve(it) ?: return null }
                        val staticProperties = resolved.properties.take(Constants.STATIC_METHODS_FORK_LIMIT)
                        val staticInstances = scope.calcOnState {
                            staticProperties.map { getStaticInstance(it.enclosingClass!!) }
                        }
                        val concreteCalls = staticProperties.mapIndexed { index, value ->
                            TsConcreteMethodCallStmt(
                                callee = value,
                                instance = staticInstances[index],
                                args = resolvedArgs,
                                returnSite = scope.calcOnState { lastStmt }
                            )
                        }
                        val blocks: List<Pair<UBoolExpr, TsState.() -> Unit>> = concreteCalls.map { stmt ->
                            mkTrue() to { newStmt(stmt) }
                        }
                        scope.forkMulti(blocks)
                    }

                    is TsResolutionResult.Unique -> {
                        val instance = scope.calcOnState {
                            getStaticInstance(resolved.property.enclosingClass!!)
                        }
                        val args = expr.args.map { resolve(it) ?: return null }
                        val concreteCall = TsConcreteMethodCallStmt(
                            callee = resolved.property,
                            instance = instance,
                            args = args,
                            returnSite = scope.calcOnState { lastStmt },
                        )
                        scope.doWithState { newStmt(concreteCall) }
                    }
                }
                null
            }
        }
    }

    private fun resolveStaticMethod(
        method: EtsMethodSignature,
    ): TsResolutionResult<EtsMethod> {
        // Perfect signature:
        if (method.enclosingClass.name != UNKNOWN_CLASS_NAME) {
            val classes = hierarchy.classesForType(EtsClassType(method.enclosingClass))
            if (classes.size > 1) {
                val methods = classes.map { it.methods.single { it.name == method.name } }
                return TsResolutionResult.create(methods)
            }

            if (classes.isEmpty()) return TsResolutionResult.Empty

            val clazz = classes.single()
            val methods = clazz.methods.filter { it.name == method.name }
            return TsResolutionResult.create(methods)
        }

        // Unknown signature:
        val methods = ctx.scene.projectAndSdkClasses
            .flatMap { it.methods }
            .filter { it.name == method.name }

        return TsResolutionResult.create(methods)
    }

    override fun visit(expr: EtsPtrCallExpr): UExpr<out USort>? = with(ctx) {
        when (val result = scope.calcOnState { methodResult }) {
            is TsMethodResult.Success -> {
                scope.doWithState { methodResult = TsMethodResult.NoCall }
                result.value
            }

            is TsMethodResult.TsException -> {
                error("Exception should be handled earlier")
            }

            is TsMethodResult.NoCall -> {
                val ptr = resolve(expr.ptr) ?: return null

                if (isStaticHeapRef(ptr)) {
                    // Handle 'resolve' and 'reject' function call
                    if (ptr === resolveFunctionRef || ptr === rejectFunctionRef) {
                        val promise = scope.calcOnState {
                            boundThis[ptr] ?: error("No bound 'this' for ptr: $ptr")
                        }
                        check(isAllocatedConcreteHeapRef(promise)) {
                            "Promise instance should be allocated, but it is not: $promise"
                        }
                        val newState = when (ptr) {
                            resolveFunctionRef -> PromiseState.FULFILLED
                            rejectFunctionRef -> PromiseState.REJECTED
                            else -> error("Unexpected ptr: $ptr")
                        }
                        check(expr.args.size == 1) {
                            "${
                                when (ptr) {
                                    resolveFunctionRef -> "resolve"
                                    rejectFunctionRef -> "reject"
                                    else -> error("Unexpected ptr: $ptr")
                                }
                            }() should have exactly one argument, but got ${expr.args.size}"
                        }
                        val value = resolve(expr.args.single()) ?: return null
                        val fakeValue = value.toFakeObject(scope)
                        scope.doWithState {
                            markResolved(promise.asExpr(addressSort))
                            setPromiseState(promise, newState)
                            setResolvedValue(promise.asExpr(addressSort), fakeValue)
                        }
                        return mkUndefinedValue()
                    }

                    val callee = scope.calcOnState {
                        associatedFunction[ptr] ?: error("No associated methods for ptr: $ptr")
                    }
                    val resolvedArgs = expr.args.map { resolve(it) ?: return null }
                    val concreteCall = TsConcreteMethodCallStmt(
                        callee = callee.method,
                        instance = callee.thisInstance ?: ctx.mkUndefinedValue(),
                        args = resolvedArgs,
                        returnSite = scope.calcOnState { lastStmt },
                    )
                    scope.doWithState { newStmt(concreteCall) }
                } else {
                    mockMethodCall(scope, expr.callee)
                }

                null
            }
        }
    }

    // endregion

    // region ACCESS

    override fun visit(value: EtsArrayAccess): UExpr<out USort>? = with(ctx) {
        val array = resolve(value.array)?.asExpr(addressSort) ?: return null

        checkUndefinedOrNullPropertyRead(array) ?: return null

        val index = resolve(value.index)?.asExpr(fp64Sort) ?: return null
        val bvIndex = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = index,
            bvSize = 32,
            isSigned = true,
        ).asExpr(sizeSort)

        val arrayType = value.array.type as? EtsArrayType
            ?: error("Expected EtsArrayType, but got ${value.array.type}")
        val sort = typeToSort(arrayType.elementType)

        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = scope.calcOnState { memory.read(lengthLValue) }

        checkNegativeIndexRead(bvIndex) ?: return null
        checkReadingInRange(bvIndex, length) ?: return null

        val expr = if (sort is TsUnresolvedSort) {
            // Concrete arrays with the unresolved sort should consist of fake objects only.
            if (array is UConcreteHeapRef) {
                // Read a fake object from the array.
                val lValue = mkArrayIndexLValue(
                    sort = addressSort,
                    ref = array,
                    index = bvIndex,
                    type = arrayType
                )

                scope.calcOnState { memory.read(lValue) }
            } else {
                // If the array is not concrete, we need to allocate a fake object
                val boolArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
                val boolLValue = mkArrayIndexLValue(boolSort, array, bvIndex, boolArrayType)

                val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
                val fpLValue = mkArrayIndexLValue(fp64Sort, array, bvIndex, numberArrayType)

                val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
                val refLValue = mkArrayIndexLValue(addressSort, array, bvIndex, unknownArrayType)

                scope.calcOnState {
                    val boolValue = memory.read(boolLValue)
                    val fpValue = memory.read(fpLValue)
                    val refValue = memory.read(refLValue)

                    // Read an object from the memory at first,
                    // we don't need to recreate it if it is already a fake object.
                    val fakeObj = if (refValue.isFakeObject()) {
                        refValue
                    } else {
                        mkFakeValue(scope, boolValue, fpValue, refValue).also {
                            lValuesToAllocatedFakeObjects += refLValue to it
                        }
                    }

                    memory.write(refLValue, fakeObj.asExpr(addressSort), guard = trueExpr)

                    fakeObj
                }
            }
        } else {
            val lValue = mkArrayIndexLValue(
                sort = sort,
                ref = array,
                index = bvIndex,
                type = arrayType
            )
            scope.calcOnState { memory.read(lValue) }
        }

        return expr
    }

    fun checkUndefinedOrNullPropertyRead(instance: UHeapRef) = with(ctx) {
        val ref = instance.unwrapRef(scope)

        val neqNull = mkAnd(
            mkHeapRefEq(ref, mkUndefinedValue()).not(),
            mkHeapRefEq(ref, mkTsNullValue()).not(),
        )

        scope.fork(
            neqNull,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    fun checkNegativeIndexRead(index: UExpr<TsSizeSort>) = with(ctx) {
        val condition = mkBvSignedGreaterOrEqualExpr(index, mkBv(0))

        scope.fork(
            condition,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    fun checkReadingInRange(index: UExpr<TsSizeSort>, length: UExpr<TsSizeSort>) = with(ctx) {
        val condition = mkBvSignedLessExpr(index, length)

        scope.fork(
            condition,
            blockOnFalseState = allocateException(EtsStringType) // TODO incorrect exception type
        )
    }

    private fun allocateException(type: EtsType): (TsState) -> Unit = { state ->
        val address = state.memory.allocConcrete(type)
        state.throwExceptionWithoutStackFrameDrop(address, type)
    }

    private fun handleFieldRef(
        instance: EtsLocal?,
        instanceRef: UHeapRef,
        field: EtsFieldSignature,
        hierarchy: EtsHierarchy,
    ): UExpr<out USort>? = with(ctx) {
        val resolvedAddr = instanceRef.unwrapRef(scope)

        val etsField = resolveEtsField(instance, field, hierarchy)

        val sort = when (etsField) {
            is TsResolutionResult.Empty -> {
                if (field.name !in listOf("i", "LogLevel")) {
                    logger.warn { "Field $field not found, creating fake field" }
                }
                // If we didn't find any real fields, let's create a fake one.
                // It is possible due to mistakes in the IR or if the field was added explicitly
                // in the code.
                // Probably, the right behaviour here is to fork the state.
                resolvedAddr.createFakeField(field.name, scope)
                addressSort
            }

            is TsResolutionResult.Unique -> typeToSort(etsField.property.type)
            is TsResolutionResult.Ambiguous -> unresolvedSort
        }

        scope.doWithState {
            // If we accessed some field, we make an assumption that
            // this field should present in the object.
            // That's not true in the common case for TS, but that's the decision we made.
            val auxiliaryType = EtsAuxiliaryType(properties = setOf(field.name))
            // assert is required to update models
            scope.assert(memory.types.evalIsSubtype(resolvedAddr, auxiliaryType))
        }

        if (sort == unresolvedSort) {
            val boolLValue = mkFieldLValue(boolSort, instanceRef, field)
            val fpLValue = mkFieldLValue(fp64Sort, instanceRef, field)
            val refLValue = mkFieldLValue(addressSort, instanceRef, field)

            scope.calcOnState {
                val bool = memory.read(boolLValue)
                val fp = memory.read(fpLValue)
                val ref = memory.read(refLValue)

                // If a fake object is already created and assigned to the field,
                // there is no need to recreate another one
                val fakeRef = if (ref.isFakeObject()) {
                    ref
                } else {
                    mkFakeValue(scope, bool, fp, ref).also {
                        lValuesToAllocatedFakeObjects += refLValue to it
                    }
                }

                // TODO ambiguous enum fields resolution
                if (etsField is TsResolutionResult.Unique) {
                    val fieldType = etsField.property.type
                    if (fieldType is EtsRawType && fieldType.kind == "EnumValueType") {
                        val fakeType = fakeRef.getFakeType(scope)
                        pathConstraints += ctx.mkOr(
                            fakeType.fpTypeExpr,
                            fakeType.refTypeExpr
                        )

                        // val supertype = TODO()
                        // TODO add enum type as a constraint
                        // pathConstraints += memory.types.evalIsSubtype(
                        //     ref,
                        //     TODO()
                        // )
                    }
                }

                memory.write(refLValue, fakeRef.asExpr(addressSort), guard = trueExpr)

                fakeRef
            }
        } else {
            val lValue = mkFieldLValue(sort, resolvedAddr, field)
            scope.calcOnState { memory.read(lValue) }
        }
    }

    private fun handleArrayLength(
        value: EtsInstanceFieldRef,
        instance: UHeapRef,
    ): UExpr<*> = with(ctx) {
        val arrayType = value.instance.type as EtsArrayType
        val length = scope.calcOnState {
            val lengthLValue = mkArrayLengthLValue(instance, arrayType)
            memory.read(lengthLValue)
        }

        scope.doWithState {
            pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
        }

        return mkBvToFpExpr(
            fp64Sort,
            fpRoundingModeSortDefaultValue(),
            length.asExpr(sizeSort),
            signed = true,
        )
    }

    private fun handleFakeLength(
        value: EtsInstanceFieldRef,
        instance: UConcreteHeapRef,
    ): UExpr<*> = with(ctx) {
        val fakeType = instance.getFakeType(scope)

        // If we want to get length from a fake object, we assume that it is an array.
        scope.doWithState {
            pathConstraints += fakeType.refTypeExpr
        }

        val ref = instance.unwrapRef(scope)

        val arrayType = when (val type = value.instance.type) {
            is EtsArrayType -> type

            is EtsAnyType, is EtsUnknownType -> {
                // If the type is not an array, we assume it is a fake object with
                // a length property that behaves like an array.
                EtsArrayType(EtsUnknownType, dimensions = 1)
            }

            else -> error("Expected EtsArrayType, EtsAnyType or EtsUnknownType, but got $type")
        }
        val length = scope.calcOnState {
            val lengthLValue = mkArrayLengthLValue(ref, arrayType)
            memory.read(lengthLValue)
        }

        scope.doWithState {
            pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
        }

        return mkBvToFpExpr(
            fp64Sort,
            fpRoundingModeSortDefaultValue(),
            length.asExpr(sizeSort),
            signed = true
        )
    }

    override fun visit(value: EtsInstanceFieldRef): UExpr<out USort>? = with(ctx) {
        val instanceResolved = resolve(value.instance) ?: return null
        if (instanceResolved.sort != addressSort) {
            logger.error { "Instance of field ref should be a reference, but got $instanceResolved" }
            scope.assert(falseExpr)
            return null
        }
        val instanceRef = instanceResolved.asExpr(addressSort)

        checkUndefinedOrNullPropertyRead(instanceRef) ?: return null

        // Handle array length
        if (value.field.name == "length" && value.instance.type is EtsArrayType) {
            return handleArrayLength(value, instanceRef)
        }

        // Handle length property for fake objects
        // TODO: handle "length" property for arrays inside fake objects
        if (value.field.name == "length" && instanceRef.isFakeObject()) {
            return handleFakeLength(value, instanceRef)
        }

        return handleFieldRef(value.instance, instanceRef, value.field, hierarchy)
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort>? = with(ctx) {
        val clazz = scene.projectAndSdkClasses.singleOrNull {
            it.signature == value.field.enclosingClass
        } ?: return null

        val instanceRef = scope.calcOnState { getStaticInstance(clazz) }

        val initializer = clazz.methods.singleOrNull { it.name == STATIC_INIT_METHOD_NAME }
        if (initializer != null) {
            val isInitialized = scope.calcOnState { isInitialized(clazz) }
            if (isInitialized) {
                scope.doWithState {
                    // TODO: Handle static initializer result
                    val result = methodResult
                    // TODO: Why this signature check is needed?
                    // TODO: Why we need to reset methodResult here? Double-check that it is even set anywhere.
                    if (result is TsMethodResult.Success && result.methodSignature == initializer.signature) {
                        methodResult = TsMethodResult.NoCall
                    }
                }
            } else {
                scope.doWithState {
                    markInitialized(clazz)
                    pushSortsForArguments(instance = null, args = emptyList(), localToIdx)
                    registerCallee(currentStatement, initializer.cfg)
                    callStack.push(initializer, currentStatement)
                    memory.stack.push(arrayOf(instanceRef), initializer.localsCount)
                    newStmt(initializer.cfg.stmts.first())
                }
                return null
            }
        }

        return handleFieldRef(instance = null, instanceRef, value.field, hierarchy)
    }

    override fun visit(value: EtsCaughtExceptionRef): UExpr<out USort>? {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsGlobalRef): UExpr<out USort>? {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsClosureFieldRef): UExpr<out USort>? = with(ctx) {
        val obj = resolve(value.base) ?: return null
        check(isStaticHeapRef(obj)) {
            "Closure object should be a concrete heap reference, but got $obj"
        }

        val sort = typeToSort(value.type)
        if (sort is TsUnresolvedSort) {
            val lValue = mkFieldLValue(addressSort, obj, value.fieldName)
            scope.calcOnState { memory.read(lValue) }
        } else {
            val lValue = mkFieldLValue(sort, obj, value.fieldName)
            scope.calcOnState { memory.read(lValue) }
        }
    }

    // endregion

    // region OTHER

    override fun visit(expr: EtsNewExpr): UExpr<out USort>? = with(ctx) {
        // Try to resolve the concrete type if possible.
        // Otherwise, create an object with UnclearRefType
        val resolvedType = if (expr.type.isResolved()) {
            scene.projectAndSdkClasses
                .singleOrNull { it.name == expr.type.typeName }?.type
                ?: expr.type
        } else {
            expr.type
        }

        if (expr.type.typeName == "Boolean") {
            val clazz = scene.sdkClasses.filter { it.name == "Boolean" }.maxBy { it.methods.size }
            return@with scope.calcOnState { memory.allocConcrete(clazz.type) }
        }

        if (expr.type.typeName == "Number") {
            val clazz = scene.sdkClasses.filter { it.name == "Number" }.maxBy { it.methods.size }
            return@with scope.calcOnState { memory.allocConcrete(clazz.type) }
        }

        scope.calcOnState { memory.allocConcrete(resolvedType) }
    }

    override fun visit(expr: EtsNewArrayExpr): UExpr<out USort>? = with(ctx) {
        val arrayType = expr.type

        require(arrayType is EtsArrayType) {
            "Expected EtsArrayType in newArrayExpr, but got ${arrayType::class.simpleName}"
        }

        scope.calcOnState {
            val size = resolve(expr.size) ?: return@calcOnState null

            if (size.sort != fp64Sort) {
                TODO()
            }

            val bvSize = mkFpToBvExpr(
                roundingMode = fpRoundingModeSortDefaultValue(),
                value = size.asExpr(fp64Sort),
                bvSize = 32,
                isSigned = true,
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

            if (arrayType.elementType is EtsArrayType) {
                TODO("Multidimensional arrays are not supported yet, https://github.com/UnitTestBot/usvm/issues/287")
            }

            val address = memory.allocConcrete(arrayType)
            memory.initializeArrayLength(address, arrayType, sizeSort, bvSize)

            address
        }
    }

    // endregion
}

class TsSimpleValueResolver(
    private val ctx: TsContext,
    private val scope: TsStepScope,
    private val localToIdx: (EtsMethod, EtsValue) -> Int?,
) : EtsValue.Visitor<UExpr<out USort>?> {

    private fun resolveLocal(local: EtsValue): UExpr<*> = with(ctx) {
        val currentMethod = scope.calcOnState { lastEnteredMethod }
        val entrypoint = scope.calcOnState { entrypoint }

        val localIdx = localToIdx(currentMethod, local)

        // If there is no local variable corresponding to the local,
        // we treat it as a field of some global object with the corresponding name.
        // It helps us to support global variables that are missed in the IR.
        if (localIdx == null) {
            require(local is EtsLocal)

            // Handle closures
            if (local.name.startsWith("%closures")) {
                val existingClosures = scope.calcOnState { closureObject[local.name] }
                if (existingClosures != null) {
                    return existingClosures
                }
                val type = local.type
                check(type is EtsLexicalEnvType)
                // val obj = scope.calcOnState { memory.allocConcrete(type) }
                val obj = mkConcreteHeapRef(addressCounter.freshStaticAddress())
                for (captured in type.closures) {
                    val resolvedCaptured = resolveLocal(captured)
                    val lValue = mkFieldLValue(resolvedCaptured.sort, obj, captured.name)
                    scope.doWithState {
                        memory.write(lValue, resolvedCaptured.cast(), guard = ctx.trueExpr)
                    }
                }
                scope.doWithState {
                    setClosureObject(local.name, obj)
                }
                return obj
            }

            val globalObject = scope.calcOnState { globalObject }

            val localName = local.name
            // Check whether this local was already created or not
            if (localName in scope.calcOnState { addedArtificialLocals }) {
                val sort = ctx.typeToSort(local.type)
                val lValue = if (sort is TsUnresolvedSort) {
                    mkFieldLValue(ctx.addressSort, globalObject, local.name)
                } else {
                    mkFieldLValue(sort, globalObject, local.name)
                }
                return scope.calcOnState { memory.read(lValue) }
            }

            logger.warn { "Cannot resolve local $local, creating a field of the global object" }

            scope.doWithState {
                addedArtificialLocals += localName
            }

            val sort = ctx.typeToSort(local.type)
            val lValue = if (sort is TsUnresolvedSort) {
                globalObject.createFakeField(localName, scope)
                mkFieldLValue(ctx.addressSort, globalObject, local.name)
            } else {
                mkFieldLValue(sort, globalObject, local.name)
            }
            return scope.calcOnState { memory.read(lValue) }
        }

        val sort = scope.calcOnState {
            val type = local.tryGetKnownType(currentMethod)
            getOrPutSortForLocal(localIdx, type)
        }

        // If we are not in the entrypoint, all correct values are already resolved and we can just return
        // a registerStackLValue for the local
        if (currentMethod != entrypoint) {
            val lValue = mkRegisterStackLValue(sort, localIdx)
            return scope.calcOnState { memory.read(lValue) }
        }

        // arguments and this for the first stack frame
        when (sort) {
            is UBoolSort -> {
                val lValue = mkRegisterStackLValue(sort, localIdx)
                scope.calcOnState { memory.read(lValue) }
            }

            is KFp64Sort -> {
                val lValue = mkRegisterStackLValue(sort, localIdx)
                scope.calcOnState { memory.read(lValue) }
            }

            is UAddressSort -> {
                val lValue = mkRegisterStackLValue(sort, localIdx)
                scope.calcOnState { memory.read(lValue) }
            }

            is TsUnresolvedSort -> {
                check(local is EtsThis || local is EtsParameterRef) {
                    "Only This and ParameterRef are expected here"
                }

                val boolRValue = ctx.mkRegisterReading(localIdx, ctx.boolSort)
                val fpRValue = ctx.mkRegisterReading(localIdx, ctx.fp64Sort)
                val refRValue = ctx.mkRegisterReading(localIdx, ctx.addressSort)

                val fakeObject = ctx.mkFakeValue(scope, boolRValue, fpRValue, refRValue)
                val lValue = mkRegisterStackLValue(ctx.addressSort, localIdx)
                scope.calcOnState {
                    memory.write(lValue, fakeObject.asExpr(ctx.addressSort), guard = ctx.trueExpr)
                }
                fakeObject
            }

            else -> error("Unsupported sort $sort")
        }
    }

    override fun visit(local: EtsLocal): UExpr<out USort> {
        if (local.name == "NaN") {
            return ctx.mkFp64NaN()
        }
        if (local.name == "Infinity") {
            return ctx.mkFpInf(false, ctx.fp64Sort)
        }

        if (local.name.startsWith(ANONYMOUS_METHOD_PREFIX)) {
            val sig = EtsMethodSignature(
                enclosingClass = EtsClassSignature.UNKNOWN,
                name = local.name,
                parameters = emptyList(),
                returnType = EtsUnknownType,
            )
            val methods = ctx.resolveEtsMethods(sig)
            val method = methods.single()
            val ref = scope.calcOnState { getMethodRef(method) }
            return ref
        }

        val currentMethod = scope.calcOnState { lastEnteredMethod }
        if (local !in currentMethod.getDeclaredLocals()) {
            if (local.type is EtsFunctionType) {
                // TODO: function pointers should be "singletons"
                return scope.calcOnState { memory.allocConcrete(local.type) }
            }
        }

        return resolveLocal(local)
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort> {
        return resolveLocal(value)
    }

    override fun visit(value: EtsThis): UExpr<out USort> {
        return resolveLocal(value)
    }

    override fun visit(value: EtsConstant): UExpr<out USort> = with(ctx) {
        logger.warn { "visit(${value::class.simpleName}) is not implemented yet" }
        error("Not supported $value")
    }

    override fun visit(value: EtsStringConstant): UExpr<out USort> = with(ctx) {
        mkStringConstant(value.value, scope)
    }

    override fun visit(value: EtsBooleanConstant): UExpr<out USort> = with(ctx) {
        mkBool(value.value)
    }

    override fun visit(value: EtsNumberConstant): UExpr<out USort> = with(ctx) {
        mkFp64(value.value)
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
        error("Should not be called")
    }

    override fun visit(value: EtsStaticFieldRef): UExpr<out USort> = with(ctx) {
        error("Should not be called")
    }

    override fun visit(value: EtsCaughtExceptionRef): UExpr<out USort>? {
        error("Should not be called")
    }

    override fun visit(value: EtsGlobalRef): UExpr<out USort>? {
        error("Should not be called")
    }

    override fun visit(value: EtsClosureFieldRef): UExpr<out USort>? {
        error("Should not be called")
    }
}
