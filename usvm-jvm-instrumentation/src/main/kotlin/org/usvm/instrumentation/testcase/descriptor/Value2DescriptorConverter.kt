package org.usvm.instrumentation.testcase.descriptor

import getFieldValue
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcField
import org.jacodb.api.JcType
import org.jacodb.api.ext.*
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.util.stringType
import org.usvm.instrumentation.util.toJavaField
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.util.allDeclaredFields
import java.util.*

open class Value2DescriptorConverter(
    workerClassLoader: WorkerClassLoader,
    val previousState: Value2DescriptorConverter?
) {

    private val jcClasspath = workerClassLoader.jcClasspath
    private val classLoader = workerClassLoader as ClassLoader

    private val objectToDescriptor = IdentityHashMap<Any, UTestValueDescriptor>()

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
        testExecutor.executeUTestExpression(uTestExpression)
            .onSuccess { return buildDescriptorResultFromAny(it) }
            .onFailure { return Result.failure(it) }
        return null
    }

    fun buildDescriptorResultFromAny(any: Any?, depth: Int = 0): Result<UTestValueDescriptor> =
        try {
            Result.success(buildDescriptorFromAny(any, depth))
        } catch (e: Throwable) {
            Result.failure(e)
        }

    private fun buildDescriptorFromAny(any: Any?, depth: Int = 0): UTestValueDescriptor {
        val builtDescriptor = buildDescriptor(any, depth)
        val descriptorFromPreviousState = previousState?.objectToDescriptor?.get(any) ?: return builtDescriptor
        if (builtDescriptor.structurallyEqual(descriptorFromPreviousState)) {
            objectToDescriptor[any] = descriptorFromPreviousState
            return descriptorFromPreviousState
        }
        return builtDescriptor
    }

    private fun buildDescriptor(any: Any?, depth: Int = 0): UTestValueDescriptor {
        if (any == null) return `null`(jcClasspath.nullType)
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
                else -> `object`(any, depth + 1)
            }
        }
    }

    private fun `null`(type: JcType) = UTestConstantDescriptor.Null(type)

    private fun const(value: Boolean) = UTestConstantDescriptor.Boolean(value, jcClasspath.boolean)

    private fun const(value: Char) = UTestConstantDescriptor.Char(value, jcClasspath.char)

    private fun const(value: String) = UTestConstantDescriptor.String(value, jcClasspath.stringType())

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
                        listOfRefs.add(buildDescriptorFromAny(array[i], depth))
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

    private fun `object`(value: Any, depth: Int): UTestValueDescriptor {
        val jcClass = jcClasspath.findClass(value::class.java.name)
        if (jcClass.isEnum) return `enum`(jcClass, value, depth)
        val jcType = jcClass.toType()
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val uTestObjectDescriptor = UTestObjectDescriptor(jcType, fields, System.identityHashCode(value))
        return createCyclicRef(uTestObjectDescriptor, value) {
            jcClass.allDeclaredFields
                //TODO! Decide for which fields descriptors should be build
                //.filterNot { it.isFinal }
                .forEach { jcField ->
                    val jField = jcField.toJavaField(classLoader) ?: return@forEach
                    val fieldValue = jField.getFieldValue(value)
                    val fieldDescriptor = buildDescriptorFromAny(fieldValue, depth)
                    fields[jcField] = fieldDescriptor
                }
        }
    }

    private fun `enum`(jcClass: JcClassOrInterface, value: Any, depth: Int): UTestEnumValueDescriptor {
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val enumValueName = value.toString()
        val jcType = jcClass.toType()
        val uTestEnumValueDescriptor = UTestEnumValueDescriptor(jcType, enumValueName, fields, System.identityHashCode(value))
        return createCyclicRef(uTestEnumValueDescriptor, value) {
            jcClass.allDeclaredFields
                .filter { jcClass.enumValues?.contains(it) == false && it.name != "\$VALUES" && !it.isFinal }
                .forEach { jcField ->
                    val jField = jcField.toJavaField(classLoader) ?: return@forEach
                    val fieldValue = jField.getFieldValue(value)
                    val fieldDescriptor = buildDescriptorFromAny(fieldValue, depth)
                    fields[jcField] = fieldDescriptor
                }
        }
    }

}