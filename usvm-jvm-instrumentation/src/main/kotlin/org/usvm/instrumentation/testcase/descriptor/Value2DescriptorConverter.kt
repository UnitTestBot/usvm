package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.*
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.mock.MockHelper
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
    ): Result<UTestValueDescriptor> {
        testExecutor.executeUTestInst(uTestExpression)
            .onSuccess { uTestExprExecRes ->
                return buildDescriptorResultFromAny(uTestExprExecRes, uTestExpression.type)
            }
            .onFailure { exception -> return Result.failure(exception) }
        error("Unexpected situation in process of descriptor building")
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
        if (any == null || depth > InstrumentationModuleConstants.maxDepthOfDescriptorConstruction) {
            return `null`(type ?: jcClasspath.nullType)
        }
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
                is String -> const(any, depth + 1)
                is BooleanArray -> `boolean array`(any)
                is ByteArray -> `byte array`(any)
                is CharArray -> `char array`(any)
                is ShortArray -> `short array`(any)
                is IntArray -> `int array`(any)
                is LongArray -> `long array`(any)
                is FloatArray -> `float array`(any)
                is DoubleArray -> `double array`(any)
                is Array<*> -> `object array`(any, depth + 1)
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

    private fun const(value: String, depth: Int) =
        try {
            UTestConstantDescriptor.String(value, jcClasspath.stringType()).also { value.length }
        } catch (e: Throwable) {
            `object`(value, depth)
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

    private fun `boolean array`(array: BooleanArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.boolean,
            array.size,
            array.map { UTestConstantDescriptor.Boolean(it, jcClasspath.boolean) },
            System.identityHashCode(array)
        )

    private fun `byte array`(array: ByteArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.byte,
            array.size,
            array.map { UTestConstantDescriptor.Byte(it, jcClasspath.byte) },
            System.identityHashCode(array)
        )

    private fun `char array`(array: CharArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.char,
            array.size,
            array.map { UTestConstantDescriptor.Char(it, jcClasspath.char) },
            System.identityHashCode(array)
        )

    private fun `short array`(array: ShortArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.short,
            array.size,
            array.map { UTestConstantDescriptor.Short(it, jcClasspath.short) },
            System.identityHashCode(array)
        )

    private fun `int array`(array: IntArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.int,
            array.size,
            array.map { UTestConstantDescriptor.Int(it, jcClasspath.int) },
            System.identityHashCode(array)
        )

    private fun `long array`(array: LongArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.long,
            array.size,
            array.map { UTestConstantDescriptor.Long(it, jcClasspath.long) },
            System.identityHashCode(array)
        )

    private fun `float array`(array: FloatArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.float,
            array.size,
            array.map { UTestConstantDescriptor.Float(it, jcClasspath.float) },
            System.identityHashCode(array)
        )

    private fun `double array`(array: DoubleArray): UTestArrayDescriptor =
        UTestArrayDescriptor(
            jcClasspath.double,
            array.size,
            array.map { UTestConstantDescriptor.Double(it, jcClasspath.double) },
            System.identityHashCode(array)
        )

    private fun `object array`(array: Array<*>, depth: Int): UTestArrayDescriptor {
        val listOfRefs = mutableListOf<UTestValueDescriptor>()
        val descriptor = UTestArrayDescriptor(
            findTypeOrNull(array.javaClass.componentType) ?: jcClasspath.objectType,
            array.size,
            listOfRefs,
            System.identityHashCode(array)
        )
        createCyclicRef(descriptor, array) {
            for (i in array.indices) {
                listOfRefs.add(buildDescriptorFromAny(array[i], elementType, depth))
            }
        }
        return descriptor
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
                originUTestInst.type.toJcClass() ?: jcClasspath.findClass(
                    value::class.java.name.substringBeforeLast(
                        MockHelper.MOCKED_CLASS_POSTFIX
                    )
                )
            } else {
                jcClasspath.findClass(value::class.java.name)
            }
        val jcType = jcClass.toType()
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val uTestObjectDescriptor =
            UTestObjectDescriptor(jcType, fields, originUTestInst, System.identityHashCode(value))
        return createCyclicRef(uTestObjectDescriptor, value) {
            jcClass.allDeclaredFields
                //TODO! Decide for which fields descriptors should be build
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
        val stackTraceElementDescriptors =
            exception.stackTrace
                .take(InstrumentationModuleConstants.maxStackTraceElements)
                .map { buildDescriptorFromAny(it, jcType, depth) }
        return UTestExceptionDescriptor(
            jcType,
            exception.message ?: "",
            stackTraceElementDescriptors,
            false
        )
    }

    private fun `enum`(value: Enum<*>, depth: Int): UTestEnumValueDescriptor {
        val enumValueJcClass = jcClasspath.findClass(value::class.java.name)
        val jcClass = if (!enumValueJcClass.isEnum) enumValueJcClass.superClass!! else enumValueJcClass
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val enumValueName = value.name
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