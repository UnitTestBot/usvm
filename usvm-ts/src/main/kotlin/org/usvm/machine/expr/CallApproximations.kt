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
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.memcpy
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsSizeSort
import org.usvm.machine.expr.TsExprApproximationResult.Companion.from
import org.usvm.machine.interpreter.PromiseState
import org.usvm.machine.interpreter.markResolved
import org.usvm.machine.interpreter.setResolvedValue
import org.usvm.sizeSort
import org.usvm.types.first
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

        // Handle `Array.join() method calls
        if (expr.callee.name == "join") {
            return from(handleArrayJoin(expr, instanceType, elementSort))
        }

        // Handle `Array.slice() method calls
        if (expr.callee.name == "slice") {
            return from(handleArraySlice(expr, instanceType, elementSort))
        }

        // Handle `Array.concat() method calls
        if (expr.callee.name == "concat") {
            return from(handleArrayConcat(expr, instanceType, elementSort))
        }

        // Handle `Array.indexOf() method calls
        if (expr.callee.name == "indexOf") {
            return from(handleArrayIndexOf(expr, instanceType, elementSort))
        }

        // Handle `Array.includes() method calls
        if (expr.callee.name == "includes") {
            return from(handleArrayIncludes(expr, instanceType, elementSort))
        }

        // Handle `Array.reverse() method calls
        if (expr.callee.name == "reverse") {
            return from(handleArrayReverse(expr, instanceType, elementSort))
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
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.isEmpty()) {
        "Array.pop() should have no arguments, but got ${expr.args.size}"
    }

    scope.calcOnState {
        // Read the length of the array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Decrease the length of the array
        // TODO: Only decrease the length if it is not zero.
        //       It is not an error/exception to pop from an empty array!
        //       If the array is empty, `pop` returns `undefined`.
        val newLength = mkBvSubExpr(length, 1.toBv())

        // Read the last element of the array (to be removed)
        val lastIndexLValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
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
 * This method modifies the array in place and returns it.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.fill
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
    // TODO: support `start` and `end` indices
    val value = resolve(expr.args.single()) ?: return null

    scope.calcOnState {
        // Allocate an array to fill
        val resultArray = memory.allocConcrete(arrayType)

        // Read the length of the original array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Fill the result array with the specified `value`
        memory.initializeArray(
            resultArray,
            arrayType,
            elementSort,
            sizeSort,
            (0 until ARRAY_FILL_MAX_SIZE).asSequence().map { value.asExpr(elementSort) }
        )

        // Set the length of the result array to match the original array
        val resultLengthLValue = mkArrayLengthLValue(resultArray, arrayType)
        memory.write(resultLengthLValue, length, guard = trueExpr)

        // Copy the filled array back to the original array (in-place modification)
        memory.memcpy(
            srcRef = resultArray,
            dstRef = array,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 0.toBv().asExpr(sizeSort),
            fromDst = 0.toBv().asExpr(sizeSort),
            length = length,
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
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
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
        val newLength = mkBvAddExpr(length, 1.toBv())
        memory.write(lengthLValue, newLength, guard = trueExpr)

        // Shift elements to the right
        memory.memcpy(
            srcRef = array,
            dstRef = array,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 0.toBv().asExpr(sizeSort),
            fromDst = 1.toBv().asExpr(sizeSort),
            length = length,
        )

        // Write the new element to the start of the array
        val startIndexLValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
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

/**
 * Handles the `Array.join(separator)` method call.
 * Joins all elements of the array into a string, separated by the specified `separator`.
 * If `separator` is not provided, it defaults to `","`.
 * This method returns the resulting string.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.join
 */
private fun TsExprResolver.handleArrayJoin(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
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
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.slice
 */
private fun TsExprResolver.handleArraySlice(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.size <= 2) {
        "Array.slice() should have at most two arguments, but got ${expr.args.size}"
    }
    val start = if (expr.args.isNotEmpty()) {
        resolve(expr.args[0]) ?: return null
    } else {
        mkBv(0, sizeSort)
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
        // Calculate the new length of the sliced array
        val newLength = mkBvSubExpr(endBv, startBv)

        // Allocate a new array for the slice
        val slicedArray = memory.allocConcrete(arrayType)

        // Copy the specified range from the original array to the new array
        memory.memcpy(
            srcRef = array,
            dstRef = slicedArray,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = startBv,
            fromDst = mkBv(0, sizeSort),
            length = newLength,
        )

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
 * a.concat() -> [1, 2]
 * a.concat(b) -> [1, 2, 3, 4]
 * a.concat(b, 5) -> [1, 2, 3, 4, 5]
 *
 * a == [1, 2] // not modified
 * ```
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.concat
 */
private fun TsExprResolver.handleArrayConcat(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.isNotEmpty()) {
        "Array.concat() should have at least one argument, but got ${expr.args.size}"
    }

    val args = expr.args.map { resolve(it) ?: return null }

    scope.calcOnState {
        // Allocate a new array for the concatenated result
        val resultArray = memory.allocConcrete(arrayType)

        // Read the length of the original array
        val originalLengthLValue = mkArrayLengthLValue(array, arrayType)
        val originalLength = memory.read(originalLengthLValue)

        // Copy the original array to the result array
        memory.memcpy(
            srcRef = array,
            dstRef = resultArray,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 0.toBv().asExpr(sizeSort),
            fromDst = 0.toBv().asExpr(sizeSort),
            length = originalLength,
        )

        // Handle each argument in the `concat` call
        var totalLength = originalLength
        for (arg in args) {
            // For array arguments, copy their elements to the result array
            if (arg.sort == addressSort) {
                // TODO: handle empty type stream
                val argType = memory.typeStreamOf(arg.asExpr(addressSort)).first()
                if (argType is EtsArrayType) {
                    val argLengthLValue = mkArrayLengthLValue(arg.asExpr(addressSort), argType)
                    val argLength = memory.read(argLengthLValue)

                    // Copy the elements of the argument array to the result array
                    memory.memcpy(
                        srcRef = arg.asExpr(addressSort),
                        dstRef = resultArray,
                        type = arrayType,
                        elementSort = elementSort,
                        fromSrc = 0.toBv().asExpr(sizeSort),
                        fromDst = totalLength,
                        length = argLength,
                    )

                    // Add the length of the argument array to the total length
                    totalLength = mkBvAddExpr(totalLength, argLength)

                    continue
                }
            }

            // For non-array arguments, treat them as a single element
            val newIndexLValue = mkArrayIndexLValue(
                sort = elementSort,
                ref = resultArray,
                index = totalLength,
                type = arrayType,
            )
            memory.write(newIndexLValue, arg.asExpr(elementSort), guard = trueExpr)
            totalLength = mkBvAddExpr(totalLength, 1.toBv())
        }

        // Set the length of the result array
        val resultLengthLValue = mkArrayLengthLValue(resultArray, arrayType)
        memory.write(resultLengthLValue, totalLength, guard = trueExpr)

        // Return the new concatenated array
        resultArray
    }
}

/**
 * Handles the `Array.indexOf(searchElement)` method call.
 * Returns the index of the first occurrence of `searchElement` in the array, or `-1` if not found.
 *
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.indexof
 */
private fun TsExprResolver.handleArrayIndexOf(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
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
        val notFound = mkEq(symbolicResult, mkBv(-1, sizeSort))
        val validIndex = mkAnd(
            mkBvSignedGreaterOrEqualExpr(symbolicResult, mkBv(0, sizeSort)),
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
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.includes
 */
private fun TsExprResolver.handleArrayIncludes(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
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
 * https://tc39.es/ecma262/multipage/indexed-collections.html#sec-array.prototype.reverse
 */
private fun TsExprResolver.handleArrayReverse(
    expr: EtsInstanceCallExpr,
    arrayType: EtsArrayType,
    elementSort: USort,
): UExpr<*>? = with(ctx) {
    val array = resolve(expr.instance)?.asExpr(addressSort) ?: return null
    check(expr.args.isEmpty()) {
        "Array.reverse() should have no arguments, but got ${expr.args.size}"
    }

    scope.calcOnState {
        // Allocate a new array to represent the reversed result
        val reversedArray = memory.allocConcrete(arrayType)

        // Read the length of the original array
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        val length = memory.read(lengthLValue)

        // Initialize the reversed array with symbolic elements
        memory.initializeArray(
            reversedArray,
            arrayType,
            elementSort,
            sizeSort,
            (0 until ARRAY_REVERSE_MAX_SIZE).asSequence().map { index ->
                // reversedIndex := length - 1 - index
                val reversedIndex = mkBvSubExpr(mkBvSubExpr(length, 1.toBv()), index.toBv())
                val elementLValue = mkArrayIndexLValue(
                    sort = elementSort,
                    ref = array,
                    index = reversedIndex,
                    type = arrayType,
                )
                memory.read(elementLValue)
            }
        )

        //! Note: `reversedArray` is a temporary object not used outside this function,
        //        so it is not necessary to set the "correct" length for it.
        // Set the length of the reversed array
        // val reversedLengthLValue = mkArrayLengthLValue(reversedArray, arrayType)
        // memory.write(reversedLengthLValue, length, guard = trueExpr)

        // Copy the reversed array back to the original array (in-place modification)
        memory.memcpy(
            srcRef = reversedArray,
            dstRef = array,
            type = arrayType,
            elementSort = elementSort,
            fromSrc = 0.toBv().asExpr(sizeSort),
            fromDst = 0.toBv().asExpr(sizeSort),
            length = length,
        )

        // Return the modified original array
        array
    }
}

private const val ARRAY_REVERSE_MAX_SIZE = 5000
