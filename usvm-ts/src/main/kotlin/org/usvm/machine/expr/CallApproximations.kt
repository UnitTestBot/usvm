package org.usvm.machine.expr

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.initializeArray
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.getIntValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModelDispatcher
import org.usvm.machine.call.dispatch
import org.usvm.machine.call.hasBuiltinGlobalOwner
import org.usvm.machine.call.isDateReceiver
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.markResolved
import org.usvm.machine.interpreter.setResolvedValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.TsUnresolvedArrayKind
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.readUnresolvedArrayElement
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.copyArrayElements
import org.usvm.util.forEachArrayPayloadRegion
import org.usvm.util.initializeArrayKind
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.resolveEtsMethods

private val logger = KotlinLogging.logger {}
private val legacyArrayMethods = setOf("concat", "fill", "join", "push", "reverse", "slice", "unshift")

internal fun TsExprResolver.tryApproximateGlobalInstanceCall(
    expr: EtsInstanceCallExpr,
): TsExprApproximationResult = with(ctx) {
    // Mock all calls to `Logger` methods
    if (expr.instance.name == "Logger") {
        return from(mkUndefinedValue())
    }

    // Handle `Number` calls.
    if (hasBuiltinGlobalOwner(owner = expr.instance, callee = expr.callee, expectedName = "Number")) {
        when (expr.callee.name) {
            "isFinite", "isInteger", "isSafeInteger" -> {
                return tryDispatchNumericBuiltin(expr)
                    ?: TsExprApproximationResult.NoApproximation
            }

            "isNaN" -> return tryDispatchNumericBuiltin(expr)
                ?: from(handleNumberIsNaN(expr))
        }
    }

    // Handle 'Boolean' constructor calls
    if (expr.callee.enclosingClass.name == "Boolean" && expr.callee.name == CONSTRUCTOR_NAME) {
        return from(handleBooleanConstructor(expr))
    }

    // Handle `Promise` constructor calls
    if (expr.callee.enclosingClass.name == "Promise" && expr.callee.name == CONSTRUCTOR_NAME) {
        return from(handlePromiseConstructor(expr))
    }

    // Handle `Promise.resolve(value)` and `Promise.reject(reason)` calls
    if (expr.instance.name == "Promise") {
        if (expr.callee.name in listOf("resolve", "reject")) {
            return from(handlePromiseResolveReject(expr))
        }
    }

    // Handle `Math` method calls.
    if (hasBuiltinGlobalOwner(owner = expr.instance, callee = expr.callee, expectedName = "Math")) {
        when (expr.callee.name) {
            "abs", "ceil", "max", "min", "round", "sqrt", "trunc" -> {
                return tryDispatchNumericBuiltin(expr)
                    ?: TsExprApproximationResult.NoApproximation
            }

            "floor" -> return tryDispatchNumericBuiltin(expr)
                ?: from(handleMathFloor(expr))
        }
    }

    return TsExprApproximationResult.NoApproximation
}

private fun TsExprResolver.tryDispatchNumericBuiltin(
    expr: EtsInstanceCallExpr,
): TsExprApproximationResult? {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        return null
    }
    val resolvedArguments = buildList {
        for (argument in expr.args) {
            add(resolve(argument) ?: return TsExprApproximationResult.ResolveFailure)
        }
    }

    dispatcher.dispatch(
        scope = scope,
        call = expr,
        callSite = scope.calcOnState { lastStmt },
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        resolvedArguments = resolvedArguments,
    )

    return TsExprApproximationResult.ResolveFailure
}

