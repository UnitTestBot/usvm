package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
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
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClosureFieldRef
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
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
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
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
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.UExpr
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.initializeArrayLength
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.mockMethodCall
import org.usvm.dataflow.ts.infer.tryGetKnownType
import org.usvm.dataflow.ts.util.type
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsOptions
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.interpreter.getGlobals
import org.usvm.machine.interpreter.getResolvedValue
import org.usvm.machine.interpreter.isResolved
import org.usvm.machine.interpreter.markResolved
import org.usvm.machine.interpreter.setResolvedValue
import org.usvm.machine.operator.TsBinaryOperator
import org.usvm.machine.operator.TsUnaryOperator
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.iteWriteIntoFakeObject
import org.usvm.sizeSort
import org.usvm.util.EtsHierarchy
import org.usvm.util.SymbolResolutionResult
import org.usvm.util.isResolved
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsMethods
import org.usvm.util.resolveImportInfo

private val logger = KotlinLogging.logger {}

/**
 * ECMAScript specification requires bitwise operations to be performed on 32-bit
 * signed integers. All numeric operands are converted to 32-bit integers
 * before the operation and the result is converted back to a Number type.
 */
private const val ECMASCRIPT_BITWISE_INTEGER_SIZE = 32

/**
 * ECMAScript bitwise shift operations mask the shift amount to 5 bits,
 * which means the effective shift amount is in the range [0, 31].
 * For example, `x << 32` is equivalent to `x << 0`,
 * and `x << 37` is equivalent to `x << 5`.
 */
private const val ECMASCRIPT_BITWISE_SHIFT_MASK = 0b11111

