package org.usvm.util

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import java.io.File
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod


fun loadClasspathFromEnv(envKey: String): List<File> {
    val classpath = System.getenv(envKey) ?: error("Environment $envKey required")
    return parseClasspath(classpath)
}

fun parseClasspath(classpath: String): List<File> =
    classpath
        .split(File.pathSeparatorChar)
        .map { File(it) }

fun JcClasspath.getJcMethodByName(func: KFunction<*>): JcTypedMethod {
    val declaringClassName = requireNotNull(func.declaringClass?.name)
    val jcClass = findClass(declaringClassName).toType()
    return jcClass.declaredMethods.first { it.name == func.name }
}

inline fun <reified T> Result<*>.isException(): Boolean = exceptionOrNull() is T

internal val KFunction<*>.declaringClass: Class<*>?
    get() = (javaMethod ?: javaConstructor)?.declaringClass
