package org.usvm.api.util

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.constructors
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.enumValues
import org.jacodb.api.jvm.ext.findTypeOrNull
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.api.jvm.ext.isEnum
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.short
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.void
import org.jacodb.approximation.JcEnrichedVirtualField
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.SymbolicIdentityMap
import org.usvm.api.SymbolicList
import org.usvm.api.SymbolicMap
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.decoder.ObjectData
import org.usvm.api.decoder.ObjectDecoder
import org.usvm.api.internal.SymbolicIdentityMapImpl
import org.usvm.api.internal.SymbolicListImpl
import org.usvm.api.internal.SymbolicMapImpl
import org.usvm.api.refSetContainsElement
import org.usvm.api.typeStreamOf
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.ref.URefSetEntries
import org.usvm.collection.set.ref.refSetEntries
import org.usvm.isStaticHeapRef
import org.usvm.isTrue
import org.usvm.logger
import org.usvm.machine.JcContext
import org.usvm.machine.extractBool
import org.usvm.machine.extractByte
import org.usvm.machine.extractChar
import org.usvm.machine.extractDouble
import org.usvm.machine.extractFloat
import org.usvm.machine.extractInt
import org.usvm.machine.extractLong
import org.usvm.machine.extractShort
import org.usvm.machine.interpreter.JcFixedInheritorsNumberTypeSelector
import org.usvm.machine.interpreter.JcTypeStreamPrioritization
import org.usvm.machine.interpreter.statics.JcStaticFieldLValue
import org.usvm.machine.interpreter.statics.extractInitialStatics
import org.usvm.machine.state.localIdx
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.types.first

