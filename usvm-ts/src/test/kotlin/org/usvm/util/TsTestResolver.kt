package org.usvm.util

import io.ksmt.expr.KBitVec32Value
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsUnclearType
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import mu.KotlinLogging
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
import org.usvm.machine.expr.TsMethod
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.FakeType
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.types.first
import org.usvm.types.single
import org.usvm.machine.expr.*

private val logger = KotlinLogging.logger {}

class TsTestResolver {
    fun resolve(method: TsMethod, state: TsState): TsTest = with(state.ctx) {
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
        model: UModelBase<TsType>,
        finalStateMemory: UReadOnlyMemory<TsType>,
        method: TsMethod,
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
    private val model: UModelBase<TsType>,
    private val finalStateMemory: UReadOnlyMemory<TsType>,
    val method: TsMethod,
) {
    fun resolveLValue(
        lValue: ULValue<*, *>,
        type: TsType,
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
        type: TsType,
    ): TsTestValue = when (type) {
        is TsPrimitiveType -> {
            resolvePrimitive(expr, type)
        }

        is TsRefType -> {
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
                resolvePrimitive(heapRef, TsNumberType)
            }

            boolSort -> {
                resolvePrimitive(heapRef, TsBooleanType)
            }

            addressSort -> {
                if (heapRef.isFakeObject()) {
                    resolveFakeObject(heapRef)
                } else {
                    resolveTsValue(
                        heapRef.asExpr(ctx.addressSort),
                        finalStateMemoryRef?.asExpr(ctx.addressSort),
                        TsUnknownType
                    )
                }
            }

            else -> TODO("Unsupported sort: ${heapRef.sort}")
        }
    }

    private fun resolveTsValue(
        heapRef: UExpr<UAddressSort>,
        finalStateMemoryRef: UExpr<UAddressSort>?,
        type: TsType,
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
            is EtsUnclearType -> {
                val classes = ctx.scene.projectAndSdkClasses.filter { it.name == type.typeName }
                if (classes.size != 1) {
                    println("Could not resolve class: $type")
                    return TsTestValue.TsUndefined
                }
                val cls = classes.single()
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef, cls.type)
            }

            is TsClassType -> {
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef, type)
            }

            is TsArrayType -> {
                resolveTsArray(concreteRef, finalStateMemoryRef ?: heapRef, type)
            }

            is TsUnknownType -> {
                val finalType = finalStateMemory.types.getTypeStream(heapRef).first()
                resolveTsValue(heapRef, finalStateMemoryRef, finalType)
            }

            else -> {
                logger.info { "Unexpected type ${type::class.simpleName}: $type" }
                TsTestValue.TsUndefined
            }
        }
    }

    private fun resolveTsArray(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
        type: TsArrayType,
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

            val ref = finalStateMemory.read(mkRegisterStackLValue(addressSort, idx))
            if (ref.isFakeObject()) {
                return@mapIndexed resolveFakeObject(ref)
            }

            val lValue = mkRegisterStackLValue(sort, idx)
            resolveLValue(lValue, param.type)
        }
    }

    fun resolveGlobals(): Map<TsClass, List<GlobalFieldValue>> {
        // TODO
        return emptyMap()
    }

    private fun resolveFakeObject(expr: UConcreteHeapRef): TsTestValue {
        val type = finalStateMemory.typeStreamOf(expr).single() as FakeType
        return when {
            model.eval(type.boolTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateBoolLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), value, TsBooleanType)
            }

            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = ctx.getIntermediateFpLValue(expr.address)
                // Note that everything about details of fake object we need to read from final state of the memory
                // since they are allocated objects
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value), value, TsNumberType)
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
        type: TsPrimitiveType,
    ): TsTestValue = with(ctx) {
        when (type) {
            TsNumberType -> {
                val e = evaluateInModel(expr)
                if (e.isFakeObject()) {
                    val lValue = getIntermediateFpLValue(e.address)
                    val value = finalStateMemory.read(lValue)
                    resolveExpr(model.eval(value), value, TsNumberType)
                } else {
                    TsTestValue.TsNumber.TsDouble(e.extractDouble())
                }
            }

            TsBooleanType -> TsTestValue.TsBoolean(evaluateInModel(expr).extractBool())
            TsUndefinedType -> TsTestValue.TsUndefined
            is TsLiteralType -> TODO()
            TsNullType -> TODO()
            TsNeverType -> TODO()
            TsStringType -> {
                TsTestValue.TsNumber.TsDouble(evaluateInModel(expr).extractDouble())
            }

            TsVoidType -> TODO()
            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveClass(
        classType: TsClassType,
    ): TsClass {
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
        classType: TsClassType,
    ): TsTestValue.TsClass = with(ctx) {
        val clazz = resolveClass(classType)
        val properties = clazz.fields
            .filterNot { field ->
                field.modifiers.isStatic
            }
            .associate { field ->
                val sort = typeToSort(field.type)
                if (sort == unresolvedSort) {
                    val lValue = mkFieldLValue(addressSort, heapRef, field.signature)
                    val fieldExpr = memory.read(lValue) as? UConcreteHeapRef
                    // ?: error("UnresolvedSort should be represented by a fake object instance")
                        ?: run {
                            return@associate field.name to TsTestValue.TsUndefined
                        }
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

    val memory: UReadOnlyMemory<TsType>
        get() = when (resolveMode) {
            ResolveMode.MODEL -> model
            ResolveMode.CURRENT -> finalStateMemory
            ResolveMode.ERROR -> error("Illegal operation for a model")
        }
}

enum class ResolveMode {
    MODEL, CURRENT, ERROR
}