internal fun TsExprResolver.tryApproximateInstanceCall(
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult = with(ctx) {
    val expr = stmt.call
    val instance = stmt.instance

    // Mock `.toString()` method calls
    if (expr.callee.name == "toString") {
        if (unknownCallDispatcher is TsUnknownCallModelDispatcher) {
            dispatchLegacyInstanceCall(stmt)
            return TsExprApproximationResult.ResolveFailure
        }

        if (expr.args.isNotEmpty()) {
            logger.warn { "toString() should have no arguments, but got ${expr.args.size}" }
        }
        return from(mkStringConstant("I am a string", scope))
    }

    // Handle `.valueOf()` method calls
    if (expr.callee.name == "valueOf") {
        val receiverIsDate = scope.calcOnState { isDateReceiver(expr, instance) }
        if (!receiverIsDate) {
            return from(handleValueOf(expr, instance))
        }
    }

    if (instance.sort != addressSort) return TsExprApproximationResult.NoApproximation

    val array = instance.asExpr(addressSort)
    val instanceType = scope.calcOnState { arrayStorageType(array, expr.instance.type) }

    if (instanceType is EtsArrayType) {
        val elementSort = typeToSort(instanceType.elementType)
            .takeIf { it !is TsUnresolvedSort }
            ?: addressSort

        if (expr.callee.name in legacyArrayMethods && unknownCallDispatcher is TsUnknownCallModelDispatcher) {
            dispatchArrayModel(stmt)
            return TsExprApproximationResult.ResolveFailure
        }

        // Handle 'Array.push()' method calls
        if (expr.callee.name == "push") {
            return from(handleArrayPush(expr, instanceType, elementSort, array))
        }

        // Handle `Array.pop() method calls
        if (expr.callee.name == "pop") {
            return handleArrayPopCall(stmt, instanceType, elementSort)
        }

        // Handle `Array.fill() method calls
        if (expr.callee.name == "fill") {
            return from(handleArrayFill(expr, instanceType, elementSort, array))
        }

        // Handle `Array.unshift() method calls
        if (expr.callee.name == "unshift") {
            return from(handleArrayUnshift(expr, instanceType, array))
        }

        // Handle `Array.shift() method calls
        if (expr.callee.name == "shift") {
            return handleArrayShiftCall(stmt, instanceType, elementSort)
        }

        // Handle `Array.join() method calls
        if (expr.callee.name == "join") {
            return from(handleArrayJoin(expr))
        }

        // Handle `Array.slice() method calls
        if (expr.callee.name == "slice") {
            return from(handleArraySlice(expr, instanceType, array))
        }

        // Handle `Array.concat() method calls
        if (expr.callee.name == "concat") {
            return handleArrayConcat(stmt, instanceType, array)
        }

        // Handle Array search and indexed access method calls.
        if (expr.callee.name in setOf("indexOf", "lastIndexOf")) {
            return handleArrayIndexSearchCall(stmt, instanceType, elementSort, array)
        }

        // Handle `Array.includes() method calls
        if (expr.callee.name == "includes") {
            return handleArrayIncludesCall(stmt)
        }

        // Handle `Array.reverse() method calls
        if (expr.callee.name == "reverse") {
            return from(handleArrayReverse(expr, instanceType, array))
        }
    }

    val modeledStringMethods = setOf(
        "replaceAll",
        "substring",
        "trim",
        "trimStart",
        "trimEnd",
        "charAt",
        "charCodeAt",
        "endsWith",
        "includes",
        "indexOf",
        "lastIndexOf",
        "slice",
        "startsWith",
        "toLowerCase",
        "toUpperCase",
    )
    if (instanceType is EtsStringType && expr.callee.name in modeledStringMethods) {
        val dispatcher = unknownCallDispatcher
        if (dispatcher !is TsUnknownCallModelDispatcher) {
            return TsExprApproximationResult.NoApproximation
        }

        dispatcher.dispatch(
            scope = scope,
            call = stmt.call,
            callSite = stmt.returnSite,
            failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
            callee = stmt.call.callee.withEnclosingClassName("String"),
            resolvedReceiver = stmt.instance,
            resolvedArguments = stmt.args,
        )

        return TsExprApproximationResult.ResolveFailure
    }

    return TsExprApproximationResult.NoApproximation
}

private fun TsExprResolver.handleArrayIndexSearchCall(
    stmt: TsVirtualMethodCallStmt,
    instanceType: EtsArrayType,
    elementSort: USort,
    array: UHeapRef,
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        if (stmt.call.callee.name != "indexOf") {
            return TsExprApproximationResult.NoApproximation
        }

        return from(handleArrayIndexOf(stmt.call, instanceType, elementSort, array))
    }

    dispatchArrayModel(stmt)
    return TsExprApproximationResult.ResolveFailure
}

