package org.usvm.util

import io.ksmt.utils.cast
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
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsVoidType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.TSObject
import org.usvm.api.TSTest
import org.usvm.api.typeStreamOf
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSRefTransformer
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.tctx
import org.usvm.machine.state.TSMethodResult
import org.usvm.machine.state.TSState
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase
import org.usvm.types.TypesResult
import org.usvm.types.first

class TSTestResolver(
    private val state: TSState,
) {

    fun resolve(method: EtsMethod): TSTest = with(state.ctx) {
        val model = state.models.first()
        when (val methodResult = state.methodResult) {
            is TSMethodResult.Success -> {
                val value = methodResult.value.boolValue ?: methodResult.value.fpValue ?: methodResult.value.refValue ?: mkUndefinedValue()
                val valueToResolve = model.eval(value)
                val returnValue = resolveExpr(valueToResolve, method.returnType, model)
                val params = resolveParams(method.parameters, this, model)

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

    private fun resolveParams(
        params: List<EtsMethodParameter>,
        ctx: TSContext,
        model: UModelBase<EtsType>,
    ): List<TSObject> = with(ctx) {
        params.map {  param ->
            val type = param.type
            val lValue = URegisterStackLValue(typeToSort(type), param.index)
            val expr = model.read(lValue)
            if (type is EtsUnknownType) {
                approximateParam(expr.cast(), param.index, model)
            } else {
                resolveExpr(expr, type, model)
            }
        }
    }

    private fun approximateParam(expr: UConcreteHeapRef, idx: Int, model: UModelBase<EtsType>): TSObject =
        when (val tr = model.typeStreamOf(expr).take(1)) {
            is TypesResult.SuccessfulTypesResult -> with (expr.tctx) {
                val newType = tr.types.first()
                val newLValue = URegisterStackLValue(typeToSort(newType), idx)
                // val transformed = model.read(newLValue).unwrapIfRequired()
                // resolveExpr(transformed, newType, model)
                TODO()
            }

            else -> TSObject.Object(expr.address)
        }

    private fun resolveExpr(expr: UExpr<out USort>, type: EtsType, model: UModelBase<*>): TSObject {
        return when {
            type is EtsUnknownType && expr is UConcreteHeapRef -> resolveUnknown(expr, model)
            type is EtsPrimitiveType -> resolvePrimitive(expr, type)
            type is EtsRefType -> TODO()
            else -> TODO()
        }
    }

    private fun resolveUnknown(expr: UConcreteHeapRef, model: UModelBase<*>): TSObject {
        val typeStream = model.types.getTypeStream(expr)

        val ctx = expr.ctx as TSContext
        return (typeStream.first() as? EtsType)?.let { type ->
            val transformed = TSRefTransformer(ctx, ctx.typeToSort(type)).apply(expr)
            resolveExpr(transformed, type, model)
        } ?: TSObject.Object(expr.address)
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
