package org.usvm.instrumentation.testcase.descriptor

import ReflectionUtils
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJavaField
import setFieldValue
import java.util.*

class Descriptor2ValueConverter(private val workerClassLoader: WorkerClassLoader) {

    private val descriptorToObject = IdentityHashMap<UTestValueDescriptor, Any>()


    fun buildObjectFromDescriptor(descriptor: UTestValueDescriptor): Any? =
            descriptorToObject.getOrPut(descriptor) {
                buildObject(descriptor)
            }

    private fun buildObject(descriptor: UTestValueDescriptor): Any? =
        when (descriptor) {
            is UTestArrayDescriptor.Array -> arrayOf(descriptor.value.map { buildObjectFromDescriptor(it) })
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
            is UTestCyclicReferenceDescriptor -> descriptorToObject[descriptor] ?: error("Can't find descriptor in cache")
            is UTestObjectDescriptor -> `object`(descriptor)
            is UTestEnumValueDescriptor -> `enum`(descriptor)
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
        val enumValue = klass.enumConstants.find { it.toString() == descriptor.enumValueName } ?: error("Can't build descriptor for enum")
        for ((jcField, jcFieldDescr) in descriptor.fields) {
            val jField = jcField.toJavaField(workerClassLoader) ?: continue
            val jFieldValue = buildObjectFromDescriptor(jcFieldDescr)
            jField.setFieldValue(enumValue, jFieldValue)
        }
        return enumValue
    }

}