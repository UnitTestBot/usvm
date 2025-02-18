package org.usvm.util

import io.ksmt.expr.KBitVec32Value
import io.ksmt.utils.asExpr
import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.jacodb.ets.base.EtsArrayType
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
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.GlobalFieldValue
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.TsValue
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.tctx
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.FakeType
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.types.first
import org.usvm.types.single

class TsTestResolver {
    fun resolve(method: EtsMethod, state: TsState): TsTest = with(state.ctx) {
        val model = state.models.first()
        val memory = state.memory

        val beforeMemoryScope = MemoryScope(this, model, memory, method)
        val afterMemoryScope = MemoryScope(this, model, memory, method)

        val result = when (val res = state.methodResult) {
            is TsMethodResult.NoCall -> {
                error("No result found")
            }

            is TsMethodResult.Success -> {
                afterMemoryScope.withMode(ResolveMode.CURRENT) {
                    resolveExpr(res.value, method.returnType)
                }
            }

            is TsMethodResult.TsException -> {
                resolveException(res, afterMemoryScope)
            }
        }

        val before = beforeMemoryScope.withMode(ResolveMode.MODEL) { (this as MemoryScope).resolveState() }
        val after = afterMemoryScope.withMode(ResolveMode.CURRENT) { (this as MemoryScope).resolveState() }

        return TsTest(method, before, after, result, trace = emptyList())
    }

    @Suppress("unused")
    private fun resolveException(
        res: TsMethodResult.TsException,
        afterMemoryScope: MemoryScope,
    ): TsValue.TsException {
        // TODO support exceptions
        return TsValue.TsException
    }

    private class MemoryScope(
        ctx: TsContext,
        model: UModelBase<EtsType>,
        finalStateMemory: UReadOnlyMemory<EtsType>,
        method: EtsMethod,
    ) : TsTestStateResolver(ctx, model, finalStateMemory, method) {
        fun resolveState(): TsParametersState {
            val thisInstance = resolveThisInstance()
            val parameters = resolveParameters()

            val globals = resolveGlobals()

            return TsParametersState(thisInstance, parameters, globals)
        }
    }
}

