package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.initializeArray
import org.usvm.api.memcpy
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.markResolved
import org.usvm.machine.interpreter.setResolvedValue
import org.usvm.sizeSort
import org.usvm.types.firstOrNull
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.resolveEtsMethods

private val logger = KotlinLogging.logger {}

sealed interface TsExprApproximationResult {
    data class SuccessfulApproximation(val expr: UExpr<*>) : TsExprApproximationResult
    data object ResolveFailure : TsExprApproximationResult
    data object NoApproximation : TsExprApproximationResult

    companion object {
        fun from(expr: UExpr<*>?): TsExprApproximationResult = when {
            expr != null -> SuccessfulApproximation(expr)
            else -> ResolveFailure
        }
    }
}

fun TsExprResolver.tryApproximateInstanceCall(
    expr: EtsInstanceCallExpr,
): TsExprApproximationResult = with(ctx) {
    // Mock all calls to `Logger` methods
    if (expr.instance.name == "Logger") {
        return from(mkUndefinedValue())
    }

    // Mock `.toString()` method calls
    if (expr.callee.name == "toString") {
        if (expr.args.isNotEmpty()) {
            logger.warn { "toString() should have no arguments, but got ${expr.args.size}" }
        }
        return from(mkStringConstant("I am a string", scope))
    }

    // Handle `.valueOf()` method calls
    if (expr.callee.name == "valueOf") {
        return from(handleValueOf(expr))
    }

    // Handle `Number.isNaN()` calls
    if (expr.instance.name == "Number") {
        if (expr.callee.name == "isNaN") {
            return from(handleNumberIsNaN(expr))
        }
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

    val instance = scope.calcOnState { resolve(expr.instance)?.asExpr(addressSort) }
        ?: return TsExprApproximationResult.ResolveFailure

    val instanceType = if (isAllocatedConcreteHeapRef(instance)) {
        scope.calcOnState {
            memory.typeStreamOf(instance).firstOrNull() ?: expr.instance.type
        }
    } else {
        expr.instance.type
    }

    if (instanceType is EtsArrayType) {
        val elementSort = typeToSort(instanceType.elementType)
            .takeIf { it !is TsUnresolvedSort }
            ?: addressSort

        // Handle 'Array.push()' method calls
        if (expr.callee.name == "push") {
            return from(handleArrayPush(expr, instanceType, elementSort))
        }

        // Handle `Array.pop() method calls
        if (expr.callee.name == "pop") {
            return from(handleArrayPop(expr, instanceType, elementSort))
        }

        // Handle `Array.fill() method calls
        if (expr.callee.name == "fill") {
            return from(handleArrayFill(expr, instanceType, elementSort))
        }

        // Handle `Array.unshift() method calls
        if (expr.callee.name == "unshift") {
            return from(handleArrayUnshift(expr, instanceType, elementSort))
        }

        // Handle `Array.shift() method calls
        if (expr.callee.name == "shift") {
            return from(handleArrayShift(expr, instanceType, elementSort))
        }
    }

    return TsExprApproximationResult.NoApproximation
}

private fun TsExprResolver.handleValueOf(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
    if (expr.args.isNotEmpty()) {
        logger.warn { "valueOf() should have no arguments, but got ${expr.args.size}" }
    }

    val instance = resolve(expr.instance) ?: return null
    instance
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

/**
 * Handles the `Array.push(...items)` method call.
 * Appends the specified `items` to the end of the array.
 * This method modifies the array in place and returns the new length of the array.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.push
 */
private fun TsExprResolver.handleArrayPush(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.size == 1) {
        "Array.push() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    scope.calcOnState {
        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Increase the length of the array
        val newLength = mkBvAddExpr(length, 1.toBv())
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Write the new element to the end of the array
        // TODO check sorts compatibility https://github.com/UnitTestBot/usvm/issues/300
        val newIndexLValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
            index = length,
            type = arrayType,
        )
        memory.write(newIndexLValue, arg.asExpr(elementSort), guard = trueExpr)

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
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.pop
 */
private fun TsExprResolver.handleArrayPop(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val instance = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.isEmpty()) { "Array.pop() should have no arguments, but got ${expr.args.size}" }

    scope.calcOnState {
        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(instance, arrayType)
        val length = memory.read(lengthLValue)

        // Decrease the length of the array
        // TODO: Only decrease the length if it is not zero.
        //       It is not an error/exception to pop from an empty array!
        //       If the array is empty, `pop` returns `undefined`.
        val newLength = mkBvSubExpr(length, 1.toBv())

        // Read the last element of the array (to be removed)
        val lastIndexLValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = instance,
            index = newLength,
            type = arrayType,
        )
        // TODO: If the array is empty, return `undefined` instead of the last element.
        val removedElement = memory.read(lastIndexLValue)

        // Update the length of the array (AFTER reading the last element)
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Return the removed element
        removedElement
    }
}

