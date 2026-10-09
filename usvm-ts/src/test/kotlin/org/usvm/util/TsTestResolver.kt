package org.usvm.util

import io.ksmt.expr.KBitVec16Value
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
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.TrackedObjectProperty
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.deletedFieldLValue
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.expr.extractInt
import org.usvm.machine.expr.initialPropertyPresenceLValue
import org.usvm.machine.expr.readInputPropertyValue
import org.usvm.machine.expr.toConcreteBoolValue
import org.usvm.machine.expr.writtenPropertyLValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.readUnresolvedArrayElement
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.types.first

/** A satisfying state lacks the modeled data needed to emit a concrete witness. */
class TsUnsupportedWitnessException(message: String) : IllegalStateException(message)

class TsTestResolver {
    private val resolvedLValuesToFakeObjects: MutableList<Pair<ULValue<*, *>, UConcreteHeapRef>> = mutableListOf()

    fun resolve(method: EtsMethod, state: TsState): TsTest = with(state.ctx) {
        val model = state.models.first()
        val memory = state.memory

        prepareForResolve(state)

        val beforeMemoryScope = MemoryScope(
            this,
            model,
            memory,
            method,
            resolvedLValuesToFakeObjects,
            state.maxStringLength,
            propertyState = state,
        )
        val afterMemoryScope = MemoryScope(
            this,
            model,
            memory,
            method,
            resolvedLValuesToFakeObjects,
            state.maxStringLength,
            propertyState = state,
        )

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

        val before = beforeMemoryScope.withMode(ResolveMode.MODEL) { resolveState() }
        val after = afterMemoryScope.withMode(ResolveMode.CURRENT) { resolveState() }

        return TsTest(method, before, after, result, trace = emptyList())
    }

