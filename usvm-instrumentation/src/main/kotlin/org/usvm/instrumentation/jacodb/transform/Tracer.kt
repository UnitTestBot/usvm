package org.usvm.instrumentation.jacodb.transform

interface Tracer<T> {

    fun getTrace(): List<T>

    fun reset()

}