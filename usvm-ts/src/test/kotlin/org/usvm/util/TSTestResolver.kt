package org.usvm.util

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.base.CONSTRUCTOR_NAME
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
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.TsObject
import org.usvm.api.TsTest
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.types.FakeType
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.memory.URegisterStackLValue
import org.usvm.model.UModelBase
import org.usvm.types.first
import org.usvm.types.single

class TsTestResolver(
    private val state: TsState,
) {
    private val ctx: TsContext get() = state.ctx

    fun resolve(method: EtsMethod): TsTest = with(ctx) {
        val model = state.models.first()
        when (val methodResult = state.methodResult) {
            is TsMethodResult.Success -> {
                val value = methodResult.value
                val valueToResolve = model.eval(value)
                val returnValue = resolveExpr(valueToResolve, method.returnType, model)
                val params = resolveParams(method.parameters, this, model)

                return TsTest(params, returnValue)
            }

            is TsMethodResult.TsException -> {
                val params = resolveParams(method.parameters, this, model)
                return TsTest(params, TsObject.TsException)
            }

            is TsMethodResult.NoCall -> {
                TODO()
            }

            else -> error("Should not be called")
        }
    }

    private fun resolveParams(
        params: List<EtsMethodParameter>,
        ctx: TsContext,
        model: UModelBase<EtsType>,
    ): List<TsObject> = with(ctx) {
        params.map { param ->
            val sort = typeToSort(param.type).takeUnless { it is TsUnresolvedSort } ?: addressSort
            val lValue = URegisterStackLValue(sort, param.index)
            val expr = state.memory.read(lValue) // TODO error
            if (param.type is EtsUnknownType) {
                resolveFakeObject(expr.cast(), model)
            } else {
                resolveExpr(model.eval(expr), param.type, model)
            }
        }
    }

    private fun resolveFakeObject(expr: UConcreteHeapRef, model: UModelBase<EtsType>): TsObject {
        val type = state.memory.types.getTypeStream(expr.asExpr(ctx.addressSort)).single() as FakeType
        return when {
            model.eval(type.boolTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateBoolLValue(expr.address)
                val value = state.memory.read(lValue)
                resolveExpr(model.eval(value), EtsBooleanType, model)
            }

            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateFpLValue(expr.address)
                val value = state.memory.read(lValue)
                resolveExpr(model.eval(value), EtsNumberType, model)
            }

            model.eval(type.refTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateRefLValue(expr.address)
                val value = state.memory.read(lValue)
                resolveExpr(model.eval(value), EtsClassType(ctx.scene.projectAndSdkClasses.first().signature), model)
            }

            else -> error("Unsupported")
        }
    }

    private fun resolveExpr(
        expr: UExpr<out USort>,
        type: EtsType,
        model: UModelBase<*>,
    ): TsObject = when {
        type is EtsUnknownType && expr is UConcreteHeapRef -> resolveUnknown(expr, model)
        type is EtsPrimitiveType -> resolvePrimitive(expr, type)
        type is EtsClassType -> resolveClass(expr, type, model)
        type is EtsRefType -> TODO()
        else -> TODO()
    }

    private fun resolveUnknown(
        expr: UConcreteHeapRef,
        model: UModelBase<*>,
    ): TsObject {
        val typeStream = model.types.getTypeStream(expr)
        return (typeStream.first() as? EtsType)?.let { type ->
            resolveExpr(expr, type, model)
        } ?: TsObject.TsObject(expr.address)
    }

    private fun resolvePrimitive(
        expr: UExpr<out USort>,
        type: EtsPrimitiveType,
    ): TsObject = when (type) {
        EtsNumberType -> {
            when (expr.sort) {
                ctx.fp64Sort -> TsObject.TsNumber.Double(extractDouble(expr))
                ctx.bv32Sort -> TsObject.TsNumber.Integer(extractInt(expr))
                else -> error("Unexpected sort: ${expr.sort}")
            }
        }

        EtsBooleanType -> {
            TsObject.TsBoolean(expr.extractBool())
        }

        EtsUndefinedType -> {
            TsObject.TsUndefinedObject
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
    ): TsObject {
        if (expr is UConcreteHeapRef && expr.address == 0) {
            return TsObject.TsUndefinedObject
        }

        val nullRef = ctx.mkTsNullValue()
        if (model.eval(ctx.mkHeapRefEq(expr.asExpr(ctx.addressSort), nullRef)).isTrue) {
            return TsObject.TsNull
        }

        check(expr.sort == ctx.addressSort) {
            "Expected address sort, but got ${expr.sort}"
        }

        val clazz = ctx.scene.projectAndSdkClasses.firstOrNull { it.signature == classType.signature }
            ?: if (classType.signature.name == "Object") {
                EtsClassImpl(
                    signature = classType.signature,
                    fields = emptyList(),
                    methods = emptyList(),
                    ctor = EtsMethodImpl(
                        EtsMethodSignature(
                            enclosingClass = classType.signature,
                            name = CONSTRUCTOR_NAME,
                            parameters = emptyList(),
                            returnType = classType,
                        )
                    ),
                )
            } else {
                error("Class not found: ${classType.signature}")
            }

        val properties = clazz.fields.associate { field ->
            val sort = ctx.typeToSort(field.type)
            val lValue = UFieldLValue(sort, expr.asExpr(ctx.addressSort), field.name)
            val fieldExpr = model.read(lValue)
            val obj = resolveExpr(fieldExpr, field.type, model)
            field.name to obj
        }
        return TsObject.TsClass(clazz.name, properties)
    }
}