abstract class JcTestStateResolver<T>(
    val ctx: JcContext,
    private val model: UModelBase<JcType>,
    private val finalStateMemory: UReadOnlyMemory<JcType>,
    val method: JcTypedMethod,
) {
    abstract val decoderApi: DecoderApi<T>

    private var resolveMode: ResolveMode = ResolveMode.ERROR

    fun <R> withMode(resolveMode: ResolveMode, body: JcTestStateResolver<T>.() -> R): R {
        val prevValue = this.resolveMode
        try {
            this.resolveMode = resolveMode
            return this.body()
        } finally {
            this.resolveMode = prevValue
        }
    }

    enum class ResolveMode {
        MODEL, CURRENT, ERROR
    }

    val memory: UReadOnlyMemory<JcType>
        get() = when (resolveMode) {
            ResolveMode.MODEL -> model
            ResolveMode.CURRENT -> finalStateMemory
            ResolveMode.ERROR -> error("You must explicitly specify type of the required memory")
        }

    val resolvedCache = mutableMapOf<UConcreteHeapAddress, T>()
    val decoders = JcTestDecoders(ctx.cp)
    val typeSelector = JcTypeStreamPrioritization(
        typesToScore = JcFixedInheritorsNumberTypeSelector.DEFAULT_INHERITORS_NUMBER_TO_SCORE
    )

    fun resolveThisInstance(): T = if (method.isStatic) {
        decoderApi.createNullConst(method.enclosingType)
    } else {
        val ref = URegisterStackLValue(ctx.addressSort, idx = 0)
        resolveLValue(ref, method.enclosingType)
    }

    fun resolveParameters(): List<T> = method.parameters.mapIndexed { idx, param ->
        val registerIdx = method.method.localIdx(idx)
        val ref = URegisterStackLValue(ctx.typeToSort(param.type), registerIdx)
        resolveLValue(ref, param.type)
    }

    fun resolveStatics(): Unit = extractInitialStatics(ctx, finalStateMemory)
        .forEach { field ->
            val fieldType = ctx.cp.findTypeOrNull(field.type.typeName)
                ?: error("No such type ${field.type} found")
            val sort = ctx.typeToSort(fieldType)
            val resolvedValue = resolveLValue(JcStaticFieldLValue(field, sort), fieldType)

            decoderApi.setField(field, decoderApi.createNullConst(field.enclosingClass.toType()), resolvedValue)
        }

    fun resolveLValue(lvalue: ULValue<*, *>, type: JcType): T {
        val expr = memory.read(lvalue)

        return resolveExpr(expr, type)
    }

    fun resolveExpr(expr: UExpr<out USort>, type: JcType): T =
        when (type) {
            is JcPrimitiveType -> resolvePrimitive(expr, type)
            is JcRefType -> resolveReference(expr.asExpr(ctx.addressSort), type)
            else -> error("Unexpected type: $type")
        }

    fun resolvePrimitive(expr: UExpr<out USort>, type: JcPrimitiveType): T = when (type) {
        ctx.cp.boolean -> decoderApi.createBoolConst(resolvePrimitiveBool(expr))
        ctx.cp.byte -> decoderApi.createByteConst(resolvePrimitiveByte(expr))
        ctx.cp.short -> decoderApi.createShortConst(resolvePrimitiveShort(expr))
        ctx.cp.int -> decoderApi.createIntConst(resolvePrimitiveInt(expr))
        ctx.cp.long -> decoderApi.createLongConst(resolvePrimitiveLong(expr))
        ctx.cp.float -> decoderApi.createFloatConst(resolvePrimitiveFloat(expr))
        ctx.cp.double -> decoderApi.createDoubleConst(resolvePrimitiveDouble(expr))
        ctx.cp.char -> decoderApi.createCharConst(resolvePrimitiveChar(expr))
        ctx.cp.void -> decoderApi.createNullConst(ctx.cp.objectType)
        else -> error("Unexpected type: ${type.typeName}")
    }

    fun resolvePrimitiveBool(expr: UExpr<out USort>): Boolean =
        extractBool(evaluateInModel(expr)) ?: false

    fun resolvePrimitiveByte(expr: UExpr<out USort>): Byte =
        extractByte(evaluateInModel(expr)) ?: 0

    fun resolvePrimitiveShort(expr: UExpr<out USort>): Short =
        extractShort(evaluateInModel(expr)) ?: 0

    fun resolvePrimitiveInt(expr: UExpr<out USort>): Int =
        extractInt(evaluateInModel(expr)) ?: 0

    fun resolvePrimitiveLong(expr: UExpr<out USort>): Long =
        extractLong(evaluateInModel(expr)) ?: 0

    fun resolvePrimitiveFloat(expr: UExpr<out USort>): Float =
        extractFloat(evaluateInModel(expr)) ?: 0f

    fun resolvePrimitiveDouble(expr: UExpr<out USort>): Double =
        extractDouble(evaluateInModel(expr)) ?: 0.0

    fun resolvePrimitiveChar(expr: UExpr<out USort>): Char =
        extractChar(evaluateInModel(expr)) ?: '\u0000'

    fun resolveReference(heapRef: UHeapRef, type: JcRefType): T {
        val ref = evaluateInModel(heapRef) as UConcreteHeapRef
        if (ref.address == NULL_ADDRESS) {
            return decoderApi.createNullConst(type)
        }
        // to find a type, we need to understand the source of the object
        val typeStream = if (ref.address <= INITIAL_INPUT_ADDRESS) {
            // input object
            model.typeStreamOf(ref)
        } else {
            // allocated object
            memory.typeStreamOf(ref)
        }.filterBySupertype(type)

        // We filter allocated object type stream, because it could be stored in the input array,
        // which resolved to a wrong type, since we do not build connections between element types
        // and array types right now.
        // In such cases, we need to resolve this element to null.

        val evaluatedType = typeSelector.firstOrNull(typeStream, type.jcClass)
            ?: return decoderApi.createNullConst(type)

        // We check for the type stream emptiness firsly and only then for the resolved cache,
        // because even if the object is already resolved, it could be incompatible with the [type], if it
        // is an element of an array of the wrong type.

        return resolveRef(ref.address) {
            when (evaluatedType) {
                is JcArrayType -> resolveArray(ref, heapRef, evaluatedType)
                is JcClassType -> resolveObject(ref, heapRef, evaluatedType)
                else -> error("Unexpected type: $type")
            }
        }
    }

    fun resolveArray(
        ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcArrayType
    ): T {
        val arrayDescriptor = ctx.arrayDescriptorOf(type)
        val lengthRef = UArrayLengthLValue(heapRef, arrayDescriptor, ctx.sizeSort)
        val resolvedLength = resolvePrimitiveInt(memory.read(lengthRef))

        val length = clipArrayLength(resolvedLength)

        val cellSort = ctx.typeToSort(type.elementType)

        val arrayInstance = decoderApi.createArray(type.elementType, decoderApi.createIntConst(length))
        saveResolvedRef(ref.address, arrayInstance)

        for (idx in 0 until length) {
            val elemRef = UArrayIndexLValue(cellSort, heapRef, ctx.mkSizeExpr(idx), arrayDescriptor)
            val element = resolveLValue(elemRef, type.elementType)
            decoderApi.setArrayIndex(arrayInstance, decoderApi.createIntConst(idx), element)
        }

        return arrayInstance
    }

    abstract fun allocateClassInstance(type: JcClassType): T

    fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): T {
        val decoder = decoders.findDecoder(type.jcClass)
        if (decoder != null) {
            return decodeObject(ref, type, decoder)
        }

        if (type.jcClass == ctx.classType.jcClass && ref.address <= INITIAL_STATIC_ADDRESS) {
            // Note that non-negative addresses are possible only for the result value.
            return resolveAllocatedClass(ref)
        }

        if (type.jcClass == ctx.stringType.jcClass && ref.address <= INITIAL_STATIC_ADDRESS) {
            // Note that non-negative addresses are possible only for the result value.
            return resolveAllocatedString(ref)
        }

        val anyEnumAncestor = type.getEnumAncestorOrNull()
        if (anyEnumAncestor != null) {
            return resolveEnumValue(heapRef, anyEnumAncestor)
        }

        return allocateAndInitializeObject(ref, heapRef, type)
    }

    fun allocateAndInitializeObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): T {
        val instance = allocateClassInstance(type)
        saveResolvedRef(ref.address, instance)

        // TODO skips throwable construction for now
        val throwable = ctx.cp.findTypeOrNull<Throwable>()
        if (throwable != null && type.isAssignable(throwable)) {
            return instance
        }

        for (cls in generateSequence(type.jcClass) { it.superClass }.map { it.toType() }) {
            // If superclass have a decoder, apply decoder and copy fields values
            val decoder = decoders.findDecoder(cls.jcClass)
            if (decoder != null) {
                val decodedCls = decodeObject(ref, cls, decoder)

                // Decoder can overwrite cached instance. Restore correct instance
                saveResolvedRef(ref.address, instance)

                generateSequence(cls) { it.superType }
                    .flatMap { it.declaredFields }
                    .filterNot { it.isStatic }
                    // Don't copy approximation-specific fields
                    .filterNot { it.field is JcEnrichedVirtualField }
                    .forEach { field ->
                        val fieldValue = decoderApi.getField(field.field, decodedCls)
                        decoderApi.setField(field.field, instance, fieldValue)
                    }

                // No need to process other superclasses since we already decode them
                break
            } else {
                val fields = cls.declaredFields.filter { !it.isStatic }

                for (field in fields) {
                    check(field.field !is JcEnrichedVirtualField) {
                        "Class ${cls.jcClass.name} has approximated field ${field.field} but has no decoder"
                    }

                    val lvalue = UFieldLValue(ctx.typeToSort(field.type), heapRef, field.field)
                    val fieldValue = resolveLValue(lvalue, field.type)
                    decoderApi.setField(field.field, instance, fieldValue)
                }
            }
        }

        return instance
    }

    fun resolveEnumValue(heapRef: UHeapRef, enumAncestor: JcClassOrInterface): T {
        val ordinalLValue = UFieldLValue(ctx.sizeSort, heapRef, ctx.enumOrdinalField)
        val ordinalFieldValue = resolvePrimitiveInt(memory.read(ordinalLValue))

        val enumField = enumAncestor.enumValues?.get(ordinalFieldValue)
            ?: error("Cant find enum field with index $ordinalFieldValue")

        return decoderApi.getField(enumField, decoderApi.createNullConst(ctx.cp.objectType))
    }

    fun resolveAllocatedClass(ref: UConcreteHeapRef): T {
        val classTypeField = ctx.classTypeSyntheticField
        val classTypeLValue = UFieldLValue(ctx.addressSort, ref, classTypeField)

        val memoryToResolveClassType = if (isStaticHeapRef(ref)) finalStateMemory else model

        val classTypeRef = memoryToResolveClassType.read(classTypeLValue) as? UConcreteHeapRef
            ?: error("No type for allocated class")

        val classType = memoryToResolveClassType.typeStreamOf(classTypeRef).first()

        return decoderApi.createClassConst(classType)
    }

    abstract fun allocateString(value: T): T

    fun resolveAllocatedString(ref: UConcreteHeapRef): T {
        val valueField = ctx.stringValueField
        val strValueLValue = UFieldLValue(ctx.typeToSort(valueField.type), ref, valueField.field)

        val strValue = if (isStaticHeapRef(ref)) {
            withMode(ResolveMode.CURRENT) {
                val expr = memory.read(strValueLValue)
                resolveExpr(expr, valueField.type)
            }
        } else {
            resolveLValue(strValueLValue, valueField.type)
        }

        return allocateString(strValue)
    }

    fun decodeObject(ref: UConcreteHeapRef, type: JcClassType, objectDecoder: ObjectDecoder): T {
        val refDecoder = TestObjectData(ref)

        val decodedObject = objectDecoder.createInstance(type.jcClass, refDecoder, decoderApi)
        requireNotNull(decodedObject) { "Object not properly decoded" }
        saveResolvedRef(ref.address, decodedObject)

        objectDecoder.initializeInstance(type.jcClass, refDecoder, decodedObject, decoderApi)
        return decodedObject
    }

    fun resolveSymbolicList(heapRef: UHeapRef): SymbolicList<T>? {
        val listType = ctx.cp.findTypeOrNull<SymbolicList<*>>() ?: return null

        val lengthRef = UArrayLengthLValue(heapRef, listType, ctx.sizeSort)
        val resolvedLength = resolvePrimitiveInt(memory.read(lengthRef))
        val length = clipArrayLength(resolvedLength)

        val result = SymbolicListImpl<T>()
        for (i in 0 until length) {
            val elemRef = UArrayIndexLValue(ctx.addressSort, heapRef, ctx.mkSizeExpr(i), listType)
            val element = resolveLValue(elemRef, ctx.cp.objectType)
            result.insert(i, element)
        }

        return result
    }

    fun resolveSymbolicMap(heapRef: UHeapRef): SymbolicMap<T, T>? {
        val mapType = ctx.cp.findTypeOrNull<SymbolicMap<*, *>>() ?: return null

        val resultMap = SymbolicMapImpl<T, T>()

        // todo: equals based check
        resolveSymbolicIdentityMapEntries(heapRef, mapType, { resultMap.size() }, { k, v -> resultMap.set(k, v) })

        return resultMap
    }

    fun resolveSymbolicIdentityMap(heapRef: UHeapRef): SymbolicIdentityMap<T, T>? {
        val mapType = ctx.cp.findTypeOrNull<SymbolicIdentityMap<*, *>>() ?: return null
        val resultMap = SymbolicIdentityMapImpl<T, T>()
        resolveSymbolicIdentityMapEntries(heapRef, mapType, { resultMap.size() }, { k, v -> resultMap.set(k, v) })
        return resultMap
    }

    private inline fun resolveSymbolicIdentityMapEntries(
        heapRef: UHeapRef,
        mapType: JcType,
        resultMapSize: () -> Int,
        resultMapAddEntry: (T, T) -> Unit
    ) {
        val lengthRef = UMapLengthLValue(heapRef, mapType, ctx.sizeSort)
        val resolvedLength = resolvePrimitiveInt(memory.read(lengthRef))
        val length = clipArrayLength(resolvedLength)

        val keysInModel = hashSetOf<UHeapRef>()

        val memoryEntries = memory.refSetEntries(heapRef, mapType)
        resolveMapEntries(memory, memoryEntries, heapRef, mapType,
            keyInModel = { !keysInModel.add(it) },
            addModelEntry = { k, v -> resultMapAddEntry(k, v) }
        )

        val modelMapRef = evaluateInModel(heapRef)
        val modelEntries = model.refSetEntries(modelMapRef, mapType)
        resolveMapEntries(model, modelEntries, modelMapRef, mapType,
            keyInModel = { !keysInModel.add(it) },
            addModelEntry = { k, v -> resultMapAddEntry(k, v) }
        )

        // todo: map length refinement loop in solver
        val mapSize = resultMapSize()
        if (length < mapSize) {
            logger.warn { "Incorrect model: map length $length lower than resolved map size $mapSize" }
        }

        if (length > mapSize) {
            logger.warn { "Incorrect model: map length $length greater than resolved map size $mapSize" }

            // fill map with new objects which are definitely unique
            // note: may not satisfy map type constraints
            val objectCtor = ctx.cp.objectType.constructors.single { it.parameters.isEmpty() }
            while (length > resultMapSize()) {
                val freshKey = decoderApi.invokeMethod(objectCtor.method, emptyList())
                resultMapAddEntry(freshKey, freshKey)
            }
        }
    }

    private inline fun resolveMapEntries(
        memory: UReadOnlyMemory<JcType>,
        keySetEntries: URefSetEntries<JcType>,
        mapRef: UHeapRef,
        mapType: JcType,
        keyInModel: (UHeapRef) -> Boolean,
        addModelEntry: (T, T) -> Unit
    ) {
        for (entry in keySetEntries.entries) {
            val key = entry.setElement
            val keyContains = memory.refSetContainsElement(mapRef, key, mapType)
            if (evaluateInModel(keyContains).isTrue) {
                val keyRefModel = evaluateInModel(key)
                if (keyInModel(keyRefModel)) {
                    continue
                }

                val keyModel = resolveReference(key, ctx.cp.objectType)

                val lvalue = URefMapEntryLValue(ctx.addressSort, mapRef, key, mapType)
                val valueModel = resolveLValue(lvalue, ctx.cp.objectType)

                addModelEntry(keyModel, valueModel)
            }
        }
    }

    inner class TestObjectData(private val instanceRef: UHeapRef) : ObjectData<T> {
        override fun decodeField(field: JcField): T {
            val fieldType = ctx.cp.findTypeOrNull(field.type) ?: error("No type for field: $field")
            val lvalue = UFieldLValue(ctx.typeToSort(fieldType), instanceRef, field)
            return resolveLValue(lvalue, fieldType)
        }

        private inline fun <R> readRefField(field: JcField, body: (UHeapRef) -> R): R? {
            val lvalue = UFieldLValue(ctx.addressSort, instanceRef, field)
            val ref = memory.read(lvalue)

            val refValue = evaluateInModel(ref) as UConcreteHeapRef
            if (refValue.address == NULL_ADDRESS) {
                return null
            }

            return body(ref)
        }

        override fun decodeSymbolicListField(field: JcField): SymbolicList<T>? =
            readRefField(field) { ref -> resolveSymbolicList(ref) }

        override fun decodeSymbolicMapField(field: JcField): SymbolicMap<T, T>? =
            readRefField(field) { ref -> resolveSymbolicMap(ref) }

        override fun decodeSymbolicIdentityMapField(field: JcField): SymbolicIdentityMap<T, T>? =
            readRefField(field) { ref -> resolveSymbolicIdentityMap(ref) }

        override fun getObjectField(field: JcField): ObjectData<T>? =
            readRefField(field) { ref -> TestObjectData(ref) }

        private inline fun <reified T> readPrimitiveField(field: JcField, sort: USort, resolver: (UExpr<*>) -> T): T {
            val lvalue = UFieldLValue(sort, instanceRef, field)
            return resolver(memory.read(lvalue))
        }

        override fun getBooleanField(field: JcField): Boolean =
            readPrimitiveField(field, ctx.booleanSort, ::resolvePrimitiveBool)

        override fun getByteField(field: JcField): Byte =
            readPrimitiveField(field, ctx.byteSort, ::resolvePrimitiveByte)

        override fun getShortField(field: JcField): Short =
            readPrimitiveField(field, ctx.shortSort, ::resolvePrimitiveShort)

        override fun getIntField(field: JcField): Int =
            readPrimitiveField(field, ctx.integerSort, ::resolvePrimitiveInt)

        override fun getLongField(field: JcField): Long =
            readPrimitiveField(field, ctx.longSort, ::resolvePrimitiveLong)

        override fun getFloatField(field: JcField): Float =
            readPrimitiveField(field, ctx.floatSort, ::resolvePrimitiveFloat)

        override fun getDoubleField(field: JcField): Double =
            readPrimitiveField(field, ctx.doubleSort, ::resolvePrimitiveDouble)

        override fun getCharField(field: JcField): Char =
            readPrimitiveField(field, ctx.charSort, ::resolvePrimitiveChar)

        override fun getArrayFieldLength(field: JcField): Int {
            val fieldType = ctx.cp.findTypeOrNull(field.type) ?: error("No type for field: $field")
            val arrayType = fieldType as? JcArrayType ?: error("Unexpected array field type: $fieldType")

            val arrayDescriptor = ctx.arrayDescriptorOf(arrayType)
            val lengthRef = UArrayLengthLValue(instanceRef, arrayDescriptor, ctx.sizeSort)
            return resolvePrimitiveInt(memory.read(lengthRef))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as JcTestStateResolver<*>.TestObjectData

            return instanceRef == other.instanceRef
        }

        override fun hashCode(): Int = instanceRef.hashCode()

        override fun toString(): String = "(Object $instanceRef)"
    }

    /**
     * If we resolve state after, [expr] is read from a state memory, so it requires concretization via [model].
     *
     * @return a concretized expression.
     */
    fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
        return model.eval(expr)
    }

    // TODO simple org.jacodb.api.jvm.ext.JcClasses.isEnum does not work with enums with abstract methods
    private fun JcRefType.getEnumAncestorOrNull(): JcClassOrInterface? =
        (sequenceOf(jcClass) + jcClass.allSuperHierarchySequence).firstOrNull { it.isEnum }

    inline fun resolveRef(
        ref: UConcreteHeapAddress,
        resolve: () -> T
    ): T {
        val result = resolvedCache[ref]
        if (result != null) {
            if (result === CYCLIC_REF_STUB) {
                error("Cyclic reference occurred when resolving: $ref")
            }

            return result
        }

        try {
            @Suppress("UNCHECKED_CAST")
            resolvedCache[ref] = CYCLIC_REF_STUB as T

            return resolve()
        } finally {
            val current = resolvedCache[ref]
            // Ref resolution process finished without producing a result
            if (current === CYCLIC_REF_STUB) {
                resolvedCache.remove(ref)
            }
        }
    }

    fun saveResolvedRef(ref: UConcreteHeapAddress, resolved: T) {
        resolvedCache[ref] = resolved
    }

    companion object {
        fun clipArrayLength(length: Int): Int =
            when {
                length in 0..MAX_ARRAY_LENGTH -> length

                length > MAX_ARRAY_LENGTH -> {
                    logger.warn { "Array length exceeds $MAX_ARRAY_LENGTH: $length" }
                    MAX_ARRAY_LENGTH
                }

                else -> {
                    logger.warn { "Negative array length: $length" }
                    0
                }
            }

        private const val MAX_ARRAY_LENGTH = 10_000

        @PublishedApi
        internal val CYCLIC_REF_STUB = Any()
    }
}