    private fun prepareForResolve(state: TsState) {
        state.lValuesToAllocatedFakeObjects.forEach { (lValue, fakeObject) ->
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

                else -> {
                    error("Unexpected lValue type: ${lValue::class.java.name}")
                }
            }
        }
    }

    private fun resolveException(
        res: TsMethodResult.TsException,
        afterMemoryScope: MemoryScope,
    ): TsTestValue.TsException {
        return afterMemoryScope.withMode(ResolveMode.CURRENT) {
            try {
                // Dispatch based on the exception type, similar to string const resolution
                when (res.type) {
                    is EtsStringType -> {
                        val concreteRef = evaluateInModel(res.value) as? UConcreteHeapRef
                        if (concreteRef != null && isAllocatedConcreteHeapRef(concreteRef)) {
                            val stringValue = ctx.getStringConstantValue(concreteRef)
                            if (stringValue != null) {
                                TsTestValue.TsException.StringException(stringValue)
                            } else {
                                TsTestValue.TsException.UnknownException
                            }
                        } else {
                            TsTestValue.TsException.UnknownException
                        }
                    }

                    else -> {
                        // Other types of exceptions - resolve and wrap the value
                        val resolvedValue = resolveExpr(res.value)
                        TsTestValue.TsException.ObjectException(resolvedValue)
                    }
                }
            } catch (_: Exception) {
                // Fallback to unknown exception if resolution fails
                TsTestValue.TsException.UnknownException
            }
        }
    }

    private class MemoryScope(
        ctx: TsContext,
        model: UModelBase<EtsType>,
        finalStateMemory: UReadOnlyMemory<EtsType>,
        method: EtsMethod,
        resolvedLValuesToFakeObjects: List<Pair<ULValue<*, *>, UConcreteHeapRef>>,
        maxStringLength: Int,
        propertyState: TsState,
    ) : TsTestStateResolver(
        ctx = ctx,
        model = model,
        finalStateMemory = finalStateMemory,
        method = method,
        resolvedLValuesToFakeObjects = resolvedLValuesToFakeObjects,
        maxStringLength = maxStringLength,
        propertyState = propertyState,
    ) {
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
    val maxStringLength: Int,
    private val propertyState: TsState? = null,
) {
    private val resolvedClasses = hashMapOf<UConcreteHeapRef, TsTestValue.TsClass>()

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
                resolvePrimitive(expr, EtsNumberType)
            }

            boolSort -> {
                resolvePrimitive(expr, EtsBooleanType)
            }

            addressSort -> {
                if (expr.isFakeObject()) {
                    resolveFakeObject(expr)
                } else {
                    val ref = expr.asExpr(addressSort)
                    resolveTsValue(ref)
                }
            }

            sizeSort -> {
                resolvePrimitive(expr, EtsNumberType)
            }

            else -> TODO("Unsupported sort: ${expr.sort}")
        }
    }

    private fun resolveTsValue(
        heapRef: UExpr<UAddressSort>,
    ): TsTestValue {
        val concreteRef = evaluateInModel(heapRef) as UConcreteHeapRef
        if (with(ctx) { concreteRef.isFakeObject() }) return resolveFakeObject(concreteRef)

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
                resolveTsArray(heapRef, type)
            }

            is EtsUnknownType -> {
                resolveTsValue(heapRef)
            }

            is EtsStringType -> {
                resolveString(heapRef, concreteRef)
            }

            else -> error("Unexpected type: $type")
        }
    }

    private fun resolveTsArray(
        heapRef: UHeapRef,
        type: EtsArrayType,
    ): TsTestValue.TsArray<*> = with(ctx) {
        val arrayLength = mkArrayLengthLValue(heapRef, type)
        val length = resolveLValue(arrayLength) as TsTestValue.TsNumber

        val values = (0 until length.number.toInt()).map { i ->
            val index = mkSizeExpr(i)
            val sort = typeToSort(type.elementType)

            if (sort is TsUnresolvedSort) {
                val value = readUnresolvedArrayElement(memory, heapRef, index)
                val currentRef = evaluateInModel(value.refValue)
                if (currentRef.isFakeObject()) {
                    return@map resolveFakeObject(currentRef)
                }

                return@map when {
                    model.eval(value.type.fpTypeExpr).isTrue -> resolveExpr(value.fpValue)
                    model.eval(value.type.boolTypeExpr).isTrue -> resolveExpr(value.boolValue)
                    model.eval(value.type.refTypeExpr).isTrue -> resolveExpr(value.refValue)
                    else -> TsTestValue.TsUndefined // An unread input element is unconstrained.
                }
            }

            require(sort is UFpSort || sort is UBoolSort || sort is UAddressSort) {
                "Other sorts must be resolved above, but got: $sort"
            }

            val lValue = mkArrayIndexLValue(sort, heapRef, index, type)
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

    private fun resolveString(
        heapRef: UHeapRef,
        concreteRef: UConcreteHeapRef,
    ): TsTestValue.TsString = with(ctx) {
        getStringConstantValue(concreteRef)?.let { return TsTestValue.TsString(it) }

        // Symbolic strings have no mutable backing field in the final state. Resolve
        // their backing array from the model in both before and after snapshots.
        val allocated = isAllocatedConcreteHeapRef(concreteRef)
        val stringMemory = if (allocated) finalStateMemory else model
        val stringRef = if (allocated) heapRef else concreteRef
        val backingLValue = mkStringBackingLValue(stringRef)
        val charsRef = evaluateInModel(stringMemory.read(backingLValue)) as UConcreteHeapRef
        if (charsRef.address == 0) {
            throw TsUnsupportedWitnessException("Symbolic string is missing backing array: $concreteRef")
        }

        val lengthLValue = mkStringBackingLengthLValue(charsRef)
        val length = evaluateInModel(stringMemory.read(lengthLValue)).extractInt()
        require(length in 0..maxStringLength) { "Unsupported symbolic string length: $length" }

        val value = buildString(length) {
            repeat(length) { index ->
                val elementLValue = mkStringBackingElementLValue(charsRef, mkSizeExpr(index))
                val element = evaluateInModel(stringMemory.read(elementLValue)) as KBitVec16Value
                append(element.shortValue.toInt().toChar())
            }
        }

        TsTestValue.TsString(value)
    }

    fun resolveThisInstance(): TsTestValue {
        val ref = mkRegisterStackLValue(ctx.addressSort, 0) // TODO check for statics
        return resolveLValue(ref)
    }

    fun resolveParameters(): List<TsTestValue> = with(ctx) {
        method.parameters.mapIndexed { i, param ->
            val idx = i + 1 // +1 because the register 0 is reserved for `this`
            val sort = typeToSort(param.type)

            if (sort is TsUnresolvedSort) {
                // this means that a fake object was created, and we need to read it from the current memory
                val ref = mkRegisterStackLValue(addressSort, idx)
                val address = finalStateMemory.read(ref)
                check(address.isFakeObject())
                return@mapIndexed resolveFakeObject(address)
            }

            val ref = mkRegisterStackLValue(sort, idx)
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
                resolveExpr(model.eval(value))
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
            EtsStringType -> error("String values must be resolved from heap references")
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
        resolvedClasses[concreteRef]?.let { return it }

        val type = if (concreteRef.isAllocated) {
            finalStateMemory.typeStreamOf(concreteRef).first()
        } else {
            model.typeStreamOf(concreteRef).first()
        }
        check(type is EtsRefType) { "Expected EtsRefType, but got $type" }
        val clazz = resolveClass(type)
        val properties = linkedMapOf<String, TsTestValue>()
        val result = TsTestValue.TsClass(clazz.name, properties)
        resolvedClasses[concreteRef] = result

        val tracked = propertyState?.trackedObjectProperties.orEmpty()
            .filter { evaluateInModel(it.instance) == concreteRef }
            .groupBy { it.name }
        val declaredFields = clazz.fields.filterNot { (it as EtsFieldImpl).modifiers.isStatic }
        declaredFields.forEach { field ->
            if (field.name in tracked) return@forEach
            if ((field as EtsFieldImpl).isOptional && !concreteRef.isAllocated) return@forEach
            if (resolveMode == ResolveMode.CURRENT &&
                model.eval(finalStateMemory.read(deletedFieldLValue(heapRef, field.name))).isTrue
            ) {
                return@forEach
            }

            properties[field.name] = resolveObjectField(concreteRef, heapRef, field.name, field.type)
        }
        tracked.forEach { (name, entries) ->
            val entry = entries.firstOrNull { it.declaredType != null } ?: entries.first()
            resolveTrackedProperty(concreteRef, entry)?.let { properties[name] = it }
        }
        if (resolveMode == ResolveMode.CURRENT) applyConcreteWrites(properties, concreteRef, heapRef)

        result
    }

    private fun resolveTrackedProperty(ref: UConcreteHeapRef, entry: TrackedObjectProperty): TsTestValue? = with(ctx) {
        val name = entry.name
        val propertyRef = entry.instance
        val initialPresence = model.eval(model.read(initialPropertyPresenceLValue(ref, name))).isTrue
        val written = resolveMode == ResolveMode.CURRENT &&
            model.eval(finalStateMemory.read(writtenPropertyLValue(propertyRef, name))).isTrue
        val deleted = resolveMode == ResolveMode.CURRENT &&
            model.eval(finalStateMemory.read(deletedFieldLValue(propertyRef, name))).isTrue
        if ((!initialPresence && !written) || deleted) return null

        val declaredType = entry.declaredType
        val sort = declaredType?.let { typeToSort(it) } ?: unresolvedSort
        if (!written && sort !is TsUnresolvedSort && !entry.optional) {
            return resolveObjectField(ref, ref, name, declaredType!!, initial = true)
        }

        val valueMemory = if (written) finalStateMemory else model
        val valueRef = if (written) propertyRef else ref
        val value = readInputPropertyValue(valueMemory, valueRef, name, written)
        when {
            model.eval(value.type.boolTypeExpr).isTrue -> resolveExpr(value.boolValue)
            model.eval(value.type.fpTypeExpr).isTrue -> resolveExpr(value.fpValue)
            model.eval(value.type.refTypeExpr).isTrue -> resolveExpr(value.refValue)
            else -> TsTestValue.TsUndefined // A property whose value was never read is unconstrained.
        }
    }

    private fun applyConcreteWrites(
        properties: MutableMap<String, TsTestValue>,
        ref: UConcreteHeapRef,
        heapRef: UHeapRef,
    ) = with(ctx) {
        propertyState?.writtenConcreteFields.orEmpty().filter { it.first == ref }.forEach { (_, name) ->
            if (model.eval(finalStateMemory.read(deletedFieldLValue(heapRef, name))).isTrue) {
                properties.remove(name)
            } else if (name !in properties) {
                val sort = propertyState?.writtenObjectLiteralFieldSorts?.get(ref to name) ?: addressSort
                properties[name] = resolveLValue(mkFieldLValue(sort, heapRef, name))
            }
        }
    }

    private fun resolveObjectField(
        ref: UConcreteHeapRef,
        heapRef: UHeapRef,
        name: String,
        type: EtsType,
        initial: Boolean = false,
    ): TsTestValue = with(ctx) {
        val sort = typeToSort(type)
        val valueMemory = if (initial) model else memory
        val valueRef = if (initial || resolveMode == ResolveMode.MODEL) ref else heapRef
        if (sort !is TsUnresolvedSort) {
            return resolveExpr(valueMemory.read(mkFieldLValue(sort, valueRef, name)))
        }

        val lValue = mkFieldLValue(addressSort, ref, name)
        val fakeObject = if (resolveMode == ResolveMode.MODEL) {
            resolvedLValuesToFakeObjects.firstOrNull { it.first == lValue }?.second
        } else {
            resolvedLValuesToFakeObjects.lastOrNull { it.first == lValue }?.second
        }
        if (fakeObject != null) return resolveFakeObject(fakeObject)

        // Unread fields can be concretized to undefined. Evaluating also recognizes symbolic wrapper branches.
        resolveExpr(valueMemory.read(mkFieldLValue(addressSort, valueRef, name)))
    }

    internal var resolveMode: ResolveMode = ResolveMode.ERROR

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

internal inline fun <S : TsTestStateResolver, R> S.withMode(
    resolveMode: ResolveMode,
    body: S.() -> R,
): R {
    val prevValue = this.resolveMode
    try {
        this.resolveMode = resolveMode
        return body()
    } finally {
        this.resolveMode = prevValue
    }
}
