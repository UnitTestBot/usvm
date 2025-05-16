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
import org.usvm.api.TsTestValue
import org.usvm.api.typeStreamOf
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.types.first

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
    ): TsTestValue.TsException {
        // TODO support exceptions
        return TsTestValue.TsException
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
    ): TsTestValue {
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
    ): TsTestValue = when (type) {
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
    ): TsTestValue = with(ctx) {
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
    ): TsTestValue {
        val concreteRef = evaluateInModel(heapRef) as UConcreteHeapRef

        if (concreteRef.address == 0) {
            return TsTestValue.TsUndefined
        }

        if (model.eval(ctx.mkHeapRefEq(heapRef, ctx.mkTsNullValue())).isTrue) {
            return TsTestValue.TsNull
        }

        return when (type) {
            // TODO add better support
            is EtsUnclearRefType -> {
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef)
            }

            is EtsClassType -> {
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef)
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
    ): TsTestValue.TsArray<*> = with(ctx) {
        val arrayLength = mkArrayLengthLValue(heapRef, type)
        val length = model.eval(memory.read(arrayLength)) as KBitVec32Value

        val values = (0 until length.intValue).map {
            val index = mkSizeExpr(it)
            val lValue = mkArrayIndexLValue(addressSort, heapRef, index, type)
            val value = memory.read(lValue)

            if (model.eval(mkHeapRefEq(value, mkUndefinedValue())).isTrue) {
                return@map TsTestValue.TsUndefined
            }

            with(ctx) { check(value.isFakeObject()) { "Only fake objects are allowed in arrays" } }

            resolveFakeObject(value as UConcreteHeapRef)
        }

        return TsTestValue.TsArray(values)
    }

    fun resolveThisInstance(): TsTestValue? {
        val parametersCount = method.parameters.size
        val ref = mkRegisterStackLValue(ctx.addressSort, parametersCount) // TODO check for statics
        val type = EtsClassType(method.signature.enclosingClass)
        return resolveLValue(ref, type)
    }

    fun resolveParameters(): List<TsTestValue> = with(ctx) {
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

    private fun resolveFakeObject(expr: UConcreteHeapRef): TsTestValue = with(ctx) {
        val type = expr.getFakeType(finalStateMemory)
        // Note that everything about the details of a fake object
        // we need to read from the final state of the memory,
        // because they are allocated objects.
        return when {
            model.eval(type.boolTypeExpr).isTrue -> {
                val lValue = getIntermediateBoolLValue(expr.address)
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), value, EtsBooleanType)
            }

            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = getIntermediateFpLValue(expr.address)
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), value, EtsNumberType)
            }

            model.eval(type.refTypeExpr).isTrue -> {
                val lValue = getIntermediateRefLValue(expr.address)
                val value = finalStateMemory.read(lValue)
                val ref = model.eval(value)
                // TODO mistake with signature, use TypeStream instead
                // TODO: replace `scene.classes.first()` with something meaningful
                resolveExpr(ref, value, scene.projectAndSdkClasses.first().type)
            }

            else -> error("Unsupported")
        }
    }

    private fun resolvePrimitive(
        expr: UExpr<out USort>,
        type: EtsPrimitiveType,
    ): TsTestValue = with(ctx) {
        when (type) {
            EtsNumberType -> {
                val e = evaluateInModel(expr)
                if (e.isFakeObject()) {
                    val lValue = getIntermediateFpLValue(e.address)
                    val value = finalStateMemory.read(lValue)
                    resolveExpr(model.eval(value), value, EtsNumberType)
                } else {
                    TsTestValue.TsNumber.TsDouble(e.extractDouble())
                }
            }

            EtsBooleanType -> TsTestValue.TsBoolean(evaluateInModel(expr).toConcreteBoolValue())
            EtsUndefinedType -> TsTestValue.TsUndefined
            is EtsLiteralType -> TODO()
            EtsNullType -> TODO()
            EtsNeverType -> TODO()
            EtsStringType -> TODO()
            EtsVoidType -> TODO()
            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveClass(
        refType: EtsRefType,
    ): EtsClass {
        if (refType is EtsArrayType) {
            TODO()
        }

        // Special case for Object:
        val name = when (refType) {
            is EtsClassType -> refType.signature.name
            is EtsUnclearRefType -> refType.name
            else -> error("Unsupported $refType")
        }

        if (name == "Object") {
            return createObjectClass()
        }

        // Perfect signature:
        if (name != UNKNOWN_CLASS_NAME) {
            val classes = ctx.scene.projectAndSdkClasses.filter {
                when (refType) {
                    is EtsClassType -> it.signature == refType.signature
                    is EtsUnclearRefType -> it.name == refType.typeName
                    else -> error("TODO")
                }
            }
            if (classes.size == 1) {
                return classes.single()
            }
        }

        // Sad signature:
        val classes = ctx.scene.projectAndSdkClasses.filter { it.signature.name == name }
        if (classes.size == 1) {
            return classes.single()
        }

        error("Could not resolve class: $refType")
    }

    private fun resolveTsClass(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
    ): TsTestValue.TsClass = with(ctx) {
        val type = model.typeStreamOf(concreteRef).first()
        check(type is EtsRefType) { "Expected EtsRefType, but got $type" }
        val clazz = resolveClass(type)
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
        TsTestValue.TsClass(clazz.name, properties)
    }

    internal var resolveMode: ResolveMode = ResolveMode.ERROR

    internal inline fun <R> withMode(
        resolveMode: ResolveMode,
        body: TsTestStateResolver.() -> R,
    ): R {
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
