package org.usvm.fuzzer.util

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.findType
import org.usvm.fuzzer.types.JcTypeWrapper
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

fun Type.simpleTypeName(): String =
    when (this) {
        is ParameterizedType -> this.rawType.typeName.substringBefore('<')
        is GenericArrayType -> "${this.genericComponentType.simpleTypeName()}[]"
        else -> this.typeName.substringBefore('<')
    }

fun Type.createJcTypeWrapper(cp: JcClasspath) =
    try {
        JcTypeWrapper(cp.findType(simpleTypeName()), this)
    } catch (e: Throwable) {
        println()
        throw e
    }