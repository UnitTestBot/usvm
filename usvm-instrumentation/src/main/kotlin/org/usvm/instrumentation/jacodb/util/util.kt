package org.usvm.instrumentation.jacodb.util

import getFieldByName
import org.jacodb.api.*
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.jcdbSignature
import org.jacodb.api.ext.toType
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.usvm.instrumentation.testcase.TestExecutorException
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Path

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

fun JcClassOrInterface.write(
    path: Path
): File? =
    try {
        path.toFile().apply {
            mkdirs()
            File("$path/${this@write.simpleName}.class").writeBytes(this@write.bytecode())
        }
    } catch (e: Throwable) {
        null
    }

val JcClassOrInterface.typename
    get() = TypeNameImpl(this.name)

val JcMethod.typename
    get() = TypeNameImpl(this.name)

val JcTypedMethod.typename
    get() = TypeNameImpl(this.name)

fun JcType.getTypename() = TypeNameImpl(this.typeName)

val JcInst.enclosingClass
    get() = this.location.method.enclosingClass

val JcInst.enclosingMethod
    get() = this.location.method

fun JcType.toJavaCLass(classLoader: ClassLoader): Class<*> =
    findClassInLoader(typeName, classLoader)
        ?: throw TestExecutorException("Can't find class in classpath")

fun findClassInLoader(name: String, classLoader: ClassLoader): Class<*>? =
    Class.forName(name, true, classLoader)

//TODO REWRITE!!!!
fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
    val klass = Class.forName(enclosingClass.name, true, classLoader)
    return klass.constructors.first()
}

fun JcField.toJavaField(classLoader: ClassLoader): Field =
    enclosingClass.toType().toJavaCLass(classLoader).getFieldByName(name)

fun TypeName.toJcType(jcClasspath: JcClasspath): JcType? = jcClasspath.findTypeOrNull(typeName)

fun Method.toJcdbSignature(): String =
    name + "(" + parameterTypes.joinToString(";", postfix = ";") { it.typeName } + ")" + returnType.name + ";"

fun JcMethod.isSameSignatures(method: Method) =
    jcdbSignature == method.toJcdbSignature()

fun Method.isSameSignatures(jcMethod: JcMethod) =
    toJcdbSignature() == jcMethod.jcdbSignature

data class Flags(val value: Int) : Comparable<Flags> {
    companion object {
        val readAll = Flags(0)
        val readSkipDebug = Flags(ClassReader.SKIP_DEBUG)
        val readSkipFrames = Flags(ClassReader.SKIP_FRAMES)
        val readCodeOnly = readSkipDebug + readSkipFrames

        val writeComputeNone = Flags(0)
        val writeComputeFrames = Flags(ClassWriter.COMPUTE_FRAMES)
        val writeComputeMaxs = Flags(ClassWriter.COMPUTE_MAXS)
        val writeComputeAll = writeComputeFrames
    }

    fun merge(other: Flags) = Flags(this.value or other.value)
    operator fun plus(other: Flags) = this.merge(other)

    override fun compareTo(other: Flags) = value.compareTo(other.value)
}