/**
 * Handles the `Array.fill(value [,start [,end]])` method call.
 * Fills the array with the specified `value` from the `start` index to the `end` index.
 * If `start` index is not provided, it defaults to `0`.
 * If `end` index is not provided, it defaults to the length of the array.
 * This method modifies the array in place and returns the modified array.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.shift
 */
private fun TsExprResolver.handleArrayFill(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.size == 1) {
        "Array.fill() should have exactly one argument, but got ${expr.args.size}"
    }
    val value = resolve(expr.args.single()) ?: return null

    scope.calcOnState {
        // Allocate an auxiliary array to fill
        val auxArray = memory.allocConcrete(arrayType)

        // Initialize the auxiliary array filled with the given `value`
        memory.initializeArray(
            auxArray,
            arrayType,
            elementSort,
            sizeSort,
            (0 until ARRAY_FILL_SIZE).asSequence().map { value.asExpr(elementSort) }
        )

        // Copy the initialized aux array to the target array
        memory.memcpy(
            srcRef = auxArray,
            dstRef = array,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 0.toBv().asExpr(sizeSort),
            fromDst = 0.toBv().asExpr(sizeSort),
            length = mkArrayLengthLValue(array, arrayType).let { memory.read(it) },
        )

        // Return the modified array
        array
    }
}

// TODO: why?
private const val ARRAY_FILL_SIZE = 10_000

/**
 * Handle the `Array.shift()` method call.
 * Removes the first element from the array and returns it.
 * If the array is empty, it returns `undefined`.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.shift
 */
private fun TsExprResolver.handleArrayShift(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.isEmpty()) {
        "Array.shift() should have no arguments, but got ${expr.args.size}"
    }

    scope.calcOnState {
        // Store the first element of the array (to be removed)
        // TODO: If the array is empty, return `undefined` instead of the first element.
        val firstIndexLValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
            index = 0.toBv().asExpr(sizeSort),
            type = arrayType,
        )
        val firstElement = memory.read(firstIndexLValue)

        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Decrease the length of the array
        // TODO: Only decrease the length if it is not zero.
        //       It is not an error/exception to shift an empty array!
        //       If the array is empty, `shift` returns `undefined`.
        val newLength = mkBvSubExpr(length, 1.toBv())
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Shift elements to the left
        memory.memcpy(
            srcRef = array,
            dstRef = array,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 1.toBv().asExpr(sizeSort),
            fromDst = 0.toBv().asExpr(sizeSort),
            length = newLength,
        )

        // Return the removed element
        firstElement
    }
}

/**
 * Handle the `Array.unshift(...items)` method call.
 * Prepends the specified `items` to the start of the array.
 * This method modifies the array in place and returns the new length of the array.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.unshift
 */
private fun TsExprResolver.handleArrayUnshift(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val instance = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    // TODO: support vararg
    check(expr.args.size == 1) {
        "Array.unshift() should have exactly one argument, but got ${expr.args.size}"
    }
    val arg = resolve(expr.args.single()) ?: return null

    scope.calcOnState {
        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(instance, arrayType)
        val length = memory.read(lengthLValue)

        // Increase the length of the array
        val newLength = mkBvAddExpr(length, 1.toBv())
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Shift elements to the right
        memory.memcpy(
            srcRef = instance,
            dstRef = instance,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 0.toBv().asExpr(sizeSort),
            fromDst = 1.toBv().asExpr(sizeSort),
            length = length,
        )

        // Write the new element to the start of the array
        val startIndexLValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = instance,
            index = 0.toBv().asExpr(sizeSort),
            type = arrayType,
        )
        memory.write(startIndexLValue, arg.asExpr(elementSort), guard = trueExpr)

        // Return the new length of the array (as per ECMAScript spec for Array.unshift)
        mkBvToFpExpr(
            sort = fp64Sort,
            roundingMode = fpRoundingModeSortDefaultValue(),
            value = newLength.asExpr(sizeSort),
            signed = true,
        )
    }
}