private fun TsExprResolver.handleArrayIncludesCall(
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        return from(handleArrayIncludes(stmt.call))
    }

    dispatchArrayModel(stmt)
    return TsExprApproximationResult.ResolveFailure
}

private fun TsExprResolver.dispatchArrayModel(stmt: TsVirtualMethodCallStmt) {
    unknownCallDispatcher.dispatch(
        scope = scope,
        call = stmt.call,
        callSite = stmt.returnSite,
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        callee = stmt.call.callee.withEnclosingClassName("Array"),
        resolvedReceiver = stmt.instance,
        resolvedArguments = stmt.args,
    )
}

private fun TsExprResolver.dispatchLegacyInstanceCall(stmt: TsVirtualMethodCallStmt) {
    unknownCallDispatcher.dispatch(
        scope = scope,
        call = stmt.call,
        callSite = stmt.returnSite,
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        resolvedReceiver = stmt.instance,
        resolvedArguments = stmt.args,
    )
}

private fun EtsMethodSignature.withEnclosingClassName(name: String): EtsMethodSignature =
    copy(enclosingClass = enclosingClass.copy(name = name))

private fun TsExprResolver.handleArrayPopCall(
    stmt: TsVirtualMethodCallStmt,
    instanceType: EtsArrayType,
    elementSort: USort,
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        return from(handleArrayPop(stmt, instanceType, elementSort, stmt.instance.asExpr(ctx.addressSort)))
    }

    dispatcher.dispatch(
        scope,
        stmt,
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        resolvedReceiver = stmt.instance,
    )

    return TsExprApproximationResult.ResolveFailure
}

private fun TsExprResolver.handleArrayShiftCall(
    stmt: TsVirtualMethodCallStmt,
    instanceType: EtsArrayType,
    elementSort: USort,
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        return from(handleArrayShift(stmt, instanceType, elementSort, stmt.instance.asExpr(ctx.addressSort)))
    }

    dispatcher.dispatch(
        scope,
        stmt,
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
        resolvedReceiver = stmt.instance,
    )

    return TsExprApproximationResult.ResolveFailure
}

private fun TsExprResolver.handleValueOf(expr: EtsInstanceCallExpr, instance: UExpr<*>): UExpr<*> {
    if (expr.args.isNotEmpty()) {
        logger.warn { "valueOf() should have no arguments, but got ${expr.args.size}" }
    }

    return instance
}

