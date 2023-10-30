package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.JcField
import org.jacodb.api.JcType
import org.jacodb.api.ext.*
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMock
import org.usvm.instrumentation.util.*
import java.util.*

open class Value2DescriptorConverter(
    workerClassLoader: WorkerClassLoader,
    val previousState: Value2DescriptorConverter?,
) {

    private val jcClasspath = workerClassLoader.jcClasspath
    private val classLoader = workerClassLoader as ClassLoader

    private val objectToDescriptor = IdentityHashMap<Any, UTestValueDescriptor>()
    var uTestExecutorCache: MutableList<Pair<Any?, UTestInst>> = mutableListOf()

    private fun findTypeOrNull(jClass: Class<*>): JcType? =
        if (jClass.isArray) {
            findTypeOrNull(jClass.componentType)?.let {
                jcClasspath.arrayTypeOf(it)
            }
        } else {
            jcClasspath.findTypeOrNull(jClass.name)
        }

    fun buildDescriptorFromUTestExpr(
        uTestExpression: UTestExpression,
        testExecutor: UTestExpressionExecutor,
    ): Result<UTestValueDescriptor>? {
        testExecutor.executeUTestInst(uTestExpression)
            .onSuccess {
                buildDescriptorResultFromAny(it, uTestExpression.type)
                    .onSuccess { return Result.success(it) }
                    .onFailure { return Result.failure(it) }
            }
            .onFailure { return Result.failure(it) }
        return null
    }

    fun buildDescriptorResultFromAny(any: Any?, type: JcType?, depth: Int = 0): Result<UTestValueDescriptor> =
        try {
            Result.success(buildDescriptorFromAny(any, type, depth))
        } catch (e: Throwable) {
            Result.failure(e)
        }

    private fun buildDescriptorFromAny(any: Any?, type: JcType?, depth: Int = 0): UTestValueDescriptor {
        val builtDescriptor = buildDescriptor(any, type, depth)
        val descriptorFromPreviousState = previousState?.objectToDescriptor?.get(any) ?: return builtDescriptor
        if (builtDescriptor.structurallyEqual(descriptorFromPreviousState)) {
            objectToDescriptor[any] = descriptorFromPreviousState
            return descriptorFromPreviousState
        }
        return builtDescriptor
    }

    private fun buildDescriptor(any: Any?, type: JcType?, depth: Int = 0): UTestValueDescriptor {
        if (any == null) return `null`(type ?: jcClasspath.nullType)
        return objectToDescriptor.getOrPut(any) {
            when (any) {
                is Boolean -> const(any)
                is Byte -> const(any)
                is Char -> const(any)
                is Short -> const(any)
                is Int -> const(any)
                is Long -> const(any)
                is Float -> const(any)
                is Double -> const(any)
                is String -> const(any)
                is BooleanArray -> array(any, depth + 1)
                is ByteArray -> array(any, depth + 1)
                is CharArray -> array(any, depth + 1)
                is ShortArray -> array(any, depth + 1)
                is IntArray -> array(any, depth + 1)
                is LongArray -> array(any, depth + 1)
                is FloatArray -> array(any, depth + 1)
                is DoubleArray -> array(any, depth + 1)
                is Array<*> -> array(any, depth + 1)
                is Enum<*> -> `enum`(any, depth + 1)
                is Class<*> -> `class`(any)
                is Throwable -> `exception`(any, depth + 1)
                else -> `object`(any, depth + 1)
            }
        }
    }

    private fun `null`(type: JcType) = UTestConstantDescriptor.Null(type)

    private fun const(value: Boolean) = UTestConstantDescriptor.Boolean(value, jcClasspath.boolean)

    private fun const(value: Char) = UTestConstantDescriptor.Char(value, jcClasspath.char)

    private fun const(value: String) =
        try {
            UTestConstantDescriptor.String(value, jcClasspath.stringType()).also { value.length }
        } catch (e: Throwable) {
            UTestConstantDescriptor.String(
                InstrumentationModuleConstants.nameForExistingButNullString,
                jcClasspath.stringType()
            )
        }

    private fun const(number: Number) = when (number) {
        is Byte -> UTestConstantDescriptor.Byte(number, jcClasspath.byte)
        is Short -> UTestConstantDescriptor.Short(number, jcClasspath.short)
        is Int -> UTestConstantDescriptor.Int(number, jcClasspath.int)
        is Long -> UTestConstantDescriptor.Long(number, jcClasspath.long)
        is Float -> UTestConstantDescriptor.Float(number, jcClasspath.float)
        is Double -> UTestConstantDescriptor.Double(number, jcClasspath.double)
        else -> error("Unsupported type")
    }

    private fun array(array: Any, depth: Int) =
        when (array) {
            is BooleanArray -> UTestArrayDescriptor.BooleanArray(jcClasspath.boolean, array.size, array)
            is ByteArray -> UTestArrayDescriptor.ByteArray(jcClasspath.byte, array.size, array)
            is ShortArray -> UTestArrayDescriptor.ShortArray(jcClasspath.short, array.size, array)
            is IntArray -> UTestArrayDescriptor.IntArray(jcClasspath.int, array.size, array)
            is LongArray -> UTestArrayDescriptor.LongArray(jcClasspath.long, array.size, array)
            is FloatArray -> UTestArrayDescriptor.FloatArray(jcClasspath.float, array.size, array)
            is DoubleArray -> UTestArrayDescriptor.DoubleArray(jcClasspath.double, array.size, array)
            is CharArray -> UTestArrayDescriptor.CharArray(jcClasspath.char, array.size, array)
            is Array<*> -> {
                val listOfRefs = mutableListOf<UTestValueDescriptor>()
                val descriptor = UTestArrayDescriptor.Array(
                    findTypeOrNull(array.javaClass.componentType)!!,
                    array.size,
                    listOfRefs,
                    System.identityHashCode(array)
                )
                createCyclicRef(descriptor, array) {
                    for (i in array.indices) {
                        listOfRefs.add(buildDescriptorFromAny(array[i], elementType, depth))
                    }
                }
            }

            else -> error("It is not array")
        }

    private fun <T> createCyclicRef(
        valueDescriptor: T,
        value: Any?,
        body: T.() -> Unit
    ): T where T : UTestRefDescriptor, T : UTestValueDescriptor =
        try {
            val objectCyclicRef = UTestCyclicReferenceDescriptor(valueDescriptor.refId, valueDescriptor.type)
            objectToDescriptor[value] = objectCyclicRef
            valueDescriptor.body()
            valueDescriptor
        } finally {
            objectToDescriptor.remove(value)
        }

    private fun `class`(value: Class<*>): UTestValueDescriptor {
        val jcType = value.toJcType(jcClasspath) ?: jcClasspath.objectType
        return UTestClassDescriptor(jcType, jcClasspath.findTypeOrNull<Class<*>>()!!)
    }

    private fun `object`(value: Any, depth: Int): UTestValueDescriptor {
        val originUTestInst = uTestExecutorCache.find { it.first === value }?.second
        val jcClass =
            if (originUTestInst is UTestMock) {
                originUTestInst.type.toJcClass() ?: jcClasspath.findClass(value::class.java.name.substringBefore("Mocked0"))
            } else {
                jcClasspath.findClass(value::class.java.name)
            }
        val jcType = jcClass.toType()
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val uTestObjectDescriptor = UTestObjectDescriptor(jcType, fields, originUTestInst, System.identityHashCode(value))
        return createCyclicRef(uTestObjectDescriptor, value) {
            jcClass.allDeclaredFields
                //TODO! Decide for which fields descriptors should be build
                .filterNot { it.isTransient }
                .forEach { jcField ->
                    val jField = jcField.toJavaField(classLoader) ?: return@forEach
                    val fieldValue = jField.getFieldValue(value)
                    val fieldDescriptor = buildDescriptorFromAny(fieldValue, jcField.type.toJcType(jcClasspath), depth)
                    fields[jcField] = fieldDescriptor
                }
        }
    }

    private fun `exception`(exception: Throwable, depth: Int): UTestExceptionDescriptor {
        val jcClass = jcClasspath.findClass(exception::class.java.name)
        val jcType = jcClass.toType()
        val stackTraceElementDescriptors = exception.stackTrace.map { buildDescriptorFromAny(it, jcType, depth) }
        return UTestExceptionDescriptor(
            jcType,
            exception.message ?: "",
            stackTraceElementDescriptors,
            false
        )
    }

    private fun `enum`(/*jcClass: JcClassOrInterface, */value: Any, depth: Int): UTestEnumValueDescriptor {
        val enumValueJcClass = jcClasspath.findClass(value::class.java.name)
        val jcClass = if (!enumValueJcClass.isEnum) enumValueJcClass.superClass!! else enumValueJcClass
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val enumValueName = value.toString()
        val jcType = jcClass.toType()
        val uTestEnumValueDescriptor =
            UTestEnumValueDescriptor(jcType, enumValueName, fields, System.identityHashCode(value))
        return createCyclicRef(uTestEnumValueDescriptor, value) {
            jcClass.allDeclaredFields
                .filter { jcClass.enumValues?.contains(it) == false && it.name != "\$VALUES" && !it.isFinal }
                .forEach { jcField ->
                    val jField = jcField.toJavaField(classLoader) ?: return@forEach
                    val fieldValue = jField.getFieldValue(value)
                    val fieldDescriptor = buildDescriptorFromAny(fieldValue, jcField.type.toJcType(jcClasspath), depth)
                    fields[jcField] = fieldDescriptor
                }
        }
    }

}