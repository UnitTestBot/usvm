package org.usvm.util

import sun.misc.Unsafe
import java.lang.reflect.Field

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
}