package org.usvm.machine.expr

import io.ksmt.expr.KFpRoundingMode
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.initializeArray
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.typeStreamOf
import org.usvm.collection.array.UArrayRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.getIntValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsSizeSort
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.call.TsBuiltInUnknownCallModels
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModelDispatcher
import org.usvm.machine.call.dispatch
import org.usvm.machine.call.hasBuiltinGlobalOwner
import org.usvm.machine.call.intrinsic.TsDateEtsIrModelFamily
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.createSettledPromise
import org.usvm.machine.interpreter.existingPromiseOrEnsureNonThenable
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.newStmt
import org.usvm.machine.types.TsUnresolvedArrayKind
import org.usvm.sizeSort
import org.usvm.types.TypesResult
import org.usvm.util.TsTypedArrayStorage
import org.usvm.util.arrayStorageType
import org.usvm.util.boxArrayValue
import org.usvm.util.componentType
import org.usvm.util.copyArrayElements
import org.usvm.util.copyKnownArrayIndexPresence
import org.usvm.util.forEachArrayPayloadRegion
import org.usvm.util.initializeArrayKind
import org.usvm.util.initializeTypedArrayOverrides
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.moveInitializedArrayIndices
import org.usvm.util.resolveEtsMethods
import org.usvm.util.truncateKnownArrayIndices
import org.usvm.util.typedArrayBoxedLValue
import org.usvm.util.typedArrayOverrideLValue

