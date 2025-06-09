package org.usvm.util

import io.ksmt.expr.KFpValue
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
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.GlobalFieldValue
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.api.typeStreamOf
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.isAllocated
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModel
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.types.first

class TsTestResolver {
    private val resolvedLValuesToFakeObjects: MutableList<Pair<ULValue<*, *>, UConcreteHeapRef>> = mutableListOf()

    fun resolve(method: EtsMethod, state: TsState): TsTest = with(state.ctx) {
        val model = state.models.first()
        val memory = state.memory

        prepareForResolve(state)

        val beforeMemoryScope = MemoryScope(this, model, memory, method, resolvedLValuesToFakeObjects)
        val afterMemoryScope = MemoryScope(this, model, memory, method, resolvedLValuesToFakeObjects)

        val result = when (val res = state.methodResult) {
            is TsMethodResult.NoCall -> {
                error("No result found")
            }

            is TsMethodResult.Success -> {
                afterMemoryScope.withMode(ResolveMode.CURRENT) {
                    resolveExpr(res.value)
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

    private fun prepareForResolve(state: TsState) {
        state.lValuesToAllocatedFakeObjects.map { (lValue, fakeObject) ->
            when (lValue) {
                is UFieldLValue<*, *> -> {
                    val resolvedRef = state.models.first().eval(lValue.ref)
                    val fieldLValue = UFieldLValue(lValue.sort, resolvedRef, lValue.field)
                    resolvedLValuesToFakeObjects += fieldLValue to fakeObject
                }

                is UArrayIndexLValue<*, *, *> -> {
                    val model = state.models.first()
                    val resolvedRef = model.eval(lValue.ref)
                    val resolvedIndex = model.eval(lValue.index)
                    val arrayIndexLValue = UArrayIndexLValue(
                        lValue.sort,
                        resolvedRef,
                        resolvedIndex,
                        lValue.arrayType,
                    )
                    resolvedLValuesToFakeObjects += arrayIndexLValue to fakeObject
                }

                else -> error("Unexpected lValue type: ${lValue::class.java.name}")
            }
        }
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
        resolvedLValuesToFakeObjects: List<Pair<ULValue<*, *>, UConcreteHeapRef>>,
    ) : TsTestStateResolver(ctx, model, finalStateMemory, method, resolvedLValuesToFakeObjects) {
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
    val resolvedLValuesToFakeObjects: List<Pair<ULValue<*, *>, UConcreteHeapRef>>,
) {
    fun resolveLValue(
        lValue: ULValue<*, *>,
    ): TsTestValue {
        val expr = memory.read(lValue)

        return resolveExpr(expr)
    }

    fun resolveExpr(
        expr: UExpr<out USort>,
    ): TsTestValue = with(ctx) {
        when (expr.sort) {
            fp64Sort -> {
                this@TsTestStateResolver.resolvePrimitive(expr, EtsNumberType)
            }

            boolSort -> {
                this@TsTestStateResolver.resolvePrimitive(expr, EtsBooleanType)
            }

            addressSort -> {
                if (expr.isFakeObject()) {
                    this@TsTestStateResolver.resolveFakeObject(expr)
                } else {
                    this@TsTestStateResolver.resolveTsValue(
                        expr.asExpr(this@TsTestStateResolver.ctx.addressSort),
                    )
                }
            }

            sizeSort -> {
                this@TsTestStateResolver.resolvePrimitive(expr, EtsNumberType)
            }

            else -> TODO("Unsupported sort: ${expr.sort}")
        }
    }

    private fun resolveTsValue(
        heapRef: UExpr<UAddressSort>,
    ): TsTestValue {
        val concreteRef = evaluateInModel(heapRef) as UConcreteHeapRef

        if (concreteRef.address == 0) {
            return TsTestValue.TsUndefined
        }

        if (model.eval(ctx.mkHeapRefEq(heapRef, ctx.mkTsNullValue())).isTrue) {
            return TsTestValue.TsNull
        }

        val type = if (concreteRef.isAllocated) {
            finalStateMemory.typeStreamOf(concreteRef).first()
        } else {
            model.typeStreamOf(concreteRef).first()
        }

        return when (type) {
            // TODO add better support
            is EtsUnclearRefType -> {
                resolveTsClass(concreteRef, heapRef)
            }

            is EtsClassType -> {
                resolveTsClass(concreteRef, heapRef)
            }

            is EtsArrayType -> {
                resolveTsArray(concreteRef, heapRef, type)
            }

            is EtsUnknownType -> {
                resolveTsValue(heapRef)
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
        val length = resolveLValue(arrayLength) as TsTestValue.TsNumber

        val values = (0 until length.number.toInt()).map { i ->
            val index = mkSizeExpr(i)
            val sort = typeToSort(type.elementType)

            if (sort is TsUnresolvedSort) {
                val arrayIndexLValue = mkArrayIndexLValue(addressSort, concreteRef, index, type)
                val fakeObject = if (memory is UModel) {
                    resolvedLValuesToFakeObjects.firstOrNull { it.first == arrayIndexLValue }?.second
                } else {
                    resolvedLValuesToFakeObjects.lastOrNull { it.first == arrayIndexLValue }?.second
                }

                fakeObject ?: return@map TsTestValue.TsUndefined

                check(fakeObject.isFakeObject())

                val fakeType = fakeObject.getFakeType(finalStateMemory)
                return@map when {
                    model.eval(fakeType.fpTypeExpr).isTrue -> {
                        resolveExpr(fakeObject.extractFp(finalStateMemory))
                    }

                    model.eval(fakeType.boolTypeExpr).isTrue -> {
                        resolveExpr(fakeObject.extractBool(finalStateMemory))
                    }

                    model.eval(fakeType.refTypeExpr).isTrue -> {
                        resolveExpr(fakeObject.extractRef(finalStateMemory))
                    }

                    else -> error("Unsupported fake object type: $fakeType")
                }
            }

            require(sort is UFpSort || sort is UBoolSort) {
                "Other sorts must be resolved above, but got: $sort"
            }

            val lValue = mkArrayIndexLValue(sort, concreteRef, index, type)
            val value = memory.read(lValue)

            if (value.sort is UAddressSort) {
                if (model.eval(mkHeapRefEq(value.asExpr(addressSort), mkUndefinedValue())).isTrue) {
                    return@map TsTestValue.TsUndefined
                }
            }

            resolveExpr(value)
        }

        return TsTestValue.TsArray(values)
    }

    fun resolveThisInstance(): TsTestValue? {
        val parametersCount = method.parameters.size
        val ref = mkRegisterStackLValue(ctx.addressSort, parametersCount) // TODO check for statics
        return resolveLValue(ref)
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
            resolveLValue(ref)
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
                resolveExpr(model.eval(value))
            }

            model.eval(type.fpTypeExpr).isTrue -> {
                val lValue = getIntermediateFpLValue(expr.address)
                val value = finalStateMemory.read(lValue)
                resolveExpr(model.eval(value))
            }

            model.eval(type.refTypeExpr).isTrue -> {
                val lValue = getIntermediateRefLValue(expr.address)
                val value = finalStateMemory.read(lValue)
                val ref = model.eval(value)
                resolveExpr(ref)
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
                    resolveExpr(model.eval(value))
                } else {
                    if (e is KFpValue<*>) {
                        TsTestValue.TsNumber.TsDouble(e.extractDouble())
                    } else {
                        TsTestValue.TsNumber.TsInteger(e.extractInt())
                    }
                }
            }

            EtsBooleanType -> TsTestValue.TsBoolean(evaluateInModel(expr).toConcreteBoolValue())
            EtsUndefinedType -> TsTestValue.TsUndefined
            is EtsLiteralType -> TODO()
            EtsNullType -> TODO()
            EtsNeverType -> TODO()
            EtsStringType -> TsTestValue.TsString("String construction is not yet implemented")
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

        // TODO incorrect
        return classes.first()
    }

    private fun resolveTsClass(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
    ): TsTestValue.TsClass = with(ctx) {
        val type = if (concreteRef.isAllocated) {
            finalStateMemory.typeStreamOf(concreteRef).first()
        } else {
            model.typeStreamOf(concreteRef).first()
        }
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

                    val fakeObject = if (memory is UModel) {
                        resolvedLValuesToFakeObjects.firstOrNull { it.first == lValue }?.second
                    } else {
                        resolvedLValuesToFakeObjects.lastOrNull { it.first == lValue }?.second
                    }

                    if (fakeObject != null) {
                        val obj = resolveFakeObject(fakeObject)
                        field.name to obj
                    } else {
                        val fieldExpr = finalStateMemory.read(lValue) as? UConcreteHeapRef
                            ?: error("UnresolvedSort should be represented by a fake object instance")
                        // TODO check values if fieldExpr is correct here
                        //      Probably we have to pass fieldExpr as symbolic value and something as a concrete one
                        val obj = resolveExpr(fieldExpr)
                        field.name to obj
                    }
                } else {
                    val lValue = mkFieldLValue(sort, concreteRef.asExpr(addressSort), field.signature)
                    val fieldExpr = memory.read(lValue)
                    // TODO check values if fieldExpr is correct here
                    //      Probably we have to pass fieldExpr as symbolic value and something as a concrete one
                    val obj = resolveExpr(fieldExpr)
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
