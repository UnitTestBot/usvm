package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.usvm.UExpr
import org.usvm.api.memcpy
import org.usvm.sizeSort
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

fun TsExprResolver.tryApproximateInstanceCall(expr: EtsInstanceCallExpr): UExpr<*>? = with(ctx) {
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

    // TODO write tests https://github.com/UnitTestBot/usvm/issues/300
    if (expr.callee.name == "push" && expr.instance.type is EtsArrayType) {
        return scope.calcOnState {
            val arrayType = expr.instance.type as EtsArrayType
            val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null

            val lengthLValue = mkArrayLengthLValue(resolvedInstance, arrayType)
            val length = memory.read(lengthLValue)

            val newLength = mkBvAddExpr(length, 1.toBv())
            memory.write(lengthLValue, newLength, guard = ctx.trueExpr)

            val resolvedArg = resolve(expr.args.single()) ?: return@calcOnState null

            // TODO check sorts compatibility https://github.com/UnitTestBot/usvm/issues/300
            val newIndexLValue = mkArrayIndexLValue(
                resolvedArg.sort,
                resolvedInstance,
                length,
                arrayType
            )
            memory.write(newIndexLValue, resolvedArg.asExpr(newIndexLValue.sort), guard = ctx.trueExpr)

            newLength
        }
    }

    if (expr.callee.name == "shift" && expr.instance.type is EtsArrayType) {
        return scope.calcOnState {
            val arrayType = expr.instance.type as EtsArrayType
            val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null

            val lengthLValue = mkArrayLengthLValue(resolvedInstance, arrayType)
            val length = memory.read(lengthLValue)
            val newLength = mkBvSubExpr(length, 1.toBv())

            val elementSort = typeToSort(arrayType.elementType)

            val indexLValue = mkArrayIndexLValue(
                elementSort,
                resolvedInstance,
                0.toBv().asExpr(sizeSort),
                arrayType
            )

            // TODO add exception for empty array????

            val result = memory.read(indexLValue)

            memory.memcpy(
                srcRef = resolvedInstance,
                dstRef = resolvedInstance,
                type = arrayType,
                elementSort = elementSort,
                fromSrc = 1.toBv().asExpr(sizeSort),
                fromDst = 0.toBv().asExpr(sizeSort),
                length = newLength,
            )

            memory.write(lengthLValue, newLength, guard = ctx.trueExpr)

            result
        }
    }

    if (expr.callee.name == "pop" && expr.instance.type is EtsArrayType) {
        return scope.calcOnState {
            val arrayType = expr.instance.type as EtsArrayType
            val resolvedInstance = resolve(expr.instance)?.asExpr(ctx.addressSort) ?: return@calcOnState null

            val lengthLValue = mkArrayLengthLValue(resolvedInstance, arrayType)
            val length = memory.read(lengthLValue)

            if (length == 0.toBv().asExpr(sizeSort)) {
                return@calcOnState null // TODO throw exception?
            }

            val newLength = mkBvSubExpr(length, 1.toBv())
            memory.write(lengthLValue, newLength, guard = ctx.trueExpr)

            val elementSort = typeToSort(arrayType.elementType)
            val indexLValue = mkArrayIndexLValue(
                elementSort,
                resolvedInstance,
                newLength,
                arrayType
            )

            memory.read(indexLValue)
        }
    }

    return null
}
