package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsType
import org.usvm.UExpr
import org.usvm.api.memcpy
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.sizeSort
import org.usvm.types.TypesResult
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
        scope.calcOnState { (memory.typeStreamOf(resolvedInstance).take(1) as? TypesResult.SuccessfulTypesResult<EtsType>)?.types?.single() ?: expr.instance.type }
    } else {
        expr.instance.type
    }

    if (instanceType is EtsArrayType) {
        val elementTypeSort = typeToSort(instanceType).takeIf { it !is TsUnresolvedSort } ?: ctx.addressSort
        // TODO write tests https://github.com/UnitTestBot/usvm/issues/300
        if (expr.callee.name == "push") {
            return scope.calcOnState {
                val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null

                val lengthLValue = mkArrayLengthLValue(resolvedInstance, instanceType)
                val length = memory.read(lengthLValue)

                val newLength = mkBvAddExpr(length, 1.toBv())
                memory.write(lengthLValue, newLength, guard = ctx.trueExpr)

                val resolvedArg = resolve(expr.args.single()) ?: return@calcOnState null

                // TODO check sorts compatibility https://github.com/UnitTestBot/usvm/issues/300
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

                // TODO add exception for empty array????

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
