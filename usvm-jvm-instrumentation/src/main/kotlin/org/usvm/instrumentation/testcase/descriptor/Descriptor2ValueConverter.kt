package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.ext.*
import org.usvm.instrumentation.util.ReflectionUtils
import org.usvm.instrumentation.util.setFieldValue
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJavaField
import java.util.*

class Descriptor2ValueConverter(private val workerClassLoader: ClassLoader) {

    private val descriptorToObject = IdentityHashMap<UTestValueDescriptor, Any>()


    fun buildObjectFromDescriptor(descriptor: UTestValueDescriptor): Any? =
        descriptorToObject.getOrPut(descriptor) {
            build(descriptor)
        }

    private fun build(descriptor: UTestValueDescriptor): Any? =
        when (descriptor) {
            is UTestArrayDescriptor.Array -> array(descriptor)
            is UTestArrayDescriptor.BooleanArray -> descriptor.value
            is UTestArrayDescriptor.ByteArray -> descriptor.value
            is UTestArrayDescriptor.CharArray -> descriptor.value
            is UTestArrayDescriptor.DoubleArray -> descriptor.value
            is UTestArrayDescriptor.FloatArray -> descriptor.value
            is UTestArrayDescriptor.IntArray -> descriptor.value
            is UTestArrayDescriptor.LongArray -> descriptor.value
            is UTestArrayDescriptor.ShortArray -> descriptor.value
            is UTestConstantDescriptor.Boolean -> descriptor.value
            is UTestConstantDescriptor.Byte -> descriptor.value
            is UTestConstantDescriptor.Char -> descriptor.value
            is UTestConstantDescriptor.Double -> descriptor.value
            is UTestConstantDescriptor.Float -> descriptor.value
            is UTestConstantDescriptor.Int -> descriptor.value
            is UTestConstantDescriptor.Long -> descriptor.value
            is UTestConstantDescriptor.Null -> null
            is UTestConstantDescriptor.Short -> descriptor.value
            is UTestConstantDescriptor.String -> descriptor.value
            is UTestCyclicReferenceDescriptor -> {
                descriptorToObject
                    .toList()
                    .filter { it.first is UTestRefDescriptor }
                    .find { (it.first as UTestRefDescriptor).refId == descriptor.refId }?.second ?: error("Can't find descriptor in cache")
            }
            is UTestObjectDescriptor -> `object`(descriptor)
            is UTestEnumValueDescriptor -> `enum`(descriptor)
            is UTestClassDescriptor -> descriptor.classType.toJavaClass(workerClassLoader)
        }

    private fun array(descriptor: UTestArrayDescriptor.Array): Any {
        val jcElementType = descriptor.elementType
        val cp = descriptor.elementType.classpath
        val elementType = jcElementType.toJavaClass(workerClassLoader)
        val length = descriptor.value.size
        val arr = java.lang.reflect.Array.newInstance(elementType, length)
        for ((i, desc) in descriptor.value.withIndex()) {
            val descValue = buildObjectFromDescriptor(desc)
            when (jcElementType) {
                cp.boolean -> java.lang.reflect.Array.setBoolean(arr, i, (descValue as? Boolean) ?: false)
                cp.byte -> java.lang.reflect.Array.setByte(arr, i, (descValue as? Byte) ?: 0)
                cp.short -> java.lang.reflect.Array.setShort(arr, i, (descValue as? Short) ?: 0)
                cp.int -> java.lang.reflect.Array.setInt(arr, i, (descValue as? Int) ?: 0)
                cp.long -> java.lang.reflect.Array.setLong(arr, i, (descValue as? Long) ?: 0L)
                cp.float -> java.lang.reflect.Array.setFloat(arr, i, (descValue as? Float) ?: 0.0f)
                cp.double -> java.lang.reflect.Array.setDouble(arr, i, (descValue as? Double) ?: 0.0)
                cp.char -> java.lang.reflect.Array.setChar(arr, i, (descValue as? Char) ?: '\u0000')
                else -> java.lang.reflect.Array.set(arr, i, descValue)
            }
        }
        return arr
    }

    private fun `object`(descriptor: UTestObjectDescriptor): Any {
        val unsafe = ReflectionUtils.UNSAFE
        val jClass = descriptor.type.toJavaClass(workerClassLoader)
        val classInstance = unsafe.allocateInstance(jClass)
        descriptorToObject[descriptor] = classInstance
        for ((jcField, jcFieldDescr) in descriptor.fields) {
            val jField = jcField.toJavaField(workerClassLoader) ?: continue
            val jFieldValue = buildObjectFromDescriptor(jcFieldDescr)
            jField.setFieldValue(classInstance, jFieldValue)
        }
        return classInstance
    }

    private fun `enum`(descriptor: UTestEnumValueDescriptor): Any {
        val klass = descriptor.type.toJavaClass(workerClassLoader)
        val enumValue = klass.enumConstants.find { it.toString() == descriptor.enumValueName }
            ?: error("Can't build descriptor for enum")
        for ((jcField, jcFieldDescr) in descriptor.fields) {
            val jField = jcField.toJavaField(workerClassLoader) ?: continue
            val jFieldValue = buildObjectFromDescriptor(jcFieldDescr)
            jField.setFieldValue(enumValue, jFieldValue)
        }
        return enumValue
    }

}