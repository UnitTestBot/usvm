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
}