private fun TsExprResolver.handleNumberIsNaN(expr: EtsInstanceCallExpr): UBoolExpr? = with(ctx) {
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

private fun TsExprResolver.handleBooleanConstructor(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Boolean constructor should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    mkTruthyExpr(arg, scope)
}

private fun TsExprResolver.handlePromiseConstructor(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
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

private fun TsExprResolver.handlePromiseResolveReject(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
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

private fun TsExprResolver.handleMathFloor(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Math.floor() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    when (arg.sort) {
        fp64Sort -> mkFpRoundToIntegralExpr(
            roundingMode = mkFpRoundingModeExpr(KFpRoundingMode.RoundTowardNegative),
            value = arg.asExpr(fp64Sort),
        )

        sizeSort -> arg.asExpr(sizeSort)

        else -> {
            logger.warn { "Unsupported argument sort for Math.floor(): ${arg.sort}" }
            null
        }
    }
}

/**
 * Handles the `Array.push(...items)` method call.
 * Appends the specified `items` to the end of the array.
 * This method modifies the array in place and returns the new length of the array.
 *
 * ### Examples:
 * ```
 * let a = [10, 20];
 *
 * a.push(30) -> 3
 * a == [10, 20, 30]
 *
 * a.push(5, 4, 3) -> 6
 * a == [10, 20, 30, 5, 4, 3]
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.push
 */
private fun TsExprResolver.handleArrayPush(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Array.push() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    scope.calcOnState {
        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Increase the length of the array
        val newLength = mkBvAddExpr(length, mkBv(1))
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Write the new element to the end of the array
        assignToArrayIndex(
            scope = scope,
            array = array,
            index = length,
            expr = arg,
            arrayType = arrayType,
        ) ?: return@calcOnState null

        // Return the new length of the array (as per ECMAScript spec for Array.push)
        mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = newLength.asExpr(sizeSort),
            signed = true,
        )
    }
}

/**
 * Handles the `Array.pop()` method call.
 * Pops the last element from the array and returns it.
 * If the array is empty, it returns `undefined`.
 *
 * ### Examples:
 * ```
 * let a = [10, 20, 30];
 *
 * a.pop() -> 30 // last element
 * a == [10, 20] // modified in place
 *
 * a.pop() -> 20 // last element
 * a == [10] // modified in place
 *
 * let empty = [];
 * empty.pop() -> undefined // no elements to pop
 * empty == [] // still empty
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.pop
 */
private fun TsExprResolver.handleArrayPop(
    stmt: TsVirtualMethodCallStmt,
    arrayType: EtsArrayType,
    elementSort: USort,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(stmt.args.isEmpty()) {
        "Array.pop() should have no arguments, but got ${stmt.args.size}"
    }

    removeArrayElement(stmt, array, arrayType, elementSort, first = false)
}

private fun TsExprResolver.removeArrayElement(
    stmt: TsVirtualMethodCallStmt,
    array: UHeapRef,
    arrayType: EtsArrayType,
    elementSort: USort,
    first: Boolean,
): UExpr<*>? = with(ctx) {
    val lengthLValue = mkArrayLengthLValue(array, arrayType)
    val length = scope.calcOnState { memory.read(lengthLValue) }
    val nonEmpty = mkNot(mkEq(length, mkBv(0)))
    scope.fork(
        nonEmpty,
        blockOnFalseState = {
            methodResult = TsMethodResult.Success.MockedCall(mkUndefinedValue(), stmt.call.callee)
            newStmt(stmt.returnSite)
        },
    ) ?: return null

    scope.calcOnState {
        val newLength = mkBvSubExpr(length, mkBv(1))
        val index = if (first) mkBv(0) else newLength
        val removed = if (typeToSort(arrayType.elementType) is TsUnresolvedSort) {
            mkFakeValue(scope, readUnresolvedArrayElement(memory, array, index))
        } else {
            memory.read(mkArrayIndexLValue(elementSort, array, index, arrayType))
        }

        if (first) {
            copyArrayElements(array, array, arrayType, fromSrc = mkBv(1), fromDst = mkBv(0), length = newLength)
        }
        memory.write(lengthLValue, newLength, guard = trueExpr)

        removed
    }
}

/**
 * Handles the `Array.fill(value [,start [,end]])` method call.
 * Fills the array with the specified `value` from the `start` index to the `end` index.
 * If `start` index is not provided, it defaults to `0`.
 * If `end` index is not provided, it defaults to the length of the array.
 * This method modifies the array in place and returns it.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3, 4, 5];
 *
 * a.fill(0) -> [0, 0, 0, 0, 0] (fill entire array)
 * a == [0, 0, 0, 0, 0]
 *
 * let b = [1, 2, 3, 4, 5];
 * b.fill(9, 2) -> [1, 2, 9, 9, 9] (fill from index 2 to end)
 * b == [1, 2, 9, 9, 9]
 *
 * let c = [1, 2, 3, 4, 5];
 * c.fill(8, 1, 3) -> [1, 8, 8, 4, 5] (fill from index 1 to 3)
 * c == [1, 8, 8, 4, 5]
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.fill
 */
private fun TsExprResolver.handleArrayFill(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(expr.args.size >= 1 && expr.args.size <= 3) {
        "Array.fill() should have 1 to 3 arguments, but got ${expr.args.size}"
    }
    val resolvedValue = resolve(expr.args[0]) ?: return null
    val value = if (typeToSort(arrayType.elementType) is TsUnresolvedSort) {
        resolvedValue.toFakeObject(scope)
    } else {
        resolvedValue
    }

    // TODO: Support negative `start` and `end` indices.
    val start = if (expr.args.size > 1) {
        resolve(expr.args[1]) ?: return null
    } else {
        mkBv(0)
    }
    val startBv: UExpr<TsSizeSort> = when (start.sort) {
        sizeSort -> start.asExpr(sizeSort)

        fp64Sort -> mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = start.asExpr(fp64Sort),
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)

        else -> {
            logger.warn { "Unsupported sort for `start` index in Array.fill(): ${start.sort}" }
            return null
        }
    }

    val end = if (expr.args.size > 2) {
        resolve(expr.args[2]) ?: return null
    } else {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        scope.calcOnState { memory.read(lengthLValue) }
    }
    val endBv: UExpr<TsSizeSort> = when (end.sort) {
        sizeSort -> end.asExpr(sizeSort)

        fp64Sort -> mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = end.asExpr(fp64Sort),
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)

        else -> {
            logger.warn { "Unsupported sort for `end` index in Array.fill(): ${end.sort}" }
            return null
        }
    }

    scope.calcOnState {
        val descriptor = arrayDescriptorOf(arrayType)

        // Calculate the length of the range to fill
        val fillLength = mkBvSubExpr(endBv, startBv)

        // Concrete ranges need no unused entries in the temporary array.
        val tempSize = getIntValue(fillLength)?.coerceIn(0, ARRAY_FILL_MAX_SIZE) ?: ARRAY_FILL_MAX_SIZE
        // TODO: check that symbolic `fillLength` is less than `ARRAY_FILL_MAX_SIZE`.

        // Allocate a temporary array to hold the filled values
        val tempArray = memory.allocConcrete(descriptor)

        // Fill the temporary array with the specified `value`
        memory.initializeArray(
            tempArray,
            descriptor,
            elementSort,
            sizeSort,
            (0 until tempSize).asSequence().map { value.asExpr(elementSort) }
        )

        // Copy the filled values to the specified range in the original array
        memory.memcpy(
            srcRef = tempArray,
            dstRef = array,
            type = descriptor,
            elementSort = elementSort,
            fromSrc = mkBv(0),
            fromDst = startBv,
            length = fillLength,
        )

        // Return the modified original array
        array
    }
}

