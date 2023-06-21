package org.usvm.instrumentation.instrumentation

interface Tracer<T> {

    fun getTrace(): T

    fun reset()

}