private val logger = KotlinLogging.logger {}

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
            "isFinite", "isInteger", "isSafeInteger", "parseFloat" -> {
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
    if (!ensureSupportedObjectMutation(stmt)) return TsExprApproximationResult.ResolveFailure

    val iterator = tryApproximateBuiltinIteratorCall(stmt)
    if (iterator !is TsExprApproximationResult.NoApproximation) return iterator

    // The model catalog has no toString target; project methods still need normal dispatch.
    if (expr.callee.name == "toString") {
        if (unknownCallDispatcher is TsUnknownCallModelDispatcher) {
            if (hasProjectToStringTarget(stmt)) return TsExprApproximationResult.NoApproximation

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
        val receiverIsDate = scope.calcOnState {
            TsDateEtsIrModelFamily.isDateReceiver(state = this, receiver = instance)
        }
        if (!receiverIsDate) {
            return from(handleValueOf(expr, instance))
        }
    }

    if (instance.sort != addressSort) return TsExprApproximationResult.NoApproximation

    val array = instance.asExpr(addressSort)
    val instanceType = scope.calcOnState { arrayStorageType(array, expr.instance.type) }

    if (instanceType is EtsArrayType) {
        val elementSort = typeToSort(instanceType.componentType)
            .takeIf { it !is TsUnresolvedSort }
            ?: addressSort

        val usesModelDispatcher = unknownCallDispatcher is TsUnknownCallModelDispatcher
        val modeledArrayCall = usesModelDispatcher && TsBuiltInUnknownCallModels.hasPartialApproximationModel(
            methodName = expr.callee.name,
            enclosingClassName = "Array",
            allowUnqualifiedTarget = true,
        )

        if (modeledArrayCall) {
            dispatchArrayModel(stmt)
            return TsExprApproximationResult.ResolveFailure
        }

        // Handle 'Array.push()' method calls
        if (expr.callee.name == "push") {
            return from(handleArrayPush(stmt, instanceType, array))
        }

        // Handle `Array.pop() method calls
        if (expr.callee.name == "pop") {
            return handleArrayPopCall(stmt, instanceType)
        }

        // Handle `Array.fill() method calls
        if (expr.callee.name == "fill") {
            return from(handleArrayFill(expr, instanceType, elementSort, array))
        }

        // Handle `Array.unshift() method calls
        if (expr.callee.name == "unshift") {
            return from(handleArrayUnshift(stmt, instanceType, array))
        }

        // Handle `Array.shift() method calls
        if (expr.callee.name == "shift") {
            return handleArrayShiftCall(stmt, instanceType)
        }

        // Handle `Array.join() method calls
        if (expr.callee.name == "join") {
            return from(handleArrayJoin())
        }

        // Handle `Array.slice() method calls
        if (expr.callee.name == "slice") {
            return from(handleArraySlice(stmt, instanceType, array))
        }

        // Handle `Array.concat() method calls
        if (expr.callee.name == "concat") {
            return handleArrayConcat(stmt, instanceType, array)
        }

        // Handle Array search and indexed access method calls.
        if (expr.callee.name in setOf("indexOf", "lastIndexOf")) {
            return handleArrayIndexSearchCall(stmt, instanceType, array)
        }

        // Handle `Array.includes() method calls
        if (expr.callee.name == "includes") {
            return handleArrayIncludesCall(stmt)
        }

        // Handle `Array.reverse() method calls
        if (expr.callee.name == "reverse") {
            return from(handleArrayReverse(instanceType, array))
        }
    }

    val dispatcher = unknownCallDispatcher
    val modeledStringCall = instanceType is EtsStringType && dispatcher is TsUnknownCallModelDispatcher &&
        TsBuiltInUnknownCallModels.hasPartialApproximationModel(
            methodName = expr.callee.name,
            enclosingClassName = "String",
        )
    if (modeledStringCall) {
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

private fun TsExprResolver.hasProjectToStringTarget(stmt: TsVirtualMethodCallStmt): Boolean = with(ctx) {
    if (stmt.instance.sort != addressSort) return false

    val receiver = stmt.instance.asExpr(addressSort)
    val concreteReceiver = isAllocatedConcreteHeapRef(receiver)
    val candidateOwners = resolveEtsMethods(stmt.call.callee)
        .filterNot { it.isStatic }
        .mapNotNull { it.enclosingClass }
        .filterTo(mutableSetOf()) { it in scene.projectClasses }
    if (!concreteReceiver && !stmt.call.isSuperCall && candidateOwners.isEmpty()) return false

    val possibleTypes = scope.calcOnState {
        memory.typeStreamOf(receiver).take(scene.projectAndSdkClasses.size + 1)
    }
    if (possibleTypes !is TypesResult.SuccessfulTypesResult) return false

    val referenceTypes = possibleTypes.types.filterIsInstance<EtsRefType>()
        .filter { it is EtsClassType || it is EtsUnclearRefType }
    referenceTypes.any { type ->
        hierarchy.classesForType(type).any { receiverClass ->
            val selectedMethod = if (stmt.call.isSuperCall) {
                hierarchy.explicitSuperMethod(receiverClass, stmt.call.callee)
            } else {
                hierarchy.nearestInstanceMethod(receiverClass, stmt.call.callee.name)
            }
            val declaringClass = selectedMethod?.enclosingClass
            val projectMethod = declaringClass?.let { it in scene.projectClasses } == true
            val eligibleOwner = stmt.call.isSuperCall || concreteReceiver ||
                declaringClass?.let(candidateOwners::contains) == true
            projectMethod && eligibleOwner
        }
    }
}

private fun TsExprResolver.handleArrayIndexSearchCall(
    stmt: TsVirtualMethodCallStmt,
    instanceType: EtsArrayType,
    array: UHeapRef,
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        if (stmt.call.callee.name != "indexOf") {
            return TsExprApproximationResult.NoApproximation
        }

        return from(handleArrayIndexOf(stmt.call, instanceType, array))
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
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        return from(handleArrayPop(stmt, instanceType, stmt.instance.asExpr(ctx.addressSort)))
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
): TsExprApproximationResult {
    val dispatcher = unknownCallDispatcher
    if (dispatcher !is TsUnknownCallModelDispatcher) {
        return from(handleArrayShift(stmt, instanceType, stmt.instance.asExpr(ctx.addressSort)))
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

    val truthy = mkTruthyExpr(arg, scope)
    scope.ensureTruthinessSupported(arg) ?: return null

    truthy
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
    val newState = when (expr.callee.name) {
        "resolve" -> PromiseState.FULFILLED
        "reject" -> PromiseState.REJECTED
        else -> error("Unexpected: $expr")
    }
    check(expr.args.size == 1) {
        "Promise.${expr.callee.name}() should have exactly one argument, but got ${expr.args.size}"
    }
    val value = resolve(expr.args.single()) ?: return null

    if (newState == PromiseState.FULFILLED) {
        val existingPromise = existingPromiseOrEnsureNonThenable(
            scope = scope,
            value = value,
            hierarchy = hierarchy,
            operation = "Promise.resolve",
        )
        if (existingPromise != null) return existingPromise
    }

    createSettledPromise(scope = scope, value = value, settlement = newState)
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
    stmt: TsVirtualMethodCallStmt,
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    val lengthLValue = mkArrayLengthLValue(array, arrayType)
    val length = scope.calcOnState { memory.read(lengthLValue) }
    ensureArrayGrowthWithinCapacity(length, stmt.args.size, operation = "push") ?: return null

    val newLength = mkBvAddExpr(length, mkBv(stmt.args.size))
    if (stmt.args.isNotEmpty()) {
        scope.doWithState { memory.write(lengthLValue, newLength, guard = trueExpr) }
    }

    for ((offset, arg) in stmt.args.withIndex()) {
        val index = mkBvAddExpr(length, mkBv(offset))
        assignToArrayIndex(
            scope = scope,
            array = array,
            index = index,
            expr = arg,
            arrayType = arrayType,
        ) ?: return null
    }

    mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = newLength.asExpr(sizeSort),
        signed = true,
    )
}

private fun TsExprResolver.ensureArrayGrowthWithinCapacity(
    length: UExpr<TsSizeSort>,
    itemCount: Int,
    operation: String,
): Unit? = with(ctx) {
    val maximumCurrentLength = options.maxArraySize - itemCount
    val withinCapacity = mkBvSignedLessOrEqualExpr(length, mkBv(maximumCurrentLength))
    val canExceedCapacity = scope.checkSat(mkNot(withinCapacity)) != null
    if (canExceedCapacity) {
        reportRuntimeFeatureLimitation(
            reason = TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
            detail = "Array.$operation would grow beyond configured length bound ${options.maxArraySize}",
        )
    }

    scope.assert(withinCapacity)
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
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    removeArrayElement(stmt, array, arrayType, first = false)
}

private fun TsExprResolver.removeArrayElement(
    stmt: TsVirtualMethodCallStmt,
    array: UHeapRef,
    arrayType: EtsArrayType,
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
        val removed = readArrayElement(scope, array, index, arrayType)

        if (first) {
            copyArrayElements(
                srcRef = array,
                dstRef = array,
                arrayType = arrayType,
                fromSrc = mkBv(1),
                fromDst = mkBv(0),
                length = newLength,
            )
            moveInitializedArrayIndices(array) { sourceIndex -> mkBvSubExpr(sourceIndex, mkBv(1)) }
        }

        truncateKnownArrayIndices(array, newLength)
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
    val resolvedValue = if (expr.args.isEmpty()) {
        mkUndefinedValue()
    } else {
        resolve(expr.args[0]) ?: return null
    }
    val hasResolvedElementSort = typeToSort(arrayType.componentType) !is TsUnresolvedSort
    val valueMatchesElementSort = resolvedValue.sort == elementSort
    val containsFakeValue = resolvedValue.sort == addressSort &&
        resolvedValue.asExpr(addressSort).hasFakeValueBranch()
    val usesBoxedValue = hasResolvedElementSort && (!valueMatchesElementSort || containsFakeValue)
    val boxedValue = if (usesBoxedValue) boxArrayValue(scope, resolvedValue) else null

    val nativeValue = when {
        !hasResolvedElementSort -> resolvedValue.toFakeObject(scope)
        !usesBoxedValue -> resolvedValue
        elementSort == boolSort -> falseExpr
        elementSort == fp64Sort -> mkFp64(0.0)
        elementSort == addressSort -> mkUndefinedValue()
        else -> {
            logger.warn { "Unsupported element sort in Array.fill(): $elementSort" }
            return null
        }
    }

    val start = if (expr.args.size > 1) {
        resolve(expr.args[1]) ?: return null
    } else {
        mkBv(0)
    }
    val startBv: UExpr<TsSizeSort> = if (start == mkUndefinedValue()) {
        mkBv(0)
    } else when (start.sort) {
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
    val endBv: UExpr<TsSizeSort> = if (end == mkUndefinedValue()) {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        scope.calcOnState { memory.read(lengthLValue) }
    } else when (end.sort) {
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
        val length = memory.read(mkArrayLengthLValue(array, arrayType))
        val zero = mkBv(0)

        fun normalizeIndex(index: UExpr<TsSizeSort>): UExpr<TsSizeSort> {
            val relative = mkIte(mkBvSignedLessExpr(index, zero), mkBvAddExpr(length, index), index)
            val capped = mkIte(mkBvSignedGreaterExpr(relative, length), length, relative)
            return mkIte(mkBvSignedLessExpr(relative, zero), zero, capped)
        }

        val from = normalizeIndex(startBv)
        val to = normalizeIndex(endBv)
        val fillLength = mkIte(mkBvSignedLessExpr(from, to), mkBvSubExpr(to, from), zero)
        val withinCapacity = mkBvSignedLessOrEqualExpr(fillLength, mkBv(ARRAY_FILL_MAX_SIZE))
        if (scope.checkSat(mkNot(withinCapacity)) != null) {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
                detail = "Array.fill exceeds the supported range of $ARRAY_FILL_MAX_SIZE elements",
            )
        }
        scope.assert(withinCapacity) ?: return@calcOnState null

        val tempSize = getIntValue(fillLength)?.coerceAtLeast(0) ?: ARRAY_FILL_MAX_SIZE
        val hasKnownOwnSlots = array in knownArrayPropertyPresence
        val concreteStart = getIntValue(from)
        val concreteFillLength = getIntValue(fillLength)
        if (hasKnownOwnSlots && (concreteStart == null || concreteFillLength == null)) {
            logger.warn { "Array.fill() cannot preserve symbolic own-slot presence in the legacy path" }
            return@calcOnState null
        }

        // Allocate a temporary array to hold the filled values
        val tempArray = memory.allocConcrete(descriptor)

        // Fill the temporary array with the specified `value`
        memory.initializeArray(
            tempArray,
            descriptor,
            elementSort,
            sizeSort,
            (0 until tempSize).asSequence().map { nativeValue.asExpr(elementSort) },
        )

        if (hasResolvedElementSort) {
            initializeTypedArrayOverrides(array)

            fun <Sort : USort> initializeTempMetadata(
                type: TsTypedArrayStorage,
                sort: Sort,
                values: List<UExpr<Sort>>,
            ) {
                val regionId = UArrayRegionId<TsTypedArrayStorage, Sort, TsSizeSort>(type, sort)
                val region = memory.getRegion(regionId)
                check(region is UArrayRegion<TsTypedArrayStorage, Sort, TsSizeSort>) {
                    "Cannot initialize typed array metadata in $region"
                }

                val initialized = region.initializeAllocatedArray(
                    address = tempArray.address,
                    arrayType = type,
                    sort = sort,
                    content = values,
                    operationGuard = trueExpr,
                    ownership = memory.ownership,
                )
                memory.setRegion(regionId, initialized)
            }

            val hasBoxedValue = if (usesBoxedValue) trueExpr else falseExpr
            val overrideValues = List(tempSize) { hasBoxedValue }
            initializeTempMetadata(TsTypedArrayStorage.HAS_BOXED_VALUE, boolSort, overrideValues)

            if (usesBoxedValue) {
                val boxedFillValue = checkNotNull(boxedValue)
                val boxedValues = List(tempSize) { boxedFillValue }
                initializeTempMetadata(TsTypedArrayStorage.BOXED_VALUE, addressSort, boxedValues)
            }
        }

        // Copy the filled values to the specified range in the original array
        memory.memcpy(
            srcRef = tempArray,
            dstRef = array,
            type = descriptor,
            elementSort = elementSort,
            fromSrc = zero,
            fromDst = from,
            length = fillLength,
        )

        if (hasResolvedElementSort) {
            memory.memcpy(
                srcRef = tempArray,
                dstRef = array,
                type = TsTypedArrayStorage.HAS_BOXED_VALUE,
                elementSort = boolSort,
                fromSrc = zero,
                fromDst = from,
                length = fillLength,
            )

            if (usesBoxedValue) {
                memory.memcpy(
                    srcRef = tempArray,
                    dstRef = array,
                    type = TsTypedArrayStorage.BOXED_VALUE,
                    elementSort = addressSort,
                    fromSrc = zero,
                    fromDst = from,
                    length = fillLength,
                )
            }
        }

        if (hasKnownOwnSlots) {
            val fillCount = checkNotNull(concreteFillLength)
            val filledIndices = (0 until fillCount).associate { offset ->
                mkBvAddExpr(from, mkBv(offset)) to trueExpr
            }
            initializedArrayIndices += array to (initializedArrayIndices[array].orEmpty() + filledIndices)
        }

        // Unknown own-slot metadata cannot be inferred from this bulk copy.
        unsupportedIteratorArrays += array

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
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    removeArrayElement(stmt, array, arrayType, first = true)
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
    stmt: TsVirtualMethodCallStmt,
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    val lengthLValue = mkArrayLengthLValue(array, arrayType)
    val length = scope.calcOnState { memory.read(lengthLValue) }
    ensureArrayGrowthWithinCapacity(length, stmt.args.size, operation = "unshift") ?: return null

    val itemCount = stmt.args.size
    val newLength = mkBvAddExpr(length, mkBv(itemCount))
    if (itemCount != 0) {
        scope.doWithState {
            memory.write(lengthLValue, newLength, guard = trueExpr)

            copyArrayElements(
                srcRef = array,
                dstRef = array,
                arrayType = arrayType,
                fromSrc = mkBv(0),
                fromDst = mkBv(itemCount),
                length = length,
            )
            moveInitializedArrayIndices(array) { index -> mkBvAddExpr(index, mkBv(itemCount)) }
        }
    }

    for ((index, arg) in stmt.args.withIndex()) {
        assignToArrayIndex(
            scope = scope,
            array = array,
            index = mkBv(index),
            expr = arg,
            arrayType = arrayType,
        ) ?: return null
    }

    mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = newLength.asExpr(sizeSort),
        signed = true,
    )
}

/** Compatibility approximation used when the source model dispatcher is unavailable. */
private fun TsExprResolver.handleArrayJoin(): UExpr<*>? = with(ctx) {
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
    stmt: TsVirtualMethodCallStmt,
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    val start = stmt.args.getOrNull(0) ?: mkBv(0)
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

    val end = stmt.args.getOrNull(1) ?: run {
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
        val hasKnownOwnSlots = array in knownArrayPropertyPresence

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
        if (hasKnownOwnSlots) {
            copyKnownArrayIndexPresence(
                srcRef = array,
                dstRef = slicedArray,
                fromSrc = from,
                fromDst = mkBv(0),
                length = newLength,
            )
            knownArrayPropertyPresence += slicedArray
        }

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
 * a.concat() -> [1, 2] // new array with the same elements
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
    val elementSort = typeToSort(arrayType.componentType)
    val arrayTypes = mutableListOf<EtsArrayType?>()
    for ((index, arg) in stmt.args.withIndex()) {
        if (arg.sort == addressSort) {
            val ref = arg.asExpr(addressSort)
            // A fake or an untyped reference may itself contain an array; spreading requires runtime dispatch.
            if (ref.hasFakeValueBranch()) return TsExprApproximationResult.NoApproximation
            val type = scope.calcOnState { arrayStorageType(ref, stmt.call.args[index].type) }
            if (typeToSort(type) is TsUnresolvedSort) return TsExprApproximationResult.NoApproximation
            arrayTypes += type as? EtsArrayType
        } else {
            arrayTypes += null
        }
    }
    val hasIncompatibleElementSort = arrayTypes.withIndex().any { (index, type) ->
        if (type != null) {
            typeToSort(type.componentType) != elementSort
        } else {
            elementSort !is TsUnresolvedSort && stmt.args[index].sort != elementSort
        }
    }
    if (hasIncompatibleElementSort) {
        logger.debug { "Array.concat requires conversion between different element storage sorts" }
        return TsExprApproximationResult.NoApproximation
    }

    val copySources = mutableListOf(array)
    for ((index, type) in arrayTypes.withIndex()) {
        if (type != null) {
            copySources += stmt.args[index].asExpr(addressSort)
        }
    }
    val knownSources = scope.calcOnState { copySources.map { it in knownArrayPropertyPresence } }
    val preserveKnownPresence = knownSources.all { it }
    val hasMixedPresence = knownSources.any { it } && !preserveKnownPresence
    if (hasMixedPresence) return TsExprApproximationResult.NoApproximation

    val originalLength = scope.calcOnState { memory.read(mkArrayLengthLValue(array, arrayType)) }
    val argumentLengths = scope.calcOnState {
        arrayTypes.mapIndexed { index, type ->
            if (type == null) {
                mkBv(1)
            } else {
                val ref = stmt.args[index].asExpr(addressSort)
                memory.read(mkArrayLengthLValue(ref, type))
            }
        }
    }
    var resultLength = originalLength
    for (length in argumentLengths) {
        resultLength = mkBvAddExpr(resultLength, length)
    }
    ensureArrayGrowthWithinCapacity(resultLength, itemCount = 0, operation = "concat")
        ?: return TsExprApproximationResult.ResolveFailure

    from(
        scope.calcOnState {
            val resultArray = memory.allocConcrete(arrayDescriptorOf(arrayType))
            copyArrayElements(
                srcRef = array,
                dstRef = resultArray,
                arrayType = arrayType,
                fromSrc = mkBv(0),
                fromDst = mkBv(0),
                length = originalLength,
            )
            if (preserveKnownPresence) {
                copyKnownArrayIndexPresence(
                    srcRef = array,
                    dstRef = resultArray,
                    fromSrc = mkBv(0),
                    fromDst = mkBv(0),
                    length = originalLength,
                )
            }

            var copiedLength = originalLength
            stmt.args.forEachIndexed { index, arg ->
                val argType = arrayTypes[index]
                if (argType != null) {
                    val ref = arg.asExpr(addressSort)
                    val length = argumentLengths[index]
                    copyArrayElements(
                        srcRef = ref,
                        dstRef = resultArray,
                        arrayType = argType,
                        fromSrc = mkBv(0),
                        fromDst = copiedLength,
                        length = length,
                    )
                    if (preserveKnownPresence) {
                        copyKnownArrayIndexPresence(
                            srcRef = ref,
                            dstRef = resultArray,
                            fromSrc = mkBv(0),
                            fromDst = copiedLength,
                            length = length,
                        )
                    }
                    copiedLength = mkBvAddExpr(copiedLength, length)
                } else {
                    val newLength = mkBvAddExpr(copiedLength, mkBv(1))
                    memory.write(mkArrayLengthLValue(resultArray, arrayType), newLength, guard = trueExpr)
                    assignToArrayIndex(scope, resultArray, copiedLength, arg, arrayType) ?: return@calcOnState null
                    copiedLength = newLength
                }
            }
            memory.write(mkArrayLengthLValue(resultArray, arrayType), resultLength, guard = trueExpr)
            if (preserveKnownPresence) {
                knownArrayPropertyPresence += resultArray
            }

            resultArray
        }
    )
}

/** Compatibility approximation constrained to -1 or an in-range index. */
private fun TsExprResolver.handleArrayIndexOf(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
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

/** Compatibility approximation with an unconstrained boolean result. */
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
    arrayType: EtsArrayType,
    array: UHeapRef,
): UExpr<*>? = with(ctx) {
    scope.calcOnState {
        val descriptor = arrayDescriptorOf(arrayType)

        // Allocate a new array to represent the reversed result
        val reversedArray = memory.allocConcrete(descriptor)

        // Read the length of the original array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        val nonNegativeLength = mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
        val withinMaximumLength = mkBvSignedLessOrEqualExpr(length, mkBv(options.maxArraySize))
        val withinCapacity = mkAnd(nonNegativeLength, withinMaximumLength)
        val capacityExceeded = mkNot(withinCapacity)
        if (scope.checkSat(capacityExceeded) != null) {
            reportRuntimeFeatureLimitation(
                reason = TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
                detail = "Array.reverse exceeds the configured length bound ${options.maxArraySize}",
            )
        }
        scope.assert(withinCapacity) ?: return@calcOnState null

        val reversedIndices = (0 until options.maxArraySize).map { index ->
            mkBvSubExpr(mkBvSubExpr(length, mkBv(1)), index.toBv())
        }
        forEachArrayPayloadRegion(arrayType) { region, sort ->
            val contents = reversedIndices.asSequence().map { index ->
                memory.readArrayIndex(array, index, region, sort)
            }
            memory.initializeArray(reversedArray, region, sort, sizeSort, contents)
        }
        if (typeToSort(arrayType.componentType) is TsUnresolvedSort) {
            TsUnresolvedArrayKind.entries.forEach { kind ->
                val contents = reversedIndices.map { index -> memory.readArrayIndex(array, index, kind, boolSort) }
                initializeArrayKind(reversedArray, kind, contents)
            }
        } else {
            initializeTypedArrayOverrides(array)
            for ((destination, source) in reversedIndices.withIndex()) {
                val target = destination.toBv()
                val hasBoxedValue = memory.read(typedArrayOverrideLValue(array, source))
                val boxedValue = memory.read(typedArrayBoxedLValue(array, source))

                memory.write(typedArrayOverrideLValue(reversedArray, target), hasBoxedValue, guard = trueExpr)
                memory.write(typedArrayBoxedLValue(reversedArray, target), boxedValue, guard = trueExpr)
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

        moveInitializedArrayIndices(array) { index -> mkBvSubExpr(mkBvSubExpr(length, mkBv(1)), index) }

        // Return the modified original array
        array
    }
}