private const val ARRAY_FILL_MAX_SIZE = 10_000

/**
 * Handle the `Array.shift()` method call.
 * Removes the first element from the array and returns it.
 * If the array is empty, it returns `undefined`.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3];
 *
 * a.shift() -> 1 (removed element)
 * a == [2, 3] // modified in place
 *
 * a.shift() -> 2 (removed element)
 * a == [3] // modified in place
 *
 * let empty = [];
 * empty.shift() -> undefined
 * empty == [] // still empty
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.shift
 */
private fun TsExprResolver.handleArrayShift(
    stmt: TsVirtualMethodCallStmt,
    arrayType: EtsArrayType,
    elementSort: USort,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(stmt.args.isEmpty()) {
        "Array.shift() should have no arguments, but got ${stmt.args.size}"
    }

    removeArrayElement(stmt, array, arrayType, elementSort, first = true)
}

/**
 * Handle the `Array.unshift(...items)` method call.
 * Prepends the specified `items` to the start of the array.
 * This method modifies the array in place and returns the new length of the array.
 *
 * ### Examples:
 * ```
 * let a = [2, 3];
 *
 * a.unshift(1) -> 3 (new length)
 * a == [1, 2, 3] // modified in place
 *
 * a.unshift(0) -> 4 (new length)
 * a == [0, 1, 2, 3] // modified in place
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.unshift
 */
private fun TsExprResolver.handleArrayUnshift(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    // TODO: support vararg
    check(expr.args.size == 1) {
        "Array.unshift() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    scope.calcOnState {
        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Increase the length of the array
        val newLength = mkBvAddExpr(length, mkBv(1))
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Shift elements to the right
        copyArrayElements(
            srcRef = array,
            dstRef = array,
            arrayType = arrayType,
            fromSrc = mkBv(0),
            fromDst = mkBv(1),
            length = length,
        )

        // Write the new element to the start of the array
        assignToArrayIndex(scope, array, index = mkBv(0), expr = arg, arrayType = arrayType)
            ?: return@calcOnState null

        // Return the new length of the array (as per ECMAScript spec for Array.unshift)
        mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = newLength.asExpr(sizeSort),
            signed = true,
        )
    }
}

/**
 * Handles the `Array.join(separator)` method call.
 * Joins all elements of the array into a string, separated by the specified `separator`.
 * If `separator` is not provided, it defaults to `","`.
 * This method returns the resulting string.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3];
 *
 * a.join() -> "1,2,3" (default comma separator)
 * a.join("-") -> "1-2-3" (custom separator)
 * a.join("") -> "123" (no separator)
 *
 * let b = ["hello", "world"];
 * b.join(" ") -> "hello world"
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.join
 */
