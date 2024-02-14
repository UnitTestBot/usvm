package org.usvm.fuzzer.util

import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.*
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.instrumentation.util.stringType

fun JcClasspath.objectTypeWrapper() =
    JcTypeWrapper(objectType, Object::class.java)

fun JcClasspath.booleanTypeWrapper() =
    JcTypeWrapper(boolean, Boolean::class.java)

fun JcClasspath.byteTypeWrapper() =
    JcTypeWrapper(byte, Byte::class.java)

fun JcClasspath.shortTypeWrapper() =
    JcTypeWrapper(short, Short::class.java)

fun JcClasspath.intTypeWrapper() =
    JcTypeWrapper(int, Int::class.java)

fun JcClasspath.longTypeWrapper() =
    JcTypeWrapper(long, Long::class.java)

fun JcClasspath.floatTypeWrapper() =
    JcTypeWrapper(float, Float::class.java)

fun JcClasspath.doubleTypeWrapper() =
    JcTypeWrapper(double, Double::class.java)

fun JcClasspath.charTypeWrapper() =
    JcTypeWrapper(char, Char::class.java)

fun JcClasspath.stringTypeWrapper() =
    JcTypeWrapper(stringType(), String::class.java)
