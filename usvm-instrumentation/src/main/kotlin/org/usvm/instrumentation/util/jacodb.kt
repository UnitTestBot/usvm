package org.usvm.instrumentation.util

import getFieldByName
import jdk.internal.org.objectweb.asm.Opcodes
import org.jacodb.api.*
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.*
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.cfg.util.isArray
import org.jacodb.impl.cfg.util.isPrimitive
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.instrumentation.testcase.executor.TestExecutorException
import setFieldValue
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

//TODO!! Test with arrays
fun JcType.toJavaClass(classLoader: ClassLoader): Class<*> =
    when (this) {
        is JcClassType -> this.jcClass.toJavaClass(classLoader)
        else -> findClassInLoader(typeName, classLoader) ?: throw TestExecutorException("Can't find class in classpath")
    }

fun JcType.toJcClass(): JcClassOrInterface? = classpath.findClassOrNull(typeName)

fun JcClassOrInterface.toJavaClass(classLoader: ClassLoader): Class<*> =
    findClassInLoader(name, classLoader) ?: throw TestExecutorException("Can't find class in classpath")


fun findClassInLoader(name: String, classLoader: ClassLoader): Class<*>? =
    Class.forName(name, true, classLoader)

fun JcField.toJavaField(classLoader: ClassLoader): Field? =
    enclosingClass.toType().toJavaClass(classLoader).getFieldByName(name)

fun JcClassOrInterface.isAllStaticsAreEasyToRollback(): Boolean {
    val statics = declaredFields.filter { it.isStatic }
    for (s in statics) {
        val typeName = s.type
        val isPrimitive = typeName.isPrimitive || typeName.isPrimitiveWrapper() || this.classpath.stringType()
            .getTypename() == typeName
        val isArrayOfPrimitive =
            typeName.isArray && (typeName.elementType().isPrimitive || typeName.elementType().isPrimitiveWrapper())
        if (!isPrimitive && !isArrayOfPrimitive) return false
    }
    return true
}

val JcClassOrInterface.allFields
    get(): List<JcField> {
        val result = HashMap<String, JcField>()
        var current: JcClassOrInterface? = this
        do {
            current!!.fields.forEach {
                result.putIfAbsent(it.name, it)
            }
            current = current.superClass
        } while (current != null)
        return result.values.toList()
    }

val JcClassOrInterface.allDeclaredFields
    get(): List<JcField> {
        val result = HashMap<String, JcField>()
        var current: JcClassOrInterface? = this
        do {
            current!!.declaredFields.forEach {
                result.putIfAbsent(it.name, it)
            }
            current = current.superClass
        } while (current != null)
        return result.values.toList()
    }

private fun String.typeName(): TypeName = TypeNameImpl(this.jcdbName())
private fun TypeName.elementType() = elementTypeOrNull() ?: this

private val NULL = "null".typeName()
private fun TypeName.elementTypeOrNull() = when {
    this == NULL -> NULL
    typeName.endsWith("[]") -> typeName.removeSuffix("[]").typeName()
    else -> null
}

fun TypeName.toJcType(jcClasspath: JcClasspath): JcType? = jcClasspath.findTypeOrNull(typeName)
fun TypeName.toJcClassOrInterface(jcClasspath: JcClasspath): JcClassOrInterface? = jcClasspath.findClassOrNull(typeName)

fun TypeName.isPrimitiveWrapper() =
    when (this.typeName) {
        Boolean::class.javaObjectType.name -> true
        Byte::class.javaObjectType.name -> true
        Short::class.javaObjectType.name -> true
        Int::class.javaObjectType.name -> true
        Long::class.javaObjectType.name -> true
        Float::class.javaObjectType.name -> true
        Double::class.javaObjectType.name -> true
        Char::class.javaObjectType.name -> true
        else -> false
    }

fun TypeName.isPrimitiveArray() =
    isArray && PredefinedPrimitives.matches(typeName.substringBefore('['))

fun JcMethod.toJavaMethod(classLoader: ClassLoader): Method {
    val klass = Class.forName(enclosingClass.name, false, classLoader)
    try {
        klass.declaredMethods
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    return klass.declaredMethods.find { it.isSameSignatures(this) }
        ?: throw TestExecutorException("Can't find method in classpath")
}

fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
    require(isConstructor) { "Can't convert not constructor to constructor" }
    val klass = Class.forName(enclosingClass.name, true, classLoader)
    return klass.constructors.find { it.toJcdbSignature() == this.jcdbSignature } ?: error("Can't find constructor")
}

fun Method.toJcdbSignature(): String {
    val parameterTypesAsString = parameterTypes.toJcdbFormat()
    return name + "(" + parameterTypesAsString + ")" + returnType.name + ";"
}

fun Constructor<*>.toJcdbSignature(): String {
    val methodName = "<init>"
    //Because of jcdb
    val returnType = "void;"
    val parameterTypesAsString = parameterTypes.toJcdbFormat()
    return "$methodName($parameterTypesAsString)$returnType"
}

private fun Array<Class<*>>.toJcdbFormat(): String =
    if (isEmpty()) ""
    else joinToString(";", postfix = ";") { it.typeName }

fun JcMethod.isSameSignatures(method: Method) =
    jcdbSignature == method.toJcdbSignature()

fun JcMethod.isSameSignatures(constructor: Constructor<*>) =
    jcdbSignature == constructor.toJcdbSignature()

fun Method.isSameSignatures(jcMethod: JcMethod) =
    toJcdbSignature() == jcMethod.jcdbSignature

//data class Flags(val value: Int) : Comparable<Flags> {
//    companion object {
//        val readAll = Flags(0)
//        val readSkipDebug = Flags(ClassReader.SKIP_DEBUG)
//        val readSkipFrames = Flags(ClassReader.SKIP_FRAMES)
//        val readCodeOnly = readSkipDebug + readSkipFrames
//
//        val writeComputeNone = Flags(0)
//        val writeComputeFrames = Flags(ClassWriter.COMPUTE_FRAMES)
//        val writeComputeMaxs = Flags(ClassWriter.COMPUTE_MAXS)
//        val writeComputeAll = writeComputeFrames
//    }
//
//    fun merge(other: Flags) = Flags(this.value or other.value)
//    operator fun plus(other: Flags) = this.merge(other)
//
//    override fun compareTo(other: Flags) = value.compareTo(other.value)
//}