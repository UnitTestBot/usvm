package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.jvm.ext.*
import org.usvm.instrumentation.util.*
import java.util.*

class Descriptor2ValueConverter(private val workerClassLoader: ClassLoader) {

    private val descriptorToObject = IdentityHashMap<UTestValueDescriptor, Any?>()

    fun clear() {
        descriptorToObject.clear()
    }

    fun buildObjectFromDescriptor(descriptor: UTestValueDescriptor): Any? =
        descriptorToObject.getOrPut(descriptor) {
            build(descriptor)
        }

    private fun build(descriptor: UTestValueDescriptor): Any? =
        when (descriptor) {
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
            is UTestArrayDescriptor -> array(descriptor)
            is UTestCyclicReferenceDescriptor -> {
                descriptorToObject
                    .toList()
                    .filter { it.first is UTestRefDescriptor }
                    .find { (it.first as UTestRefDescriptor).refId == descriptor.refId }?.second
            }
            is UTestObjectDescriptor -> `object`(descriptor)
            is UTestEnumValueDescriptor -> `enum`(descriptor)
            is UTestClassDescriptor -> descriptor.classType.toJavaClass(workerClassLoader)
            is UTestExceptionDescriptor -> `exception`(descriptor)
        }

    private fun array(descriptor: UTestArrayDescriptor): Any {
        val jcElementType = descriptor.elementType
        val cp = descriptor.elementType.classpath
        val elementType = jcElementType.toJavaClass(workerClassLoader)
        val length = descriptor.value.size
        val arr = java.lang.reflect.Array.newInstance(elementType, length)
        descriptorToObject[descriptor] = arr
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

    private fun `exception`(descriptor: UTestExceptionDescriptor): Any {
        val unsafe = ReflectionUtils.UNSAFE
        val jClass = descriptor.type.toJavaClass(workerClassLoader)
        val exceptionInstance = unsafe.allocateInstance(jClass) as Throwable
        val stackTrace = descriptor.stackTrace
            .map { (buildObjectFromDescriptor(it) as? StackTraceElement) ?: error("Exception instantiation error") }
            .toTypedArray()
        val exceptionFields = exceptionInstance::class.java.allFields
        val msgField = exceptionFields.find { it.name == "detailMessage" } ?: error("Exception instantiation error")
        val stackTraceField = exceptionFields.find { it.name == "stackTrace" } ?: error("Exception instantiation error")
        msgField.setFieldValue(exceptionInstance, descriptor.message)
        stackTraceField.setFieldValue(exceptionInstance, stackTrace)
        return exceptionInstance
    }

    private fun `enum`(descriptor: UTestEnumValueDescriptor): Any {
        val klass = descriptor.type.toJavaClass(workerClassLoader)
        val enumValue = klass.enumConstants.find { (it as Enum<*>).name == descriptor.enumValueName }
            ?: error("Can't build descriptor for enum")
        for ((jcField, jcFieldDescr) in descriptor.fields) {
            val jField = jcField.toJavaField(workerClassLoader) ?: continue
            val jFieldValue = buildObjectFromDescriptor(jcFieldDescr)
            jField.setFieldValue(enumValue, jFieldValue)
        }
        return enumValue
    }

}