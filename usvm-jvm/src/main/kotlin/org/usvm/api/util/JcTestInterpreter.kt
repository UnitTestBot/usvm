package org.usvm.api.util

import io.ksmt.utils.asExpr
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.allSuperHierarchySequence
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.isEnum
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.INITIAL_STATIC_ADDRESS
import org.usvm.NULL_ADDRESS
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.JcCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.SymbolicIdentityMap
import org.usvm.api.SymbolicList
import org.usvm.api.SymbolicMap
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.decoder.ObjectData
import org.usvm.api.decoder.ObjectDecoder
import org.usvm.api.internal.SymbolicListImpl
import org.usvm.api.typeStreamOf
import org.usvm.api.util.JcClassLoader.loadClass
import org.usvm.api.util.JcTestResolver.Companion.resolveRef
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.api.util.Reflection.getFieldValue
import org.usvm.api.util.Reflection.invoke
import org.usvm.api.util.Reflection.setFieldValue
import org.usvm.api.util.Reflection.toJavaClass
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
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
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.localIdx
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.types.first

/**
 * A class, responsible for resolving a single [JcTest] for a specific method from a symbolic state.
 *
 * Uses reflection to resolve objects.
 *
 * @param classLoader a class loader to load target classes.
 */
class JcTestInterpreter(
    private val classLoader: ClassLoader = JcClassLoader,
) : JcTestResolver {
    /**
     * Resolves a [JcTest] from a [method] from a [state].
     */
    override fun resolve(method: JcTypedMethod, state: JcState): JcTest {
        val model = state.models.first()
        val memory = state.memory

        val ctx = state.ctx

        val initialScope = MemoryScope(ctx, model, model, method, classLoader)
        val afterScope = MemoryScope(ctx, model, memory, method, classLoader)

        val before = with(initialScope) { resolveState() }
        val after = with(afterScope) { resolveState() }

        val result = when (val res = state.methodResult) {
            is JcMethodResult.NoCall -> error("No result found")
            is JcMethodResult.Success -> with(afterScope) { Result.success(resolveExpr(res.value, method.returnType)) }
            is JcMethodResult.JcException -> Result.failure(resolveException(res, afterScope))
        }
        val coverage = resolveCoverage(method, state)

        return JcTest(
            method,
            before,
            after,
            result,
            coverage
        )
    }

    private fun resolveException(
        exception: JcMethodResult.JcException,
        afterMemory: MemoryScope
    ): Throwable = with(afterMemory) {
        resolveExpr(exception.address, exception.type) as Throwable
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resolveCoverage(method: JcTypedMethod, state: JcState): JcCoverage {
        // TODO: extract coverage
        return JcCoverage(emptyMap())
    }

    /**
     * An actual class for resolving objects from [UExpr]s.
     *
     * @param model a model to which compose expressions.
     * @param memory a read-only memory to read [ULValue]s from.
     * @param classLoader a class loader to load target classes.
     */
    private class MemoryScope(
        private val ctx: JcContext,
        private val model: UModelBase<JcType>,
        private val memory: UReadOnlyMemory<JcType>,
        private val method: JcTypedMethod,
        private val classLoader: ClassLoader = JcClassLoader,
    ) {
        private val resolvedCache = mutableMapOf<UConcreteHeapAddress, Any?>()
        private val decoders = JcTestDecoders(ctx.cp)
        private val decoderApi = TestInterpreterDecoderApi()
        private val typeSelector = JcTypeStreamPrioritization(
            typesToScore = JcFixedInheritorsNumberTypeSelector.DEFAULT_INHERITORS_NUMBER_TO_SCORE
        )

        fun resolveState(): JcParametersState {
            // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
            val thisInstance = if (!method.isStatic) {
                val ref = URegisterStackLValue(ctx.addressSort, idx = 0)
                resolveLValue(ref, method.enclosingType)
            } else {
                null
            }

            val parameters = method.parameters.mapIndexed { idx, param ->
                val registerIdx = method.method.localIdx(idx)
                val ref = URegisterStackLValue(ctx.typeToSort(param.type), registerIdx)
                resolveLValue(ref, param.type)
            }

            return JcParametersState(thisInstance, parameters)
        }

        fun resolveLValue(lvalue: ULValue<*, *>, type: JcType): Any? {
            val expr = memory.read(lvalue)

            return resolveExpr(expr, type)
        }

        fun resolveExpr(expr: UExpr<out USort>, type: JcType): Any? =
            when (type) {
                is JcPrimitiveType -> resolvePrimitive(expr, type)
                is JcRefType -> resolveReference(expr.asExpr(ctx.addressSort), type)
                else -> error("Unexpected type: $type")
            }

        fun resolvePrimitive(expr: UExpr<out USort>, type: JcPrimitiveType): Any {
            val exprInModel = evaluateInModel(expr)
            return when (type) {
                ctx.cp.boolean -> extractBool(exprInModel)
                ctx.cp.short -> extractShort(exprInModel)
                ctx.cp.int -> extractInt(exprInModel)
                ctx.cp.long -> extractLong(exprInModel)
                ctx.cp.float -> extractFloat(exprInModel)
                ctx.cp.double -> extractDouble(exprInModel)
                ctx.cp.byte -> extractByte(exprInModel)
                ctx.cp.char -> extractChar(exprInModel)
                ctx.cp.void -> Unit
                else -> error("Unexpected type: ${type.typeName}")
            } ?: error("Can't extract $expr to ${type.typeName}")
        }

        fun resolveReference(heapRef: UHeapRef, type: JcRefType): Any? {
            val ref = evaluateInModel(heapRef) as UConcreteHeapRef
            if (ref.address == NULL_ADDRESS) {
                return null
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

            val evaluatedType = typeSelector.firstOrNull(typeStream, type.jcClass) ?: return null

            // We check for the type stream emptiness firsly and only then for the resolved cache,
            // because even if the object is already resolved, it could be incompatible with the [type], if it
            // is an element of an array of the wrong type.

            return resolvedCache.resolveRef(ref.address) {
                when (evaluatedType) {
                    is JcArrayType -> resolveArray(ref, heapRef, evaluatedType)
                    is JcClassType -> resolveObject(ref, heapRef, evaluatedType)
                    else -> error("Unexpected type: $type")
                }
            }
        }

        private fun resolveArray(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcArrayType): Any {
            val arrayDescriptor = ctx.arrayDescriptorOf(type)
            val lengthRef = UArrayLengthLValue(heapRef, arrayDescriptor, ctx.sizeSort)
            val resolvedLength = resolveLValue(lengthRef, ctx.cp.int) as Int
            val length = clipArrayLength(resolvedLength)

            val cellSort = ctx.typeToSort(type.elementType)

            val instance = type.allocateInstance(classLoader, length)
            for (i in 0 until length) {
                val elemRef = UArrayIndexLValue(cellSort, heapRef, ctx.mkSizeExpr(i), arrayDescriptor)
                val element = resolveLValue(elemRef, type.elementType)
                Reflection.setArrayIndex(instance, i, element)
            }

            if (type.elementType is JcPrimitiveType) {
                resolvedCache[ref.address] = instance
            }
            return instance
        }

        private fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): Any {
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

            val instance = type.allocateInstance(classLoader)
            resolvedCache[ref.address] = instance

            // TODO skips throwable construction for now
            if (instance is Throwable) {
                return instance
            }

            val fields = generateSequence(type.jcClass) { it.superClass }
                .map { it.toType() }
                .flatMap { it.declaredFields }
                .filter { !it.isStatic }
            for (field in fields) {
                val lvalue = UFieldLValue(ctx.typeToSort(field.fieldType), heapRef, field.field)
                val fieldValue = resolveLValue(lvalue, field.fieldType)
                field.field.setFieldValue(classLoader, instance, fieldValue)
            }
            return instance
        }

        private fun resolveEnumValue(heapRef: UHeapRef, enumAncestor: JcClassOrInterface): Any {
            with(ctx) {
                val ordinalLValue = UFieldLValue(sizeSort, heapRef, enumOrdinalField)
                val ordinalFieldValue = resolveLValue(ordinalLValue, cp.int)
                val enumClass = enumAncestor.toType().toJavaClass(classLoader)

                return enumClass.enumConstants[ordinalFieldValue as Int]
            }
        }

        private fun resolveAllocatedClass(ref: UConcreteHeapRef): Class<*> {
            val classTypeField = ctx.classTypeSyntheticField
            val classTypeLValue = UFieldLValue(ctx.addressSort, ref, classTypeField)

            val classTypeRef = memory.read(classTypeLValue) as? UConcreteHeapRef
                ?: error("No type for allocated class")

            val classType = memory.typeStreamOf(classTypeRef).first()

            return classType.toJavaClass(classLoader)
        }

        private fun resolveAllocatedString(ref: UConcreteHeapRef): String {
            val valueField = ctx.stringValueField
            val strValueLValue = UFieldLValue(ctx.typeToSort(valueField.fieldType), ref, valueField.field)
            val strValue = resolveLValue(strValueLValue, valueField.fieldType)

            return when (strValue) {
                is CharArray -> String(strValue)
                is ByteArray -> String(strValue)
                else -> String()
            }
        }

        private fun decodeObject(ref: UConcreteHeapRef, type: JcClassType, objectDecoder: ObjectDecoder): Any {
            val refDecoder = TestObjectData(ref)

            val decodedObject = objectDecoder.createInstance(type.jcClass, refDecoder, decoderApi)
            requireNotNull(decodedObject) { "Object not properly decoded" }
            resolvedCache[ref.address] = decodedObject

            objectDecoder.initializeInstance(type.jcClass, refDecoder, decodedObject, decoderApi)
            return decodedObject
        }

        private fun resolveSymbolicList(heapRef: UHeapRef): SymbolicList<Any?>? {
            val ref = evaluateInModel(heapRef) as UConcreteHeapRef
            if (ref.address == NULL_ADDRESS) {
                return null
            }

            val listType = ctx.cp.findTypeOrNull<SymbolicList<*>>() ?: return null

            val lengthRef = UArrayLengthLValue(heapRef, listType, ctx.sizeSort)
            val resolvedLength = resolveLValue(lengthRef, ctx.cp.int) as Int
            val length = clipArrayLength(resolvedLength)

            val result = SymbolicListImpl<Any?>()
            for (i in 0 until length) {
                val elemRef = UArrayIndexLValue(ctx.addressSort, heapRef, ctx.mkSizeExpr(i), listType)
                val element = resolveLValue(elemRef, ctx.cp.objectType)
                result.insert(i, element)
            }

            return result
        }

        private inner class TestObjectData(private val instanceRef: UHeapRef) : ObjectData<Any?> {
            override fun decodeField(field: JcField): Any? {
                val lvalue = UFieldLValue(ctx.typeToSort(field.typed().fieldType), instanceRef, field)
                return resolveLValue(lvalue, field.typed().fieldType)
            }

            override fun decodeSymbolicListField(field: JcField): SymbolicList<Any?>? {
                val lvalue = UFieldLValue(ctx.addressSort, instanceRef, field)
                val listRef = memory.read(lvalue)
                return resolveSymbolicList(listRef)
            }

            override fun decodeSymbolicMapField(field: JcField): SymbolicMap<Any?, Any?>? {
                TODO("Not yet implemented")
            }

            override fun decodeSymbolicIdentityMapField(field: JcField): SymbolicIdentityMap<Any?, Any?>? {
                TODO("Not yet implemented")
            }

            override fun getObjectField(field: JcField): ObjectData<Any?>? {
                val lvalue = UFieldLValue(ctx.addressSort, instanceRef, field)
                val objectRef = memory.read(lvalue)

                val ref = evaluateInModel(objectRef) as UConcreteHeapRef
                if (ref.address == NULL_ADDRESS) {
                    return null
                }

                return TestObjectData(objectRef)
            }

            private inline fun <reified T> readPrimitiveField(field: JcField, sort: USort, type: JcPrimitiveType): T {
                val lvalue = UFieldLValue(sort, instanceRef, field)
                return resolveLValue(lvalue, type) as T
            }

            override fun getBooleanField(field: JcField): Boolean =
                readPrimitiveField(field, ctx.booleanSort, ctx.cp.boolean)

            override fun getByteField(field: JcField): Byte =
                readPrimitiveField(field, ctx.byteSort, ctx.cp.byte)

            override fun getShortField(field: JcField): Short =
                readPrimitiveField(field, ctx.shortSort, ctx.cp.short)

            override fun getIntField(field: JcField): Int =
                readPrimitiveField(field, ctx.integerSort, ctx.cp.int)

            override fun getLongField(field: JcField): Long =
                readPrimitiveField(field, ctx.longSort, ctx.cp.long)

            override fun getFloatField(field: JcField): Float =
                readPrimitiveField(field, ctx.floatSort, ctx.cp.float)

            override fun getDoubleField(field: JcField): Double =
                readPrimitiveField(field, ctx.doubleSort, ctx.cp.double)

            override fun getCharField(field: JcField): Char =
                readPrimitiveField(field, ctx.charSort, ctx.cp.char)

            override fun getArrayFieldLength(field: JcField): Int {
                val type = field.typed().fieldType as JcArrayType
                val arrayDescriptor = ctx.arrayDescriptorOf(type)
                val lengthRef = UArrayLengthLValue(instanceRef, arrayDescriptor, ctx.sizeSort)
                return resolveLValue(lengthRef, ctx.cp.int) as Int
            }
        }

        private inner class TestInterpreterDecoderApi : DecoderApi<Any?> {
            override fun invokeMethod(method: JcMethod, args: List<Any?>): Any? =
                if (method.isStatic || method.isConstructor) {
                    method.invoke(classLoader, null, args)
                } else {
                    method.invoke(classLoader, args.first(), args.drop(1))
                }

            override fun getField(field: JcField, instance: Any?): Any? =
                field.getFieldValue(classLoader, instance)

            override fun setField(field: JcField, instance: Any?, value: Any?) {
                field.setFieldValue(classLoader, instance, value)
            }

            override fun createBoolConst(value: Boolean): Any = value
            override fun createByteConst(value: Byte): Any = value
            override fun createShortConst(value: Short): Any = value
            override fun createIntConst(value: Int): Any = value
            override fun createLongConst(value: Long): Any = value
            override fun createFloatConst(value: Float): Any = value
            override fun createDoubleConst(value: Double): Any = value
            override fun createCharConst(value: Char): Any = value
            override fun createStringConst(value: String): Any = value

            override fun createClassConst(cls: JcClassOrInterface): Any =
                cls.toType().toJavaClass(classLoader)

            override fun createNullConst(): Any? = null

            override fun setArrayIndex(array: Any?, index: Any?, value: Any?) {
                Reflection.setArrayIndex(array!!, index as Int, value)
            }

            override fun getArrayIndex(array: Any?, index: Any?): Any? =
                Reflection.getArrayIndex(array!!, index as Int)

            override fun getArrayLength(array: Any?): Any =
                Reflection.getArrayLength(array)

            override fun createArray(elementType: JcType, size: Any?): Any =
                ctx.cp.arrayTypeOf(elementType).allocateInstance(classLoader, size as Int)

            override fun castClass(type: JcClassOrInterface, obj: Any?): Any? = obj
        }

        /**
         * If we resolve state after, [expr] is read from a state memory, so it requires concretization via [model].
         *
         * @return a concretized expression.
         */
        private fun <T : USort> evaluateInModel(expr: UExpr<T>): UExpr<T> {
            return model.eval(expr)
        }

        // TODO simple org.jacodb.api.ext.JcClasses.isEnum does not work with enums with abstract methods
        private fun JcRefType.getEnumAncestorOrNull(): JcClassOrInterface? =
            (sequenceOf(jcClass) + jcClass.allSuperHierarchySequence).firstOrNull { it.isEnum }

        private fun JcField.typed(): JcTypedField =
            enclosingClass.toType()
                .findFieldOrNull(name)
                ?: error("Field not found: $this")
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
    }
}

fun ClassLoader.loadClass(jcClass: JcClassOrInterface): Class<*> = if (this is JcClassLoader) {
    loadClass(jcClass)
} else {
    loadClass(jcClass.name)
}
