@file:Suppress("DEPRECATION")

package org.usvm.instrumentation.util

import sun.misc.Unsafe
import java.lang.reflect.*


object ReflectionUtils {

    var UNSAFE: Unsafe

    init {
        try {
            val uns = Unsafe::class.java.getDeclaredField("theUnsafe")
            uns.isAccessible = true
            UNSAFE = uns[null] as Unsafe
        } catch (e: Throwable) {
            throw RuntimeException()
        }
    }

}

fun Field.getFieldValue(instance: Any?): Any? = with(ReflectionUtils.UNSAFE) {
    val (fixedInstance, fieldOffset) = getInstanceAndOffset(instance)
    return when (type) {
        Boolean::class.javaPrimitiveType -> getBoolean(fixedInstance, fieldOffset)
        Byte::class.javaPrimitiveType -> getByte(fixedInstance, fieldOffset)
        Char::class.javaPrimitiveType -> getChar(fixedInstance, fieldOffset)
        Short::class.javaPrimitiveType -> getShort(fixedInstance, fieldOffset)
        Int::class.javaPrimitiveType -> getInt(fixedInstance, fieldOffset)
        Long::class.javaPrimitiveType -> getLong(fixedInstance, fieldOffset)
        Float::class.javaPrimitiveType -> getFloat(fixedInstance, fieldOffset)
        Double::class.javaPrimitiveType -> getDouble(fixedInstance, fieldOffset)
        else -> getObject(fixedInstance, fieldOffset)
    }

}

fun Method.invokeWithAccessibility(instance: Any?, args: List<Any?>): Any? =
    try {
        withAccessibility {
            invoke(instance, *args.toTypedArray())
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }

fun Constructor<*>.newInstanceWithAccessibility(args: List<Any?>): Any =
    try {
        withAccessibility {
            newInstance(*args.toTypedArray())
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }

fun Field.setFieldValue(instance: Any?, fieldValue: Any?) = with(ReflectionUtils.UNSAFE) {
    val (fixedInstance, fieldOffset) = getInstanceAndOffset(instance)
    when (type) {
        Boolean::class.javaPrimitiveType -> putBoolean(fixedInstance, fieldOffset, fieldValue as? Boolean ?: false)
        Byte::class.javaPrimitiveType -> putByte(fixedInstance, fieldOffset, fieldValue as? Byte ?: 0)
        Char::class.javaPrimitiveType -> putChar(fixedInstance, fieldOffset, fieldValue as? Char ?: '\u0000')
        Short::class.javaPrimitiveType -> putShort(fixedInstance, fieldOffset, fieldValue as? Short ?: 0)
        Int::class.javaPrimitiveType -> putInt(fixedInstance, fieldOffset, fieldValue as? Int ?: 0)
        Long::class.javaPrimitiveType -> putLong(fixedInstance, fieldOffset, fieldValue as? Long ?: 0L)
        Float::class.javaPrimitiveType -> putFloat(fixedInstance, fieldOffset, fieldValue as? Float ?: 0.0f)
        Double::class.javaPrimitiveType -> putDouble(fixedInstance, fieldOffset, fieldValue as? Double ?: 0.0)
        else -> putObject(fixedInstance, fieldOffset, fieldValue)
    }
}

fun Field.getInstanceAndOffset(instance: Any?) = with(ReflectionUtils.UNSAFE) {
    if (isStatic) {
        staticFieldBase(this@getInstanceAndOffset) to staticFieldOffset(this@getInstanceAndOffset)
    } else {
        instance to objectFieldOffset(this@getInstanceAndOffset)
    }
}

val Class<*>.allFields
    get(): List<Field> {
        val result = mutableListOf<Field>()
        var current: Class<*>? = this
        do {
            result += current!!.declaredFields
            current = current!!.superclass
        } while (current != null)
        return result
    }

fun Class<*>.getFieldByName(name: String): Field? {
    var result: Field?
    var current: Class<*> = this
    do {
        result = `try` { current.getDeclaredField(name) }.getOrNull()
        current = current.superclass ?: break
    } while (result == null)
    return result
}

inline fun <reified R> Method.withAccessibility(block: () -> R): R {
    val prevAccessibility = isAccessible

    isAccessible = true

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
    }
}

inline fun <reified R> Constructor<*>.withAccessibility(block: () -> R): R {
    val prevAccessibility = isAccessible

    isAccessible = true

    try {
        return block()
    } finally {
        isAccessible = prevAccessibility
    }
}

val Field.isStatic: Boolean
    get() = modifiers.and(Modifier.STATIC) > 0

val Field.isFinal: Boolean
    get() = (this.modifiers and Modifier.FINAL) == Modifier.FINAL