open class TsTestStateResolver(
    val ctx: TsContext,
    private val model: UModelBase<EtsType>,
    private val finalStateMemory: UReadOnlyMemory<EtsType>,
    val method: EtsMethod,
) {
    fun resolveLValue(lValue: ULValue<*, *>, type: EtsType): TsValue {
        val expr = memory.read(lValue)
        return resolveExpr(expr, type)
    }

    fun resolveExpr(
        expr: UExpr<out USort>,
        type: EtsType,
    ): TsValue = when (type) {
        is EtsPrimitiveType -> resolvePrimitive(expr, type)
        is EtsRefType -> resolveRef(expr.asExpr(ctx.addressSort), type)
        else -> resolveUnknownExpr(expr)
    }

    fun resolveUnknownExpr(expr: UExpr<out USort>): TsValue = with(expr.tctx) {
        when (expr.sort) {
            fp64Sort -> resolvePrimitive(expr, EtsNumberType)
            boolSort -> resolvePrimitive(expr, EtsBooleanType)
            addressSort -> {
                if (expr.isFakeObject()) {
                    resolveFakeObject(expr)
                } else {
                    resolveRef(expr.asExpr(ctx.addressSort), EtsUnknownType)
                }
            }
            else -> TODO()
        }
    }

    private fun resolveRef(expr: UExpr<UAddressSort>, type: EtsType): TsValue {
        val instance = model.eval(expr) as UConcreteHeapRef

        if (instance.address == 0) {
            return TsValue.TsUndefined
        }

        if (model.eval(ctx.mkHeapRefEq(expr, ctx.mkTsNullValue())).isTrue) {
            return TsValue.TsNull
        }

        return when (type) {
            is EtsClassType -> resolveClass(instance, type)
            is EtsArrayType -> resolveArray(instance, type)
            is EtsUnknownType -> {
                val type = finalStateMemory.types.getTypeStream(expr).first()
                resolveRef(expr, type)
            }
            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveArray(expr: UConcreteHeapRef, type: EtsArrayType): TsValue.TsArray<*> {
        val arrayLengthLValue = UArrayLengthLValue(expr, type, ctx.sizeSort)
        val length = model.eval(memory.read(arrayLengthLValue)) as KBitVec32Value

        val values = (0 until length.intValue).map {
            val index = ctx.mkSizeExpr(it)
            val lValue = UArrayIndexLValue(ctx.addressSort, expr, index, type)
            val value = memory.read(lValue)

            if (model.eval(ctx.mkHeapRefEq(value, ctx.mkUndefinedValue())).isTrue) {
                return@map TsValue.TsUndefined
            }

            with(ctx) { check(value.isFakeObject()) { "Only fake objects are allowed in arrays" } }

            resolveFakeObject(value as UConcreteHeapRef)
        }

        return TsValue.TsArray(values)
    }

    fun resolveThisInstance(): TsValue? {
        val parametersCount = method.parameters.size
        val ref = URegisterStackLValue(ctx.addressSort, idx = parametersCount) // TODO check for statics
        val type = EtsClassType(method.enclosingClass)
        return resolveLValue(ref, type)
    }

    fun resolveParameters(): List<TsValue> = with(ctx) {
        method.parameters.mapIndexed { idx, param ->
            val sort = typeToSort(param.type)

            if (sort is TsUnresolvedSort) {
                // this means that a fake object was created, and we need to read it from the current memory
                val address = finalStateMemory.read(URegisterStackLValue(addressSort, idx)).asExpr(addressSort)
                check(address.isFakeObject())
                return@mapIndexed resolveFakeObject(address)
            }

            val ref = URegisterStackLValue(sort, idx)
            resolveLValue(ref, param.type)
        }
    }

    fun resolveGlobals(): Map<EtsClass, List<GlobalFieldValue>> {
        // TODO
        return emptyMap()
    }

    private fun resolveFakeObject(expr: UConcreteHeapRef): TsValue {
        val type = finalStateMemory.types.getTypeStream(expr.asExpr(ctx.addressSort)).single() as FakeType
        return when {
            model.eval(type.boolTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateBoolLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), EtsBooleanType)
            }

            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateFpLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), EtsNumberType)
            }

            model.eval(type.refTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateRefLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                val ref = model.eval(value)
                // TODO mistake with signature, use TypeStream instead
                resolveExpr(ref, EtsClassType(ctx.scene.projectAndSdkClasses.first().signature))
            }

            else -> error("Unsupported")
        }
    }

    private fun resolvePrimitive(
        expr: UExpr<out USort>,
        type: EtsPrimitiveType,
    ): TsValue = when (type) {
        EtsNumberType -> TsValue.TsNumber.TsDouble(extractDouble(evaluateInModel(expr)))
        EtsBooleanType -> TsValue.TsBoolean(evaluateInModel(expr).extractBool())
        EtsUndefinedType -> TsValue.TsUndefined
        is EtsLiteralType -> TODO()
        EtsNullType -> TODO()
        EtsNeverType -> TODO()
        EtsStringType -> TODO()
        EtsVoidType -> TODO()
        else -> error("Unexpected type: $type")
    }

    private fun resolveClass(
        expr: UExpr<out USort>,
        classType: EtsClassType,
    ): TsValue {
        if (expr is UConcreteHeapRef && expr.address == 0) {
            return TsValue.TsUndefined
        }

        val nullRef = ctx.mkTsNullValue()
        if (model.eval(ctx.mkHeapRefEq(expr.asExpr(ctx.addressSort), nullRef)).isTrue) {
            return TsValue.TsNull
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
            val fieldExpr = memory.read(lValue)
            val obj = resolveExpr(fieldExpr, field.type)
            field.name to obj
        }
        return TsValue.TsClass(clazz.name, properties)
    }

    private var resolveMode: ResolveMode = ResolveMode.ERROR

    fun <R> withMode(resolveMode: ResolveMode, body: TsTestStateResolver.() -> R): R {
        val prevValue = this.resolveMode
        try {
            this.resolveMode = resolveMode
            return body()
        } finally {
            this.resolveMode = prevValue
        }
    }

    fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
        return model.eval(expr)
    }

    val memory: UReadOnlyMemory<EtsType>
        get() = when (resolveMode) {
            ResolveMode.MODEL -> model
            ResolveMode.CURRENT -> finalStateMemory
            ResolveMode.ERROR -> error("Illegal operation for a model")
        }
}

enum class ResolveMode {
    MODEL, CURRENT, ERROR
}
