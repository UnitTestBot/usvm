package org.usvm.instrumentation.testcase.descriptor

import getFieldValue
import org.jacodb.api.JcField
import org.jacodb.api.JcType
import org.jacodb.api.ext.*
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.jacodb.util.stringType
import org.usvm.instrumentation.jacodb.util.toJavaField
import org.usvm.instrumentation.testcase.UTestExpressionExecutor
import org.usvm.instrumentation.testcase.statement.UTestExpression
import org.usvm.instrumentation.util.SystemTypeNames
import java.util.*

class DescriptorBuilder(private val classLoader: WorkerClassLoader, private val previousState: DescriptorBuilder?) {

    private val jcClasspath = classLoader.jcClasspath
    private val objectToDescriptor = IdentityHashMap<Any, UTestValueDescriptor>()

    fun buildDescriptorFromUTestExpr(
        uTestExpressionList: UTestExpression,
        testExecutor: UTestExpressionExecutor
    ): Result<UTestValueDescriptor>? =
        buildDescriptorFromUTestExprs(listOf(uTestExpressionList), testExecutor)

    fun buildDescriptorFromUTestExprs(
        uTestExpressionList: List<UTestExpression>,
        testExecutor: UTestExpressionExecutor
    ): Result<UTestValueDescriptor>? {
        testExecutor.executeUTestExpressions(uTestExpressionList)
            ?.onSuccess { return buildDescriptorResultFromAny(it) }
            ?.onFailure { return Result.failure(it) }
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
        val jcType = jcClasspath.findTypeOrNull(any::class.java.name)!!
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
                else -> `object`(jcType, any, depth + 1)
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
                    jcClasspath.findTypeOrNull(array.javaClass.componentType.name)!!,
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

    private fun `object`(type: JcType, value: Any, depth: Int): UTestObjectDescriptor {
        val jcClass = jcClasspath.findClass(type.typeName)
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val uTestObjectDescriptor = UTestObjectDescriptor(type, fields, System.identityHashCode(value))
        return createCyclicRef(uTestObjectDescriptor, value) {
            jcClass.fields.forEach { jcField ->
                val jField = jcField.toJavaField(classLoader)
                val fieldValue = jField.getFieldValue(value)
                val fieldDescriptor = buildDescriptorFromAny(fieldValue, depth)
                fields[jcField] = fieldDescriptor
            }
        }
    }

    private fun booleanWrapper(any: Boolean, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.booleanClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun byteWrapper(any: Byte, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.byteClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun shortWrapper(any: Short, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.shortClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun intWrapper(any: Int, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.integerClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun longWrapper(any: Long, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.longClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun floatWrapper(any: Float, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.floatClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun doubleWrapper(any: Double, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.doubleClass)!!
        return `object`(wrapperClass, any, depth)
    }

    private fun charWrapper(any: Char, depth: Int): UTestValueDescriptor {
        val wrapperClass = jcClasspath.findTypeOrNull(SystemTypeNames.charClass)!!
        return `object`(wrapperClass, any, depth)
    }

}