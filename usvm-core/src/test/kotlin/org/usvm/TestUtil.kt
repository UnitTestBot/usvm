package org.usvm

import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

typealias Field = java.lang.reflect.Field
typealias Type = kotlin.reflect.KClass<*>
typealias Method = kotlin.reflect.KFunction<*>

fun <T> shouldNotBeCalled(): T {
    error("Should not be called")
}

/**
 * Pseudo random function for tests.
 * Random with constant seed can return different values in different Kotlin versions. This
 * implementation should not have this problem.
 */
internal fun pseudoRandom(i: Int): Int {
    var res = ((i shr 16) xor i) * 0x45d9f3b
    res = ((res shr 16) xor res) * 0x45d9f3b
    res = (res shr 16) xor res
    return res
}
