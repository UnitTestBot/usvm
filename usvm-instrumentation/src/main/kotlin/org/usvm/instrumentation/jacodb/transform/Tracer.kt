package org.usvm.instrumentation.jacodb.transform

interface Tracer<T> {

    fun getTrace(): T

    fun reset()

}