package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.usvm.UExpr
import org.usvm.api.initializeArray
import org.usvm.api.initializeArrayLength
import org.usvm.api.memcpy
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.sizeSort
import org.usvm.types.firstOrNull
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

fun TsExprResolver.tryApproximateInstanceCall(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
    val resolvedInstance = scope.calcOnState { resolve(expr.instance)?.asExpr(ctx.addressSort) } ?: return null

    if (expr.instance.name == "Number") {
        if (expr.callee.name == "isNaN") {
            check(expr.args.size == 1) { "Number.isNaN should have one argument" }
            return resolveAfterResolved(expr.args.single()) { arg ->
                handleNumberIsNaN(arg)
            }
        }
    }

    if (expr.instance.name == "Logger") {
        return mkUndefinedValue()
    }

    if (expr.callee.name == "toString") {
        return mkStringConstant("I am a string", scope)
    }

    val instanceType = if (isAllocatedConcreteHeapRef(resolvedInstance)) {
        scope.calcOnState {
            memory.typeStreamOf(resolvedInstance).firstOrNull() ?: expr.instance.type
        }
    } else {
        expr.instance.type
    }

    if (instanceType is EtsArrayType) {
        val elementTypeSort = typeToSort(instanceType.elementType).takeIf { it !is TsUnresolvedSort } ?: ctx.addressSort

        // push
        if (expr.callee.name == "push") {
            return scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null
                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)
                val newLength = mkBvAddExpr(length, 1.toBv())
                memory.write(lengthLValue, newLength, guard = ctx.trueExpr)
                val resolvedArg = resolve(expr.args.single()) ?: return@calcOnState null
                val newIndexLValue = mkArrayIndexLValue(
                    resolvedArg.sort,
                    resolvedInstance,
                    length,
                    instanceType
                )
                memory.write(newIndexLValue, resolvedArg.asExpr(newIndexLValue.sort), guard = ctx.trueExpr)
                newLength
            }
        }

        // fill
        if (expr.callee.name == "fill") {
            return scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null
                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)
                val resolvedArg = resolve(expr.args.single()) ?: return@calcOnState null

                val artificialArray = memory.allocConcrete(instanceType)
                memory.initializeArrayLength(artificialArray, instanceType, sizeSort, 10_000.toBv().asExpr(sizeSort))
                memory.initializeArray(artificialArray, instanceType, elementTypeSort, sizeSort, Array(10_000) { resolvedArg.asExpr(elementTypeSort) }.asSequence())
                memory.memcpy(artificialArray, resolvedInstance, instanceType, elementTypeSort, 0.toBv(), 0.toBv(), length)

                resolvedInstance
            }
        }

        // unshift
        if (expr.callee.name == "unshift") {
            return scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null
                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)
                val newLength = mkBvAddExpr(length, 1.toBv())
                memory.write(lengthLValue, newLength, guard = ctx.trueExpr)
                val resolvedArg = resolve(expr.args.single()) ?: return@calcOnState null
                memory.memcpy(
                    srcRef = resolvedInstance,
                    dstRef = resolvedInstance,
                    type = instanceType,
                    elementSort = elementTypeSort,
                    fromSrc = 0.toBv().asExpr(sizeSort),
                    fromDst = 1.toBv().asExpr(sizeSort),
                    length = length,
                )
                val zeroIndexLValue = mkArrayIndexLValue(
                    resolvedArg.sort,
                    resolvedInstance,
                    0.toBv().asExpr(sizeSort),
                    instanceType
                )
                memory.write(zeroIndexLValue, resolvedArg.asExpr(zeroIndexLValue.sort), guard = ctx.trueExpr)
                newLength
            }
        }

        if (expr.callee.name == "shift") {
            return scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null
                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)
                val newLength = mkBvSubExpr(length, 1.toBv())
                val indexLValue = mkArrayIndexLValue(
                    elementTypeSort,
                    resolvedInstance,
                    0.toBv().asExpr(sizeSort),
                    instanceType
                )
                val result = memory.read(indexLValue)
                memory.memcpy(
                    srcRef = resolvedInstance,
                    dstRef = resolvedInstance,
                    type = instanceType,
                    elementSort = elementTypeSort,
                    fromSrc = 1.toBv().asExpr(sizeSort),
                    fromDst = 0.toBv().asExpr(sizeSort),
                    length = newLength,
                )
                memory.write(lengthLValue, newLength, guard = ctx.trueExpr)
                result
            }
        }

        if (expr.callee.name == "pop") {
            return scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null
                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)
                if (length == 0.toBv().asExpr(sizeSort)) {
                    return@calcOnState null // TODO throw exception?
                }
                val newLength = mkBvSubExpr(length, 1.toBv())
                memory.write(lengthLValue, newLength, guard = ctx.trueExpr)
                val indexLValue = mkArrayIndexLValue(
                    elementTypeSort,
                    resolvedInstance,
                    newLength,
                    instanceType
                )
                memory.read(indexLValue)
            }
        }
    }

    return null
}
