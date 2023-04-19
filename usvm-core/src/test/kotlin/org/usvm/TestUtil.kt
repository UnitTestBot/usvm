package org.usvm

typealias Field = java.lang.reflect.Field
typealias Type = kotlin.reflect.KClass<*>
typealias Method = kotlin.reflect.KFunction<*>

fun <T> shouldNotBeCalled(): T {
    error("Should not be called")
}