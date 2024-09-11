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
import org.usvm.TSContext
import org.usvm.TSObject
import org.usvm.TSRefTransformer
import org.usvm.TSTest
import org.usvm.TSWrappedValue
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.extractBool
import org.usvm.extractDouble
import org.usvm.extractInt
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase
import org.usvm.state.TSMethodResult
import org.usvm.state.TSState
import org.usvm.types.first

class TSTestResolver(
    val state: TSState
) {

    @Suppress("UNUSED_VARIABLE")
    fun resolve(method: EtsMethod): TSTest = with(state.ctx) {
        val model = state.models.first()
        when (val methodResult = state.methodResult) {
            is TSMethodResult.Success -> {
                val valueToResolve = model.eval(methodResult.value.extractOrThis())
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
        params.mapIndexed { idx, param ->
            val type = param.type
            val lValue = URegisterStackLValue(typeToSort(type), idx)
            val expr = model.read(lValue).extractOrThis()
            if (type is EtsUnknownType) {
                approximateParam(expr.cast(), idx, model)
            } else resolveExpr(expr, type, model)
        }
    }

    private fun approximateParam(expr: UConcreteHeapRef, idx: Int, model: UModelBase<EtsType>): TSObject =
        with(expr.ctx as TSContext) {
            val suggestedType = state.getSuggestedType(expr)
            return suggestedType?.let { newType ->
                val newLValue = URegisterStackLValue(typeToSort(newType), idx)
                val transformed = model.read(newLValue).extractOrThis()
                resolveExpr(transformed, newType, model)
            } ?: TSObject.Object(expr.address)
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

    private fun UExpr<out USort>.extractOrThis(): UExpr<out USort> = if (this is TSWrappedValue) value else this
}
