package org.usvm.util

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
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
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.TSObject
import org.usvm.api.TSTest
import org.usvm.isTrue
import org.usvm.machine.FakeType
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.TSContext
import org.usvm.machine.expr.TSRefTransformer
import org.usvm.machine.expr.TSUnresolvedSort
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.tctx
import org.usvm.machine.state.TSMethodResult
import org.usvm.machine.state.TSState
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase
import org.usvm.types.first
import org.usvm.types.single

class TSTestResolver(
    private val state: TSState,
) {
    private val ctx: TSContext get() = state.ctx

    fun resolve(method: EtsMethod): TSTest = with(ctx) {
        val model = state.models.first()
        when (val methodResult = state.methodResult) {
            is TSMethodResult.Success -> {
                val value = methodResult.value
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
            val sort = typeToSort(param.type).takeUnless { it is TSUnresolvedSort } ?: addressSort
            val lValue = URegisterStackLValue(sort, param.index)
            val expr = state.memory.read(lValue) // TODO error
            if (param.type is EtsUnknownType) {
                approximateParam(expr.cast(), model)
            } else {
                resolveExpr(expr, param.type, model)
            }
        }
    }

    private fun approximateParam(expr: UConcreteHeapRef, model: UModelBase<EtsType>): TSObject {
        if (expr.address == 0) {
            return TSObject.UndefinedObject
        }
        val type = state.memory.types.getTypeStream(expr.asExpr(expr.tctx.addressSort)).single() as FakeType
        return when {
            model.eval(type.boolTypeExpr).isTrue -> {
                val lValue = expr.tctx.getIntermediateBoolLValue(expr.address)
                val value = state.memory.read(lValue)
                resolveExpr(value, EtsBooleanType, model)
            }
            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = expr.tctx.getIntermediateFpLValue(expr.address)
                val value = state.memory.read(lValue)
                resolveExpr(value, EtsNumberType, model)
            }
            model.eval(type.refTypeExpr).isTrue -> {
                val lValue = expr.tctx.getIntermediateRefLValue(expr.address)
                val value = state.memory.read(lValue)
                resolveExpr(value, EtsClassType(EtsClassSignature.DEFAULT), model)
            }
            else -> error("Unsupported")
        }
    }
    private fun resolveExpr(
        expr: UExpr<out USort>,
        type: EtsType,
        model: UModelBase<*>,
    ): TSObject = when {
        type is EtsUnknownType && expr is UConcreteHeapRef -> resolveUnknown(expr, model)
        type is EtsPrimitiveType -> resolvePrimitive(expr, type)
        type is EtsClassType -> resolveClass(expr, type, model)
        type is EtsRefType -> TODO()
        else -> TODO()
    }

    private fun resolveUnknown(
        expr: UConcreteHeapRef,
        model: UModelBase<*>,
    ): TSObject {
        val typeStream = model.types.getTypeStream(expr)
        return (typeStream.first() as? EtsType)?.let { type ->
            val sort = ctx.typeToSort(type)
            val transformed = TSRefTransformer(ctx, sort).apply(expr)
            resolveExpr(transformed, type, model)
        } ?: TSObject.TSObject(expr.address)
    }

    private fun resolvePrimitive(
        expr: UExpr<out USort>,
        type: EtsPrimitiveType,
    ): TSObject = when (type) {
        EtsNumberType -> {
            when (expr.sort) {
                ctx.fp64Sort -> TSObject.TSNumber.Double(extractDouble(expr))
                ctx.bv32Sort -> TSObject.TSNumber.Integer(extractInt(expr))
                else -> error("Unexpected sort: ${expr.sort}")
            }
        }

        EtsBooleanType -> {
            TSObject.TSBoolean(extractBool(expr))
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

    private fun resolveClass(
        expr: UExpr<out USort>,
        classType: EtsClassType,
        model: UModelBase<*>,
    ): TSObject.TSClass {
        val clazz = ctx.scene.classes.first { it.signature == classType.signature }
        val properties = clazz.fields.associate { field ->
            check(expr.sort == ctx.addressSort) {
                "Expected address sort, but got ${expr.sort}"
            }
            val sort = ctx.typeToSort(field.type)
            val lValue = UFieldLValue(sort, expr.asExpr(ctx.addressSort), field.name)
            val fieldExpr = model.read(lValue)
            val obj = resolveExpr(fieldExpr, field.type, model)
            field.name to obj
        }
        return TSObject.TSClass(clazz.name, properties)
    }
}
