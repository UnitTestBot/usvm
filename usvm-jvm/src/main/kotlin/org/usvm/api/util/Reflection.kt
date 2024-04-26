package org.usvm.api.util

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.ifArrayGetElementType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.jcdbSignature
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short
import sun.misc.Unsafe
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * An util class encapsulating reflection usage.
 */
object Reflection {
    private val unsafe: Unsafe

    init {
        val f: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        unsafe = f.get(null) as Unsafe
    }

    fun JcClassType.allocateInstance(classLoader: ClassLoader): Any =
        unsafe.allocateInstance(toJavaClass(classLoader))

    fun JcArrayType.allocateInstance(classLoader: ClassLoader, length: Int): Any =
        when (elementType) {
            classpath.boolean -> BooleanArray(length)
            classpath.short -> ShortArray(length)
            classpath.int -> IntArray(length)
            classpath.long -> LongArray(length)
            classpath.float -> FloatArray(length)
            classpath.double -> DoubleArray(length)
            classpath.byte -> ByteArray(length)
            classpath.char -> CharArray(length)
            is JcRefType -> {
                // TODO: works incorrectly for inner array
                val clazz = elementType.toJavaClass(classLoader)
                java.lang.reflect.Array.newInstance(clazz, length)
            }

            else -> error("Unexpected type: $this")
        }

    fun JcType.toJavaClass(classLoader: ClassLoader): Class<*> =
        when (this) {
            classpath.boolean -> Boolean::class.javaPrimitiveType!!
            classpath.short -> Short::class.javaPrimitiveType!!
            classpath.int -> Int::class.javaPrimitiveType!!
            classpath.long -> Long::class.javaPrimitiveType!!
            classpath.float -> Float::class.javaPrimitiveType!!
            classpath.double -> Double::class.javaPrimitiveType!!
            classpath.byte -> Byte::class.javaPrimitiveType!!
            classpath.char -> Char::class.javaPrimitiveType!!
            is JcRefType -> toJavaClass(classLoader)
            else -> error("Unexpected type: $this")
        }

    fun JcRefType.toJavaClass(classLoader: ClassLoader): Class<*> =
        ifArrayGetElementType?.let { elementType ->
            when (elementType) {
                classpath.boolean -> BooleanArray::class.java
                classpath.short -> ShortArray::class.java
                classpath.int -> IntArray::class.java
                classpath.long -> LongArray::class.java
                classpath.float -> FloatArray::class.java
                classpath.double -> DoubleArray::class.java
                classpath.byte -> ByteArray::class.java
                classpath.char -> CharArray::class.java
                is JcRefType -> {
                    val clazz = elementType.toJavaClass(classLoader)
                    java.lang.reflect.Array.newInstance(clazz, 0).javaClass
                }

                else -> error("Unexpected type: $elementType")
            }
        } ?: classLoader.loadClass(jcClass)

    private fun ClassLoader.loadClass(jcClass: JcClassOrInterface): Class<*> =
        if (this is JcClassLoader) {
            loadClass(jcClass)
        } else {
            loadClass(jcClass.name)
        }

    fun getArrayLength(instance: Any?): Int =
        java.lang.reflect.Array.getLength(instance)

    fun getArrayIndex(instance: Any, index: Int): Any? =
        when (instance::class.java) {
            BooleanArray::class.java -> java.lang.reflect.Array.getBoolean(instance, index)
            ByteArray::class.java -> java.lang.reflect.Array.getByte(instance, index)
            ShortArray::class.java -> java.lang.reflect.Array.getShort(instance, index)
            IntArray::class.java -> java.lang.reflect.Array.getInt(instance, index)
            LongArray::class.java -> java.lang.reflect.Array.getLong(instance, index)
            FloatArray::class.java -> java.lang.reflect.Array.getFloat(instance, index)
            DoubleArray::class.java -> java.lang.reflect.Array.getDouble(instance, index)
            CharArray::class.java -> java.lang.reflect.Array.getChar(instance, index)
            else -> java.lang.reflect.Array.get(instance, index)
        }

