package org.usvm.api.util

import sun.misc.Unsafe
import java.lang.reflect.Field
import java.lang.reflect.Modifier

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
        val (fieldBase, fieldOffset) = if (javaField.isStatic) {
            unsafe.staticFieldBase(javaField) to unsafe.staticFieldOffset(javaField)
        } else {
            instance to unsafe.objectFieldOffset(javaField)
        }

        unsafe.putObject(fieldBase, fieldOffset, fieldValue)
    }
}

val Field.isStatic: Boolean get() = Modifier.isStatic(modifiers)
