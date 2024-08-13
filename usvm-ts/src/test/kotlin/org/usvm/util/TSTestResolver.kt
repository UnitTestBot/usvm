package org.usvm.util

import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsPrimitiveType
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.model.EtsMethod
import org.usvm.TSObject
import org.usvm.TSTest
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.extractBool
import org.usvm.extractDouble
import org.usvm.extractInt
import org.usvm.memory.URegisterStackLValue
import org.usvm.state.TSMethodResult
import org.usvm.state.TSState

class TSTestResolver {

    fun resolve(method: EtsMethod, state: TSState): TSTest = with(state.ctx) {
        val model = state.models.first()
        when (val methodResult = state.methodResult) {
            is TSMethodResult.Success -> {
                val valueToResolve = model.eval(methodResult.value)
                val returnValue = resolveExpr(valueToResolve, method.returnType)
                val params = method.parameters.mapIndexed { idx, param ->
                    val lValue = URegisterStackLValue(typeToSort(param.type), idx)
                    val expr = model.read(lValue)
                    resolveExpr(expr, param.type)
                }

                return TSTest(params, returnValue)
            }

            is TSMethodResult.TSException -> {
                TODO()
            }

            is TSMethodResult.NoCall -> {
                TODO()
            }

            else -> error("Should not be called")
        }
    }

    private fun resolveExpr(expr: UExpr<out USort>, type: EtsType): TSObject = when (type) {
        is EtsPrimitiveType -> resolvePrimitive(expr, type)
        is EtsRefType -> TODO()
        else -> TODO()
    }

    private fun resolvePrimitive(expr: UExpr<out USort>, type: EtsPrimitiveType): TSObject = when (type) {
        EtsNumberType -> {
            when (expr.sort) {
                expr.ctx.fp64Sort -> TSObject.TSNumber.Double(extractDouble(expr))
                expr.ctx.bv32Sort -> TSObject.TSNumber.Integer(extractInt(expr))
                else -> error("Unexpected sort: ${expr.sort}")
            }
        }

        EtsBooleanType -> {
            TSObject.Boolean(extractBool(expr))
        }

        EtsUndefinedType -> {
            TSObject.UndefinedObject
        }

        is EtsLiteralType -> {
            TODO()
        }

        EtsNullType -> {
            TODO()
        }

        EtsNeverType -> {
            TODO()
        }

        EtsStringType -> {
            TODO()
        }

        EtsVoidType -> {
            TODO()
        }

        else -> error("Unexpected type: $type")
    }
}