    fun setArrayIndex(instance: Any, index: Int, value: Any?) =
        when (instance::class.java) {
            BooleanArray::class.java -> java.lang.reflect.Array.setBoolean(instance, index, value as Boolean)
            ByteArray::class.java -> java.lang.reflect.Array.setByte(instance, index, value as Byte)
            ShortArray::class.java -> java.lang.reflect.Array.setShort(instance, index, value as Short)
            IntArray::class.java -> java.lang.reflect.Array.setInt(instance, index, value as Int)
            LongArray::class.java -> java.lang.reflect.Array.setLong(instance, index, value as Long)
            FloatArray::class.java -> java.lang.reflect.Array.setFloat(instance, index, value as Float)
            DoubleArray::class.java -> java.lang.reflect.Array.setDouble(instance, index, value as Double)
            CharArray::class.java -> java.lang.reflect.Array.setChar(instance, index, value as Char)
            else -> java.lang.reflect.Array.set(instance, index, value)
        }

    fun JcField.getFieldValue(classLoader: ClassLoader, instance: Any?): Any? {
        val javaField = toJavaField(classLoader)
        return withAccessibility(javaField) {
            javaField.get(instance)
        }
    }

    fun JcField.setFieldValue(classLoader: ClassLoader, instance: Any?, value: Any?) {
        val javaField = toJavaField(classLoader)
        return withAccessibility(javaField) {
            javaField.set(instance, value)
        }
    }

    fun JcMethod.invoke(classLoader: ClassLoader, instance: Any?, args: List<Any?>): Any? =
        if (isConstructor) {
            val javaCtor = toJavaConstructor(classLoader)
            withAccessibility(javaCtor) {
                javaCtor.newInstance(*args.toTypedArray())
            }
        } else {
            val javaMethod = toJavaMethod(classLoader)
            withAccessibility(javaMethod) {
                javaMethod.invoke(instance, *args.toTypedArray())
            }
        }

    private fun JcField.toJavaField(classLoader: ClassLoader): Field {
        val klass = Class.forName(enclosingClass.name, false, classLoader)
        return try {
            klass.getDeclaredField(name)
        } catch (ex: NoSuchFieldException) {
            error("Class ${enclosingClass.name} has no `$name` field")
        }
    }

    private fun JcMethod.toJavaMethod(classLoader: ClassLoader): Method {
        val klass = Class.forName(enclosingClass.name, false, classLoader)
        return (klass.methods + klass.declaredMethods)
            .find { it.jcdbSignature == this.jcdbSignature }
            ?: error("Can't find method in classpath")
    }

    private fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
        require(isConstructor) { "Can't convert not constructor to constructor" }
        val klass = Class.forName(enclosingClass.name, true, classLoader)
        return klass.constructors
            .find { it.jcdbSignature == this.jcdbSignature }
            ?: error("Can't find constructor")
    }

    private val Method.jcdbSignature: String
        get() = methodJcdbSignature(name, returnType.typeName, parameterTypes)

    private val Constructor<*>.jcdbSignature: String
        get() = methodJcdbSignature(name = "<init>", returnType = "void", parameterTypes)

    private fun methodJcdbSignature(name: String, returnType: String, params: Array<Class<*>>): String {
        val parameterTypes = if (params.isEmpty()) {
            ""
        } else {
            params.joinToString(";", postfix = ";") { it.typeName }
        }
        return "$name($parameterTypes)$returnType;"
    }

    private inline fun <T> withAccessibility(field: Field, block: () -> T): T {
        field.isAccessible = true
        return block()
    }

    private inline fun <T> withAccessibility(method: Method, block: () -> T): T {
        method.isAccessible = true
        return block()
    }

    private inline fun <T> withAccessibility(ctor: Constructor<*>, block: () -> T): T {
        ctor.isAccessible = true
        return block()
    }
}