package org.usvm

typealias Field = java.lang.reflect.Field
typealias Type = kotlin.reflect.KClass<*>
typealias Method = kotlin.reflect.KFunction<*>

fun <T> shouldNotBeCalled(): T {
    error("Should not be called")
}

internal fun hash(i: Int): Int {
    var res = ((i shr 16) xor i) * 0x45d9f3b
    res = ((res shr 16) xor res) * 0x45d9f3b
    res = (res shr 16) xor res
    return res
}