private fun TsExprResolver.handleArrayJoin(
    expr: EtsInstanceCallExpr,
): UExpr<*>? = with(ctx) {
    check(expr.args.size <= 1) {
        "Array.join() should have at most one argument, but got ${expr.args.size}"
    }

    // For simplicity, we return a symbolic string constant
    // A full implementation would need to handle element conversion and concatenation symbolically
    logger.warn {
        "Array.join() is not fully implemented, returning a symbolic string constant"
    }
    scope.calcOnState {
        mkStringConstant(ARRAY_JOIN_RESULT, scope)
    }
}

private const val ARRAY_JOIN_RESULT = "joined_array_result"

/**
 * Handles the `Array.slice(start, end)` method call.
 * Returns a shallow copy of a portion of the array from the `start` index to the `end` index.
 * The `start` index is inclusive, and the `end` index is exclusive.
 * If `start` is not provided, it defaults to `0`.
 * If `end` is not provided, it defaults to the length of the array.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3, 4, 5];
 *
 * a.slice() -> [1, 2, 3, 4, 5] (copy entire array)
 * a.slice(2) -> [3, 4, 5] (from index 2 to end)
 * a.slice(1, 4) -> [2, 3, 4] (from index 1 to 4, exclusive)
 * a.slice(-2) -> [4, 5] (last 2 elements)
 *
 * a == [1, 2, 3, 4, 5] // original array not modified
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.slice
 */
private fun TsExprResolver.handleArraySlice(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(expr.args.size <= 2) {
        "Array.slice() should have at most two arguments, but got ${expr.args.size}"
    }

    val start = if (expr.args.isNotEmpty()) {
        resolve(expr.args[0]) ?: return null
    } else {
        mkBv(0)
    }
    val startBv: UExpr<TsSizeSort> = when (start.sort) {
        sizeSort -> start.asExpr(sizeSort)

        fp64Sort -> mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = start.asExpr(fp64Sort),
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)

        else -> {
            logger.warn { "Unsupported sort for `start` index in Array.slice(): ${start.sort}" }
            return null
        }
    }

    val end = if (expr.args.size > 1) {
        resolve(expr.args[1]) ?: return null
    } else {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        scope.calcOnState { memory.read(lengthLValue) }
    }
    val endBv: UExpr<TsSizeSort> = when (end.sort) {
        sizeSort -> end.asExpr(sizeSort)

        fp64Sort -> mkFpToBvExpr(
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = end.asExpr(fp64Sort),
            bvSize = sizeSort.sizeBits.toInt(),
            isSigned = true,
        ).asExpr(sizeSort)

        else -> {
            logger.warn { "Unsupported sort for `end` index in Array.slice(): ${end.sort}" }
            return null
        }
    }

    scope.calcOnState {
        val descriptor = arrayDescriptorOf(arrayType)

        val length = memory.read(mkArrayLengthLValue(array, arrayType))
        val zero = mkBv(0)

        fun normalizeIndex(index: UExpr<TsSizeSort>): UExpr<TsSizeSort> {
            val relative = mkIte(mkBvSignedLessExpr(index, zero), mkBvAddExpr(length, index), index)
            val capped = mkIte(mkBvSignedGreaterExpr(relative, length), length, relative)
            return mkIte(mkBvSignedLessExpr(relative, zero), zero, capped)
        }

        val from = normalizeIndex(startBv)
        val to = normalizeIndex(endBv)
        val newLength = mkIte(mkBvSignedLessExpr(from, to), mkBvSubExpr(to, from), zero)

        // Allocate a new array for the slice
        val slicedArray = memory.allocConcrete(descriptor)

        // Copy the specified range from the original array to the new array
        copyArrayElements(
            srcRef = array,
            dstRef = slicedArray,
            arrayType = arrayType,
            fromSrc = from,
            fromDst = mkBv(0),
            length = newLength,
        )

        memory.write(mkArrayLengthLValue(slicedArray, arrayType), newLength, guard = trueExpr)

        // Return the new array containing the slice
        slicedArray
    }
}

