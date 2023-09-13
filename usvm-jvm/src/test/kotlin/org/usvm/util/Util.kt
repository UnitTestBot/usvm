package org.usvm.util

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import java.io.File
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

val allClasspath: List<File>
    get() {
        return classpath.map { File(it) }
    }

private val classpath: List<String>
    get() {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparatorChar)
            .toList()
    }

fun JcClasspath.getJcMethodByName(func: KFunction<*>): JcMethod {
    val declaringClassName = requireNotNull(func.javaMethod?.declaringClass?.name)
    val jcClass = findClass(declaringClassName).toType()
    return jcClass.declaredMethods.first { it.name == func.name }.method
}

inline fun <reified T> Result<*>.isException(): Boolean = exceptionOrNull() is T
