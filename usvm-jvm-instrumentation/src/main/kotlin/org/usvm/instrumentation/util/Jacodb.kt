package org.usvm.instrumentation.util

import org.jacodb.api.*
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.*
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.instrumentation.testcase.executor.TestExecutorException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

fun JcClasspath.stringType(): JcType {
    return findClassOrNull("java.lang.String")!!.toType()
}

fun JcClasspath.findFieldByFullNameOrNull(fieldFullName: String): JcField? {
    val className = fieldFullName.substringBeforeLast('.')
    val fieldName = fieldFullName.substringAfterLast('.')
    val jcClass = findClassOrNull(className) ?: return null
    return jcClass.declaredFields.find { it.name == fieldName }
}

operator fun JcClasspath.get(klass: Class<*>) = this.findClassOrNull(klass.name)

val JcClassOrInterface.typename
    get() = TypeNameImpl(this.name)

val JcMethod.typename
    get() = TypeNameImpl(this.name)

val JcTypedMethod.typename
    get() = TypeNameImpl(this.name)

fun JcType.toStringType(): String =
    when (this) {
        is JcClassType -> jcClass.name
        is JcTypeVariable -> jcClass.name
        is JcArrayType -> "${elementType.toStringType()}[]"
        else -> typeName
    }

fun JcType.getTypename() = TypeNameImpl(this.typeName)

val JcInst.enclosingClass
    get() = this.location.method.enclosingClass

val JcInst.enclosingMethod
    get() = this.location.method

fun JcType.toJavaClass(classLoader: ClassLoader): Class<*> =
    when (this) {
        is JcPrimitiveType -> toJavaClass()
        is JcArrayType -> findClassInLoader(toJvmType(), classLoader) ?: throw TestExecutorException("Can't find class in classpath")
        is JcClassType -> this.jcClass.toJavaClass(classLoader)
        else -> findClassInLoader(typeName, classLoader) ?: throw TestExecutorException("Can't find class in classpath")
    }

private fun JcPrimitiveType.toJavaClass(): Class<*> {
    val cp = this.classpath
    return when (this) {
        cp.boolean -> Boolean::class.java
        cp.byte -> Byte::class.java
        cp.short -> Short::class.java
        cp.int -> Int::class.java
        cp.long -> Long::class.java
        cp.float -> Float::class.java
        cp.double -> Double::class.java
        cp.char -> Char::class.java
        cp.void -> Void::class.java
        else -> error("Not primitive type")

    }
}

fun Class<*>.toJcType(jcClasspath: JcClasspath): JcType? {
    return jcClasspath.findTypeOrNull(this.typeName)
}

fun Class<*>.toJcClassOrInterface(jcClasspath: JcClasspath): JcClassOrInterface? {
    return jcClasspath.findClassOrNull(this.name)
}

fun JcArrayType.toJvmType(strBuilder: StringBuilder = StringBuilder()): String {
    strBuilder.append('[')
    when (elementType) {
        is JcArrayType -> (elementType as JcArrayType).toJvmType(strBuilder)
        elementType.classpath.boolean -> strBuilder.append("Z")
        elementType.classpath.byte -> strBuilder.append("B")
        elementType.classpath.short -> strBuilder.append("S")
        elementType.classpath.int -> strBuilder.append("I")
        elementType.classpath.long -> strBuilder.append("J")
        elementType.classpath.float -> strBuilder.append("F")
        elementType.classpath.double -> strBuilder.append("D")
        elementType.classpath.char -> strBuilder.append("C")
        else -> strBuilder.append("L${elementType.toStringType()};")
    }
    return strBuilder.toString()
}

fun JcType.toJcClass(): JcClassOrInterface? =
    when (this) {
        is JcRefType -> jcClass
        is JcPrimitiveType -> null
        else -> error("Unexpected type")
    }

fun JcClassOrInterface.toJavaClass(classLoader: ClassLoader): Class<*> =
    findClassInLoader(name, classLoader) ?: throw TestExecutorException("Can't find class in classpath")


fun findClassInLoader(name: String, classLoader: ClassLoader): Class<*>? =
    Class.forName(name, true, classLoader)

fun JcField.toJavaField(classLoader: ClassLoader): Field? =
    enclosingClass.toType().toJavaClass(classLoader).getFieldByName(name)

val JcClassOrInterface.allDeclaredFields
    get(): List<JcField> {
        val result = HashMap<String, JcField>()
        var current: JcClassOrInterface? = this
        do {
            current!!.declaredFields.forEach {
                result.putIfAbsent("${it.name}${it.type}", it)
            }
            current = current.superClass
        } while (current != null)
        return result.values.toList()
    }

fun TypeName.toJcType(jcClasspath: JcClasspath): JcType? = jcClasspath.findTypeOrNull(typeName)
fun TypeName.toJcClassOrInterface(jcClasspath: JcClasspath): JcClassOrInterface? = jcClasspath.findClassOrNull(typeName)

fun JcMethod.toJavaMethod(classLoader: ClassLoader): Method {
    val klass = Class.forName(enclosingClass.name, false, classLoader)
    return (klass.methods + klass.declaredMethods).find { it.isSameSignatures(this) }
        ?: throw TestExecutorException("Can't find method in classpath")
}

fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
    require(isConstructor) { "Can't convert not constructor to constructor" }
    val klass = Class.forName(enclosingClass.name, true, classLoader)
    return klass.constructors.find { it.jcdbSignature == this.jcdbSignature } ?: error("Can't find constructor")
}

val Method.jcdbSignature: String
    get() {
        val parameterTypesAsString = parameterTypes.toJcdbFormat()
        return name + "(" + parameterTypesAsString + ")" + returnType.typeName + ";"
    }

val Constructor<*>.jcdbSignature: String
    get() {
        val methodName = "<init>"
        //Because of jcdb
        val returnType = "void;"
        val parameterTypesAsString = parameterTypes.toJcdbFormat()
        return "$methodName($parameterTypesAsString)$returnType"
    }

private fun Array<Class<*>>.toJcdbFormat(): String =
    if (isEmpty()) "" else joinToString(";", postfix = ";") { it.typeName }

fun Method.isSameSignatures(jcMethod: JcMethod) =
    jcdbSignature == jcMethod.jcdbSignature