/**
 * Handles the `Array.concat(...items)` method call.
 * Returns a new array containing the elements of the original array followed by the array elements of each argument.
 *
 * ### Examples:
 * ```
 * let a = [1, 2];
 * let b = [3, 4];
 *
 * a.concat() -> [1, 2] // same array
 * a.concat(b) -> [1, 2, 3, 4] // concat two arrays
 * a.concat(b, 5) -> [1, 2, 3, 4, 5] // concat with an array and a single element
 *
 * a == [1, 2] // not modified
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.concat
 */
private fun TsExprResolver.handleArrayConcat(
    stmt: TsVirtualMethodCallStmt,
    arrayType: EtsArrayType,
    array: UHeapRef,
): TsExprApproximationResult = with(ctx) {
    val elementSort = typeToSort(arrayType.elementType)
    val arrayTypes = stmt.args.mapIndexed { index, arg ->
        if (arg.sort == addressSort) {
            val ref = arg.asExpr(addressSort)
            // A fake or an untyped reference may itself contain an array; spreading requires runtime dispatch.
            if (ref.hasFakeValueBranch()) return TsExprApproximationResult.NoApproximation
            val type = scope.calcOnState { arrayStorageType(ref, stmt.call.args[index].type) }
            if (typeToSort(type) is TsUnresolvedSort) return TsExprApproximationResult.NoApproximation
            type as? EtsArrayType
        } else {
            null
        }
    }
    if (arrayTypes.withIndex().any { (index, type) ->
            if (type != null) {
                typeToSort(type.elementType) != elementSort
            } else {
                elementSort !is TsUnresolvedSort && stmt.args[index].sort != elementSort
            }
        }
    ) {
        logger.debug { "Array.concat requires conversion between different element storage sorts" }
        return TsExprApproximationResult.NoApproximation
    }

    from(
        scope.calcOnState {
            val resultArray = memory.allocConcrete(arrayDescriptorOf(arrayType))
            val originalLength = memory.read(mkArrayLengthLValue(array, arrayType))
            copyArrayElements(
                srcRef = array,
                dstRef = resultArray,
                arrayType = arrayType,
                fromSrc = mkBv(0),
                fromDst = mkBv(0),
                length = originalLength,
            )

            var totalLength = originalLength
            stmt.args.forEachIndexed { index, arg ->
                val argType = arrayTypes[index]
                if (argType != null) {
                    val ref = arg.asExpr(addressSort)
                    val length = memory.read(mkArrayLengthLValue(ref, argType))
                    copyArrayElements(
                        srcRef = ref,
                        dstRef = resultArray,
                        arrayType = argType,
                        fromSrc = mkBv(0),
                        fromDst = totalLength,
                        length = length,
                    )
                    totalLength = mkBvAddExpr(totalLength, length)
                } else {
                    val newLength = mkBvAddExpr(totalLength, mkBv(1))
                    memory.write(mkArrayLengthLValue(resultArray, arrayType), newLength, guard = trueExpr)
                    assignToArrayIndex(scope, resultArray, totalLength, arg, arrayType) ?: return@calcOnState null
                    totalLength = newLength
                }
            }
            memory.write(mkArrayLengthLValue(resultArray, arrayType), totalLength, guard = trueExpr)

            resultArray
        }
    )
}

/**
 * Handles the `Array.indexOf(searchElement)` method call.
 * Returns the index of the first occurrence of `searchElement` in the array, or `-1` if not found.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3, 2, 4];
 *
 * a.indexOf(2) -> 1 (first occurrence at index 1)
 * a.indexOf(5) -> -1 (not found)
 * a.indexOf(3) -> 2 (found at index 2)
 *
 * let b = ["hello", "world", "hello"];
 * b.indexOf("hello") -> 0 (first occurrence)
 * b.indexOf("foo") -> -1 (not found)
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.indexof
 */