class TsExprResolver(
    internal val ctx: TsContext,
    internal val scope: TsStepScope,
    internal val options: TsOptions,
    internal val hierarchy: EtsHierarchy,
) : EtsEntity.Visitor<UExpr<out USort>?> {

    val simpleValueResolver: TsSimpleValueResolver =
        TsSimpleValueResolver(ctx, scope)

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

    internal inline fun <T> resolveAfterResolved(
        dependency: EtsEntity,
        block: (UExpr<out USort>) -> T,
    ): T? {
        val result = resolve(dependency) ?: return null
        return block(result)
    }

    internal inline fun <T> resolveAfterResolved(
        dependency0: EtsEntity,
        dependency1: EtsEntity,
        block: (UExpr<out USort>, UExpr<out USort>) -> T,
    ): T? {
        val result0 = resolve(dependency0) ?: return null
        val result1 = resolve(dependency1) ?: return null
        return block(result0, result1)
    }

    // region SIMPLE VALUE

    override fun visit(value: EtsLocal): UExpr<out USort>? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsParameterRef): UExpr<out USort>? {
        return simpleValueResolver.visit(value)
    }

    override fun visit(value: EtsThis): UExpr<out USort>? {
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

    override fun visit(expr: EtsUnaryPlusExpr): UExpr<out USort>? = with(ctx) {
        // Unary plus converts its operand to a number
        val arg = resolve(expr.arg) ?: return null
        return mkNumericExpr(arg, scope)
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

    override fun visit(expr: EtsBitNotExpr): UExpr<out USort>? = with(ctx) {
        // Bitwise NOT: converts operand to 32-bit signed integer and inverts all bits
        val arg = resolve(expr.arg) ?: return null
        val numericArg = mkNumericExpr(arg, scope)

        // Convert to 32-bit integer, perform bitwise NOT, then convert back to number
        val bvArg = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = numericArg.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true
        )
        val notResult = mkBvNotExpr(bvArg)

        return mkBvToFpExpr(
            fp64Sort,
            fpRoundingModeSortDefaultValue(),
            notResult,
            signed = true
        )
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

    override fun visit(expr: EtsDeleteExpr): UExpr<out USort>? = with(ctx) {
        logger.warn {
            "delete operator is not fully supported, the result may not be accurate"
        }

        // The delete operator removes a property from an object and returns true/false
        // For property access like "delete obj.prop", we need to handle EtsInstanceFieldRef
        when (val operand = expr.arg) {
            is EtsInstanceFieldRef -> {
                val instance = resolve(operand.instance)?.asExpr(addressSort) ?: return null

                // Check for null/undefined access
                checkUndefinedOrNullPropertyRead(scope, instance, operand.field.name) ?: return null

                // For now, we simulate deletion by setting the property to undefined
                // This is a simplification of the real semantics but sufficient for basic cases
                // TODO: This is incorrect for cases that the existing field is not of sort Address.
                //       In such case, the "overwriting" the field value with undefined does nothing
                //       to the actual number/boolean/string value inside the field,
                //       [if only we read the field using that "other" sort].
                val fieldLValue = mkFieldLValue(addressSort, instance, operand.field)
                scope.doWithState {
                    memory.write(fieldLValue, mkUndefinedValue(), guard = trueExpr)
                }

                // The delete operator returns true in most cases for property deletion
                mkTrue()
            }

            else -> {
                // For other operands (like variables), delete typically returns true without effect
                resolve(operand) ?: return null // Evaluate for potential side effects
                mkTrue()
            }
        }
    }

    override fun visit(expr: EtsVoidExpr): UExpr<out USort>? = with(ctx) {
        // The void operator evaluates its operand for side effects and returns undefined.
        resolve(expr.arg) ?: return null
        return mkUndefinedValue()
    }

    override fun visit(expr: EtsAwaitExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg) ?: return null

        // Awaiting primitives does nothing.
        if (arg.sort != addressSort) {
            return arg
        }
        // ...including null/undefined
        if (arg == mkTsNullValue() || arg == mkUndefinedValue()) {
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

            // Executor lambda does not have 'this', so we fill it with 'undefined':
            args += mkUndefinedValue()

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

    override fun visit(expr: EtsBitAndExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftNum = mkNumericExpr(left, scope)
        val rightNum = mkNumericExpr(right, scope)

        // Convert to 32-bit integers, perform bitwise AND, then convert back
        val leftBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = leftNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val rightBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = rightNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val result = mkBvAndExpr(leftBv, rightBv)

        return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), result, signed = true)
    }

    override fun visit(expr: EtsBitOrExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftNum = mkNumericExpr(left, scope)
        val rightNum = mkNumericExpr(right, scope)

        // Convert to 32-bit integers, perform bitwise OR, then convert back
        val leftBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = leftNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val rightBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = rightNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val result = mkBvOrExpr(leftBv, rightBv)

        return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), result, signed = true)
    }

    override fun visit(expr: EtsBitXorExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftNum = mkNumericExpr(left, scope)
        val rightNum = mkNumericExpr(right, scope)

        // Convert to 32-bit integers, perform bitwise XOR, then convert back
        val leftBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = leftNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val rightBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = rightNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val result = mkBvXorExpr(leftBv, rightBv)

        return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), result, signed = true)
    }

    override fun visit(expr: EtsLeftShiftExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftNum = mkNumericExpr(left, scope)
        val rightNum = mkNumericExpr(right, scope)

        // Convert to 32-bit integers and perform left shift
        val leftBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = leftNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val rightBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = rightNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )

        // Mask the shift amount to 5 bits (0-31) as per JavaScript spec
        val shiftAmount = mkBvAndExpr(
            rightBv,
            mkBv(ECMASCRIPT_BITWISE_SHIFT_MASK, ECMASCRIPT_BITWISE_INTEGER_SIZE.toUInt())
        )
        val result = mkBvShiftLeftExpr(leftBv, shiftAmount)

        return mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), result, signed = true)
    }

    override fun visit(expr: EtsRightShiftExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftNum = mkNumericExpr(left, scope)
        val rightNum = mkNumericExpr(right, scope)

        // Convert to 32-bit integers and perform signed right shift
        val leftBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = leftNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val rightBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = rightNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )

        // Mask the shift amount to 5 bits (0-31)
        val shiftAmount = mkBvAndExpr(
            rightBv,
            mkBv(ECMASCRIPT_BITWISE_SHIFT_MASK, ECMASCRIPT_BITWISE_INTEGER_SIZE.toUInt())
        )
        val result = mkBvArithShiftRightExpr(leftBv, shiftAmount)

        return mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = result,
            signed = true,
        )
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftNum = mkNumericExpr(left, scope)
        val rightNum = mkNumericExpr(right, scope)

        // Convert to 32-bit integers and perform unsigned right shift
        val leftBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = leftNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )
        val rightBv = mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = rightNum.asExpr(fp64Sort),
            bvSize = ECMASCRIPT_BITWISE_INTEGER_SIZE,
            isSigned = true,
        )

        // Mask the shift amount to 5 bits (0-31)
        val shiftAmount = mkBvAndExpr(
            rightBv,
            mkBv(ECMASCRIPT_BITWISE_SHIFT_MASK, ECMASCRIPT_BITWISE_INTEGER_SIZE.toUInt())
        )
        val result = mkBvLogicalShiftRightExpr(leftBv, shiftAmount)

        return mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = result,
            signed = false,
        )
    }

    override fun visit(expr: EtsNullishCoalescingExpr): UExpr<out USort>? = with(ctx) {
        val left = resolve(expr.left) ?: return null
        val right = resolve(expr.right) ?: return null

        val leftIsNullish = mkNullishExpr(left, scope)

        // If both operands have the same sort, use mkIte directly
        if (left.sort == right.sort) {
            val commonSort = left.sort
            return mkIte(
                condition = leftIsNullish,
                trueBranch = right.asExpr(commonSort),
                falseBranch = left.asExpr(commonSort)
            )
        }

        // If sorts differ, create a fake object that can hold either value
        return iteWriteIntoFakeObject(scope, leftIsNullish, right, left)
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

    override fun visit(expr: EtsInExpr): UExpr<out USort>? = with(ctx) {
        val property = resolve(expr.left) ?: return null
        val obj = resolve(expr.right)?.asExpr(addressSort) ?: return null

        // Check for null/undefined access
        checkUndefinedOrNullPropertyRead(scope, obj, propertyName = "<in>") ?: return null

        logger.warn {
            "The 'in' operator is supported yet, the result may not be accurate"
        }

        // For now, just return a symbolic boolean (that can be true or false)
        scope.calcOnState {
            makeSymbolicPrimitive(boolSort)
        }
    }

    override fun visit(expr: EtsInstanceOfExpr): UExpr<out USort>? = with(ctx) {
        val arg = resolve(expr.arg)?.asExpr(addressSort) ?: return null
        scope.calcOnState {
            memory.types.evalIsSubtype(arg, expr.checkType)
        }
    }

    // endregion

    // region CALL

    override fun visit(expr: EtsInstanceCallExpr): UExpr<*>? = handleInstanceCall(expr)

    override fun visit(expr: EtsStaticCallExpr): UExpr<*>? = handleStaticCall(expr)

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

                if (isAllocatedConcreteHeapRef(ptr)) {
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

    override fun visit(value: EtsArrayAccess): UExpr<*>? = handleArrayAccess(value)

    override fun visit(value: EtsInstanceFieldRef): UExpr<*>? = handleInstanceFieldRef(value)

    override fun visit(value: EtsStaticFieldRef): UExpr<*>? = handleStaticFieldRef(value)

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
        check(isAllocatedConcreteHeapRef(obj)) {
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
            val clazz = scene.sdkClasses.filter { it.name == "Boolean" }.maxByOrNull { it.methods.size }
                ?: error("No Boolean class found in SDK")
            return@with scope.calcOnState { memory.allocConcrete(clazz.type) }
        }

        if (expr.type.typeName == "Number") {
            val clazz = scene.sdkClasses.filter { it.name == "Number" }.maxByOrNull { it.methods.size }
                ?: error("No Number class found in SDK")
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
                    mkBvToFpExpr(
                        sort = fp64Sort,
                        roundingMode = fpRoundingModeSortDefaultValue(),
                        value = bvSize,
                        signed = true,
                    ),
                    size.asExpr(fp64Sort)
                ),
                mkAnd(
                    mkBvSignedLessOrEqualExpr(mkBv(0), bvSize.asExpr(bv32Sort)),
                    mkBvSignedLessOrEqualExpr(bvSize.asExpr(bv32Sort), mkBv(Int.MAX_VALUE))
                )
            )

            scope.fork(
                condition,
                blockOnFalseState = { throwException("Invalid array size: ${size.asExpr(fp64Sort)}") }
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
) : EtsValue.Visitor<UExpr<out USort>?> {

    private fun resolveLocal(local: EtsValue): UExpr<*>? = with(ctx) {
        check(local is EtsLocal || local is EtsThis || local is EtsParameterRef) {
            "Expected EtsLocal, EtsThis, or EtsParameterRef, but got ${local::class.java}: $local"
        }

        // Handle closures
        if (local is EtsLocal && local.name.startsWith("%closures")) {
            // TODO: add comments
            val existingClosures = scope.calcOnState { closureObject[local.name] }
            if (existingClosures != null) {
                return existingClosures
            }
            val type = local.type
            check(type is EtsLexicalEnvType)
            val obj = allocateConcreteRef()
            // TODO: consider 'types.allocate'
            for (captured in type.closures) {
                val resolvedCaptured = resolveLocal(captured) ?: return null
                val lValue = mkFieldLValue(resolvedCaptured.sort, obj, captured.name)
                scope.doWithState {
                    memory.write(lValue, resolvedCaptured.cast(), guard = trueExpr)
                }
            }
            scope.doWithState {
                setClosureObject(local.name, obj)
            }
            return obj
        }

        val currentMethod = scope.calcOnState { lastEnteredMethod }

        // Locals in %dflt method are a little bit *special*...
        if (currentMethod.name == DEFAULT_ARK_METHOD_NAME) {
            val file = currentMethod.enclosingClass!!.declaringFile!!
            if (local is EtsLocal) {
                val name = local.name
                if (!name.startsWith("%") && !name.startsWith("_tmp") && name != "this") {
                    logger.info {
                        "Reading global variable in %dflt: $local in $file"
                    }
                    return readGlobal(scope, file, name)
                }
            }
        }

        // Get local index
        val idx = getLocalIdx(local, currentMethod)

        // If local is found in the current method:
        if (idx != null) {
            return scope.calcOnState {
                val sort = getOrPutSortForLocal(idx) {
                    val type = local.tryGetKnownType(currentMethod)
                    typeToSort(type).let {
                        if (it is TsUnresolvedSort) {
                            addressSort
                        } else {
                            it
                        }
                    }
                }
                val lValue = mkRegisterStackLValue(sort, idx)
                memory.read(lValue)
            }
        }

        // Local not found, either global or imported
        val file = currentMethod.enclosingClass!!.declaringFile!!
        val globals = file.getGlobals()

        require(local is EtsLocal) {
            "Only locals are supported here, but got ${local::class.java}: $local"
        }

        // If local is a global variable:
        if (globals.any { it.name == local.name }) {
            logger.info { "Reading global variable: $local in $file" }
            return readGlobal(scope, file, local.name)
        }

        // If local is an imported variable:
        val importInfo = file.importInfos.find { it.name == local.name }
        if (importInfo != null) {
            when (val resolutionResult = scene.resolveImportInfo(file, importInfo)) {
                is SymbolResolutionResult.Success -> {
                    val importedFile = resolutionResult.file
                    val importedName = resolutionResult.exportInfo.originalName
                    logger.info { "Reading imported variable: $importedName from $importedFile" }
                    return readGlobal(scope, importedFile, importedName)
                }

                is SymbolResolutionResult.FileNotFound -> {
                    logger.error { "Cannot resolve import for '$local': ${resolutionResult.reason}" }
                    scope.assert(falseExpr)
                    return null
                }

                is SymbolResolutionResult.SymbolNotFound -> {
                    logger.error { "Cannot find symbol '$local' in '${resolutionResult.file.name}': ${resolutionResult.reason}" }
                    scope.assert(falseExpr)
                    return null
                }
            }
        }

        logger.error { "Cannot resolve local variable '$local' in method: $currentMethod" }
        scope.assert(falseExpr)
        return null
    }

    override fun visit(local: EtsLocal): UExpr<out USort>? {
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

    override fun visit(value: EtsParameterRef): UExpr<out USort>? {
        return resolveLocal(value)
    }

    override fun visit(value: EtsThis): UExpr<out USort>? {
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
