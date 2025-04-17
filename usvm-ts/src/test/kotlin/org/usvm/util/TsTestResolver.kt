package org.usvm.util

import io.ksmt.expr.KBitVec32Value
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.GlobalFieldValue
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.TsValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.FakeType
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
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
                    resolveExpr(res.value, res.value, method.returnType)
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
    fun resolveLValue(
        lValue: ULValue<*, *>,
        type: EtsType,
    ): TsValue {
        val expr = memory.read(lValue)
        val symbolicRef = if (lValue.sort == ctx.addressSort) {
            finalStateMemory.read(lValue).asExpr(ctx.addressSort)
        } else {
            null
        }
        return resolveExpr(expr, symbolicRef, type)
    }

    fun resolveExpr(
        expr: UExpr<out USort>,
        symbolicRef: UExpr<out USort>? = null,
        type: EtsType,
    ): TsValue = when (type) {
        is EtsPrimitiveType -> {
            resolvePrimitive(expr, type)
        }

        is EtsRefType -> {
            val finalStateMemoryRef = symbolicRef?.asExpr(ctx.addressSort) ?: expr.asExpr(ctx.addressSort)
            resolveTsValue(expr.asExpr(ctx.addressSort), finalStateMemoryRef, type)
        }

        else -> {
            resolveUnknownExpr(expr, symbolicRef)
        }
    }

    fun resolveUnknownExpr(
        heapRef: UExpr<out USort>,
        finalStateMemoryRef: UExpr<out USort>?,
    ): TsValue = with(ctx) {
        when (heapRef.sort) {
            fp64Sort -> {
                resolvePrimitive(heapRef, EtsNumberType)
            }

            boolSort -> {
                resolvePrimitive(heapRef, EtsBooleanType)
            }

            addressSort -> {
                if (heapRef.isFakeObject()) {
                    resolveFakeObject(heapRef)
                } else {
                    resolveTsValue(
                        heapRef.asExpr(ctx.addressSort),
                        finalStateMemoryRef?.asExpr(ctx.addressSort),
                        EtsUnknownType
                    )
                }
            }

            else -> TODO("Unsupported sort: ${heapRef.sort}")
        }
    }

    private fun resolveTsValue(
        heapRef: UExpr<UAddressSort>,
        finalStateMemoryRef: UExpr<UAddressSort>?,
        type: EtsType,
    ): TsValue {
        val concreteRef = evaluateInModel(heapRef) as UConcreteHeapRef

        if (concreteRef.address == 0) {
            return TsValue.TsUndefined
        }

        if (model.eval(ctx.mkHeapRefEq(heapRef, ctx.mkTsNullValue())).isTrue) {
            return TsValue.TsNull
        }

        return when (type) {
            // TODO add better support
            is EtsUnclearRefType -> {
                val cls = ctx.scene.projectAndSdkClasses.single { it.name == type.typeName }
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef, cls.type)
            }

            is EtsClassType -> {
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef, type)
            }

            is EtsArrayType -> {
                resolveTsArray(concreteRef, finalStateMemoryRef ?: heapRef, type)
            }

            is EtsUnknownType -> {
                val finalType = finalStateMemory.types.getTypeStream(heapRef).first()
                resolveTsValue(heapRef, finalStateMemoryRef, finalType)
            }

            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveTsArray(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
        type: EtsArrayType,
    ): TsValue.TsArray<*> = with(ctx) {
        val arrayLength = mkArrayLengthLValue(heapRef, type)
        val length = model.eval(memory.read(arrayLength)) as KBitVec32Value

        val values = (0 until length.intValue).map {
            val index = mkSizeExpr(it)
            val lValue = mkArrayIndexLValue(addressSort, heapRef, index, type)
            val value = memory.read(lValue)

            if (model.eval(mkHeapRefEq(value, mkUndefinedValue())).isTrue) {
                return@map TsValue.TsUndefined
            }

            with(ctx) { check(value.isFakeObject()) { "Only fake objects are allowed in arrays" } }

            resolveFakeObject(value as UConcreteHeapRef)
        }

        return TsValue.TsArray(values)
    }

    fun resolveThisInstance(): TsValue? {
        val parametersCount = method.parameters.size
        val ref = mkRegisterStackLValue(ctx.addressSort, parametersCount) // TODO check for statics
        val type = EtsClassType(method.signature.enclosingClass)
        return resolveLValue(ref, type)
    }

    fun resolveParameters(): List<TsValue> = with(ctx) {
        method.parameters.mapIndexed { idx, param ->
            val sort = typeToSort(param.type)

            if (sort is TsUnresolvedSort) {
                // this means that a fake object was created, and we need to read it from the current memory
                val address = finalStateMemory.read(mkRegisterStackLValue(addressSort, idx))
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
                resolveExpr(model.eval(value), value, EtsBooleanType)
            }

            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateFpLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), value, EtsNumberType)
            }

            model.eval(type.refTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateRefLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                val ref = model.eval(value)
                // TODO mistake with signature, use TypeStream instead
                // TODO: replace `scene.classes.first()` with something meaningful
                resolveExpr(ref, value, ctx.scene.projectAndSdkClasses.first().type)
            }

            else -> error("Unsupported")
        }
    }

    private fun resolvePrimitive(
        expr: UExpr<out USort>,
        type: EtsPrimitiveType,
    ): TsValue = with(ctx) {
        when (type) {
            EtsNumberType -> {
                val e = evaluateInModel(expr)
                if (e.isFakeObject()) {
                    val lValue = getIntermediateFpLValue(e.address)
                    val value = finalStateMemory.read(lValue)
                    resolveExpr(model.eval(value), value, EtsNumberType)
                } else {
                    TsValue.TsNumber.TsDouble(e.extractDouble())
                }
            }

            EtsBooleanType -> TsValue.TsBoolean(evaluateInModel(expr).extractBool())
            EtsUndefinedType -> TsValue.TsUndefined
            is EtsLiteralType -> TODO()
            EtsNullType -> TODO()
            EtsNeverType -> TODO()
            EtsStringType -> TODO()
            EtsVoidType -> TODO()
            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveClass(
        classType: EtsClassType,
    ): EtsClass {
        // Special case for Object:
        if (classType.signature.name == "Object") {
            return createObjectClass()
        }

        // Perfect signature:
        if (classType.signature.name != UNKNOWN_CLASS_NAME) {
            val classes = ctx.scene.projectAndSdkClasses.filter { it.signature == classType.signature }
            if (classes.size == 1) {
                return classes.single()
            }
        }

        // Sad signature:
        val classes = ctx.scene.projectAndSdkClasses.filter { it.signature.name == classType.signature.name }
        if (classes.size == 1) {
            return classes.single()
        }

        error("Could not resolve class: ${classType.signature}")
    }

    private fun resolveTsClass(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
        classType: EtsClassType,
    ): TsValue.TsClass = with(ctx) {
        val clazz = resolveClass(classType)
        val properties = clazz.fields
            .filterNot { field ->
                field as EtsFieldImpl
                field.modifiers.isStatic
            }
            .associate { field ->
                val sort = typeToSort(field.type)
                if (sort == unresolvedSort) {
                    val lValue = mkFieldLValue(addressSort, heapRef, field.signature)
                    val fieldExpr = finalStateMemory.read(lValue) as? UConcreteHeapRef
                        ?: error("UnresolvedSort should be represented by a fake object instance")
                    // TODO check values if fieldExpr is correct here
                    //      Probably we have to pass fieldExpr as symbolic value and something as a concrete one
                    val obj = resolveExpr(fieldExpr, fieldExpr, field.type)
                    field.name to obj
                } else {
                    val lValue = mkFieldLValue(sort, concreteRef.asExpr(addressSort), field.signature)
                    val fieldExpr = memory.read(lValue)
                    // TODO check values if fieldExpr is correct here
                    //      Probably we have to pass fieldExpr as symbolic value and something as a concrete one
                    val obj = resolveExpr(fieldExpr, fieldExpr, field.type)
                    field.name to obj
                }
            }
        TsValue.TsClass(clazz.name, properties)
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