private fun TsExprResolver.handleArrayIndexOf(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Array.indexOf() should have exactly one argument, but got ${expr.args.size}"
    }
    val searchElement = resolve(expr.args.single()) ?: return null

    logger.warn {
        "Array.indexOf() is not fully implemented, returning a symbolic index approximation"
    }

    scope.calcOnState {
        // For symbolic execution, we return a symbolic result that could be any valid index or -1
        // A more precise implementation would require symbolic iteration
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Create a symbolic index that is either -1 (not found) or a valid index
        val symbolicResult = makeSymbolicPrimitive(sizeSort)

        // Add constraints: result is either -1 or in range [0, length-1]
        val notFound = mkEq(symbolicResult, mkBv(-1))
        val validIndex = mkAnd(
            mkBvSignedGreaterOrEqualExpr(symbolicResult, mkBv(0)),
            mkBvSignedLessExpr(symbolicResult, length)
        )
        pathConstraints += mkOr(notFound, validIndex)

        // Convert to fp64 for return
        mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = symbolicResult.asExpr(sizeSort),
            signed = true
        )
    }
}

/**
 * Handles the `Array.includes(searchElement)` method call.
 * Returns `true` if the array contains the `searchElement`, and `false` otherwise.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3, 4, 5];
 *
 * a.includes(3) -> true (element found)
 * a.includes(6) -> false (element not found)
 * a.includes(1) -> true (first element)
 *
 * let b = ["hello", "world"];
 * b.includes("hello") -> true (string found)
 * b.includes("foo") -> false (string not found)
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.includes
 */
private fun TsExprResolver.handleArrayIncludes(
    expr: EtsInstanceCallExpr,
): UExpr<*>? = with(ctx) {
    check(expr.args.size == 1) {
        "Array.includes() should have exactly one argument, but got ${expr.args.size}"
    }
    val searchElement = resolve(expr.args.single()) ?: return null

    logger.warn {
        "Array.includes() is not fully implemented, returning a symbolic boolean approximation"
    }

    scope.calcOnState {
        // For symbolic execution, return a symbolic boolean result
        // A more precise implementation would require symbolic iteration or constraint solving
        val symbolicResult = memory.mocker.createMockSymbol(null, boolSort, ownership)

        // The result is unconstrained - could be true or false
        // In a more sophisticated implementation, we would add constraints based on
        // array contents and search element

        symbolicResult
    }
}

/**
 * Handles the `Array.reverse()` method call.
 * Reverses the elements of the array in place and returns the modified array.
 *
 * ### Examples:
 * ```
 * let a = [1, 2, 3, 4, 5];
 *
 * a.reverse() -> [5, 4, 3, 2, 1] (reversed array)
 * a == [5, 4, 3, 2, 1] // modified in place
 *
 * let b = ["hello", "world"];
 * b.reverse() -> ["world", "hello"]
 * b == ["world", "hello"] // modified in place
 *
 * let empty = [];
 * empty.reverse() -> [] // still empty
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.reverse
 */
private fun TsExprResolver.handleArrayReverse(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    check(expr.args.isEmpty()) {
        "Array.reverse() should have no arguments, but got ${expr.args.size}"
    }

    scope.calcOnState {
        val descriptor = arrayDescriptorOf(arrayType)

        // Allocate a new array to represent the reversed result
        val reversedArray = memory.allocConcrete(descriptor)

        // Read the length of the original array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        val reversedIndices = (0 until ARRAY_REVERSE_MAX_SIZE).map { index ->
            mkBvSubExpr(mkBvSubExpr(length, mkBv(1)), index.toBv())
        }
        forEachArrayPayloadRegion(arrayType) { region, sort ->
            val contents = reversedIndices.asSequence().map { index ->
                memory.readArrayIndex(array, index, region, sort)
            }
            memory.initializeArray(reversedArray, region, sort, sizeSort, contents)
        }
        if (typeToSort(arrayType.elementType) is TsUnresolvedSort) {
            TsUnresolvedArrayKind.entries.forEach { kind ->
                val contents = reversedIndices.map { index -> memory.readArrayIndex(array, index, kind, boolSort) }
                initializeArrayKind(reversedArray, kind, contents)
            }
        }

        copyArrayElements(
            arrayType = arrayType,
            srcRef = reversedArray,
            dstRef = array,
            fromSrc = mkBv(0),
            fromDst = mkBv(0),
            length = length
        )

        // Return the modified original array
        array
    }
}

private const val ARRAY_REVERSE_MAX_SIZE = 5000
