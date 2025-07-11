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
import org.usvm.machine.expr.TsExprApproximationResult.Companion.create
import org.usvm.sizeSort
import org.usvm.types.firstOrNull
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

fun TsExprResolver.tryApproximateInstanceCall(expr: EtsInstanceCallExpr): TsExprApproximationResult = with(ctx) {
    val resolvedInstance = scope.calcOnState { resolve(expr.instance)?.asExpr(ctx.addressSort) }
        ?: return TsExprApproximationResult.ResolveFailure

    if (expr.instance.name == "Number") {
        if (expr.callee.name == "isNaN") {
            check(expr.args.size == 1) { "Number.isNaN should have one argument" }
            val expr = resolveAfterResolved(expr.args.single()) { arg ->
                handleNumberIsNaN(arg)
            }
            return create(expr)
        }
    }

    if (expr.instance.name == "Logger") {
        return create(mkUndefinedValue())
    }

    if (expr.callee.name == "toString") {
        return create(mkStringConstant("I am a string", scope))
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
            val expr = scope.calcOnState {
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

            return create(expr)
        }

        // fill
        if (expr.callee.name == "fill") {
            val expr = scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null
                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)
                val resolvedArg = resolve(expr.args.single()) ?: return@calcOnState null

                val artificialArray = memory.allocConcrete(instanceType)
                memory.initializeArrayLength(artificialArray, instanceType, sizeSort, 10_000.toBv().asExpr(sizeSort))
                memory.initializeArray(
                    artificialArray,
                    instanceType,
                    elementTypeSort,
                    sizeSort,
                    Array(10_000) { resolvedArg.asExpr(elementTypeSort) }.asSequence()
                )
                memory.memcpy(
                    artificialArray,
                    resolvedInstance,
                    instanceType,
                    elementTypeSort,
                    0.toBv(),
                    0.toBv(),
                    length
                )

                resolvedInstance
            }

            return create(expr)
        }

        // unshift
        if (expr.callee.name == "unshift") {
            val expr = scope.calcOnState {
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

            return create(expr)
        }

        if (expr.callee.name == "shift") {
            val expr = scope.calcOnState {
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

            return create(expr)
        }

        if (expr.callee.name == "pop") {
            val expr = scope.calcOnState {
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

            return create(expr)
        }
    }

    return TsExprApproximationResult.NoApproximation
}

sealed class TsExprApproximationResult {
    data class SuccessfulApproximation(val expr: UExpr<*>) : TsExprApproximationResult()
    data object NoApproximation : TsExprApproximationResult()
    data object ResolveFailure : TsExprApproximationResult()

    companion object {
        fun create(expr: UExpr<*>?): TsExprApproximationResult {
            return when {
                expr != null -> SuccessfulApproximation(expr)
                else -> ResolveFailure
            }
        }
    }
}
