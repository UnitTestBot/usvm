package org.usvm.api.util

import org.jacodb.api.JcMethod
import org.jacodb.api.ext.jcdbSignature
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

    fun allocateInstance(cls: Class<*>): Any =
        unsafe.allocateInstance(cls)

    @Suppress("UNCHECKED_CAST")
    fun allocateArray(cls: Class<*>, length: Int): Array<Any?> =
        java.lang.reflect.Array.newInstance(cls, length) as Array<Any?>

    fun setField(instance: Any?, javaField: Field, fieldValue: Any?) {
        javaField.isAccessible = true
        try {
            javaField.set(instance, fieldValue)
        } finally {
            javaField.isAccessible = false
        }
    }

    fun getField(instance: Any?, javaField: Field): Any? {
        javaField.isAccessible = true
        try {
            return javaField.get(instance)
        } finally {
            javaField.isAccessible = false
        }
    }

    // TODO: remove copypaste from jvm-instrumentation
    fun JcMethod.toJavaMethod(classLoader: ClassLoader): Method {
        val klass = Class.forName(enclosingClass.name, false, classLoader)
        return (klass.methods + klass.declaredMethods).find { it.isSameSignatures(this) }
            ?: error("Can't find method in classpath")
    }

    fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
        require(isConstructor) { "Can't convert not constructor to constructor" }
        val klass = Class.forName(enclosingClass.name, true, classLoader)
        return klass.constructors.find { it.jcdbSignature == this.jcdbSignature } ?: error("Can't find constructor")
    }

    private val Method.jcdbSignature: String
        get() {
            val parameterTypesAsString = parameterTypes.toJcdbFormat()
            return name + "(" + parameterTypesAsString + ")" + returnType.typeName + ";"
        }

    private val Constructor<*>.jcdbSignature: String
        get() {
            val methodName = "<init>"
            //Because of jcdb
            val returnType = "void;"
            val parameterTypesAsString = parameterTypes.toJcdbFormat()
            return "$methodName($parameterTypesAsString)$returnType"
        }

    private fun Array<Class<*>>.toJcdbFormat(): String =
        if (isEmpty()) "" else joinToString(";", postfix = ";") { it.typeName }

    private fun Method.isSameSignatures(jcMethod: JcMethod) =
        jcdbSignature == jcMethod.jcdbSignature
}